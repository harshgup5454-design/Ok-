package com.example.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.DeviceMonitor
import com.example.data.GameBoostDatabase
import com.example.data.GameSpaceManager
import com.example.data.GamingProtectionEngine
import com.example.data.NetworkMonitor
import com.example.data.OverlayPreferences
import com.example.data.ProfileType
import com.example.data.ProtectionSeverity
import com.example.smartgaming.SmartGamingManager
import com.example.ui.components.GamingSidebarContent
import com.example.ui.theme.GameBoostTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class OverlayStartResult(
    val started: Boolean,
    val reason: String
)

data class SafeWindowBounds(
    val widthPx: Int,
    val heightPx: Int,
    val safeLeftPx: Int,
    val safeTopPx: Int,
    val safeRightPx: Int,
    val safeBottomPx: Int
)

class GamingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private var windowManager: WindowManager? = null
    private var rootContainer: OverlayTouchContainer? = null
    private var composeView: ComposeView? = null
    private var isForegroundStarted = false
    private var collapseWindowJob: Job? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val detail = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Unexpected overlay error"}"
        logEvent("Handled overlay coroutine exception: $detail")
        _lastOverlayError.value = "Floating sidebar closed safely after an error ($detail)."
        _isServiceRunning.value = false
        _isSessionOwnedOverlay.value = false
        val cleanBlock = Runnable {
            detachOverlayViewsSafely()
            stopForegroundSafely()
            stopSelf()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanBlock.run()
        } else {
            Handler(Looper.getMainLooper()).post(cleanBlock)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob() + coroutineExceptionHandler)

    private var isSidebarOpen by mutableStateOf(false)
    private var isCompactMode by mutableStateOf(false)
    private var savedHandleYOffsetPx = 0
    private var sessionSeconds by mutableStateOf(0L)
    private var isTimerRunning by mutableStateOf(true)
    private var isAimTrainerQueued by mutableStateOf(false)
    private var aimTrainerStatusHint by mutableStateOf<String?>(null)
    private var queuedGamePackage: String? = null

    private var memoryUsage by mutableStateOf(0)
    private var availMemory by mutableStateOf(0f)
    private var batteryTemp by mutableStateOf(0f)
    private var batteryLevel by mutableStateOf(100)
    private var isCharging by mutableStateOf(false)
    private var thermalStatus by mutableStateOf("Normal")
    private var thermalHeadroom by mutableStateOf(-1f)
    private var pingMs by mutableStateOf(-1)
    private var jitterMs by mutableStateOf(0)
    private var refreshRateHz by mutableStateOf(60f)
    private var protectionAlertHeadline by mutableStateOf<String?>(null)

    companion object {
        const val TAG = "GameBoostOverlay"
        const val NOTIFICATION_CHANNEL_ID = "gameboost_overlay_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_OVERLAY = "com.example.action.START_OVERLAY"
        const val EXTRA_AUTO_STARTED_BY_SESSION = "extra_auto_started_by_session"

        const val EXTRA_TARGET_ROUTE = "extra_target_route"
        const val EXTRA_AIM_TAB = "extra_aim_tab"
        const val ROUTE_NETWORK = "network"
        const val ROUTE_AIM = "aim"
        const val AIM_TAB_CALCULATOR = 1
        const val AIM_TAB_TRAINER = 3

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isSessionOwnedOverlay = MutableStateFlow(false)
        val isSessionOwnedOverlay: StateFlow<Boolean> = _isSessionOwnedOverlay.asStateFlow()

        private val _lastOverlayError = MutableStateFlow<String?>(null)
        val lastOverlayError: StateFlow<String?> = _lastOverlayError.asStateFlow()

        private val _isAppInForeground = MutableStateFlow(false)
        val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

        private val _pendingAimTrainerOnReturn = MutableStateFlow(false)
        val pendingAimTrainerOnReturn: StateFlow<Boolean> = _pendingAimTrainerOnReturn.asStateFlow()

        private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
        val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun logEvent(message: String) {
            Log.d(TAG, message)
            val stamp = timeFormat.format(Date())
            val entry = "[$stamp] $message"
            _diagnosticLogs.value = (listOf(entry) + _diagnosticLogs.value).take(35)
        }

        fun setAppForeground(inForeground: Boolean) {
            _isAppInForeground.value = inForeground
        }

        fun clearLastOverlayError() {
            _lastOverlayError.value = null
        }

        fun consumePendingAimTrainerOnReturn(): Boolean {
            val wasPending = _pendingAimTrainerOnReturn.value
            _pendingAimTrainerOnReturn.value = false
            return wasPending
        }

        fun startOverlay(
            context: Context,
            autoStartedBySession: Boolean = false
        ): OverlayStartResult {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val msg = "Display Over Other Apps permission required to show floating sidebar."
                logEvent("Overlay start blocked: SYSTEM_ALERT_WINDOW permission denied")
                _lastOverlayError.value = msg
                return OverlayStartResult(false, msg)
            }

            // Prevent duplicate service start calls if overlay is already active
            if (_isServiceRunning.value) {
                logEvent("Overlay start skipped duplicate request (already running, sessionAuto=$autoStartedBySession)")
                _lastOverlayError.value = null
                return OverlayStartResult(true, "Floating sidebar is already active")
            }

            _lastOverlayError.value = null
            if (autoStartedBySession) {
                _isSessionOwnedOverlay.value = true
            }

            val intent = Intent(context, GamingOverlayService::class.java).apply {
                action = ACTION_START_OVERLAY
                putExtra(EXTRA_AUTO_STARTED_BY_SESSION, autoStartedBySession)
            }

            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                logEvent("Overlay foreground service start requested (sessionAuto=$autoStartedBySession)")
                OverlayStartResult(true, "Overlay start requested")
            } catch (t: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t is ForegroundServiceStartNotAllowedException) {
                    val msg = "Android restricted starting the overlay from background. Open ZeroX Boost to launch the floating handle."
                    logEvent("ForegroundServiceStartNotAllowedException: ${t.message}")
                    _isSessionOwnedOverlay.value = false
                    _lastOverlayError.value = msg
                    return OverlayStartResult(false, msg)
                }
                if (_isAppInForeground.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    try {
                        context.startService(intent)
                        logEvent("Overlay started via standard startService fallback")
                        OverlayStartResult(true, "Overlay started as foreground-bound service")
                    } catch (t2: Throwable) {
                        val msg = "Failed to start overlay service: ${t2.javaClass.simpleName}: ${t2.message}"
                        logEvent(msg)
                        _isSessionOwnedOverlay.value = false
                        _lastOverlayError.value = msg
                        OverlayStartResult(false, msg)
                    }
                } else {
                    val msg = "Failed to start overlay service: ${t.javaClass.simpleName}: ${t.message}"
                    logEvent(msg)
                    _isSessionOwnedOverlay.value = false
                    _lastOverlayError.value = msg
                    OverlayStartResult(false, msg)
                }
            }
        }

        fun stopAutoStartedOverlay(context: Context) {
            if (_isSessionOwnedOverlay.value) {
                logEvent("Stopping session-started overlay service cleanly")
                stopOverlay(context)
            }
        }

        fun stopOverlay(context: Context) {
            _isSessionOwnedOverlay.value = false
            try {
                context.stopService(Intent(context, GamingOverlayService::class.java))
            } catch (t: Throwable) {
                logEvent("Failed to stop overlay service: ${t.message}")
            }
        }

        internal fun clampHandleY(desiredY: Int, bounds: SafeWindowBounds, handleHeightPx: Int): Int {
            val usableHalf = ((bounds.heightPx - bounds.safeTopPx - bounds.safeBottomPx - handleHeightPx) / 2)
                .coerceAtLeast(100)
            return desiredY.coerceIn(-usableHalf, usableHalf)
        }

        internal fun isKnownGameOrExternalApp(foregroundPkg: String?, selfPkg: String): Boolean {
            if (foregroundPkg.isNullOrBlank()) return false
            if (foregroundPkg == selfPkg) return false
            if (foregroundPkg == "com.android.systemui") return false
            if (foregroundPkg.contains("launcher", ignoreCase = true)) return false
            return true
        }

        @androidx.annotation.VisibleForTesting
        internal var testWindowManagerOverride: WindowManager? = null
    }

    @androidx.annotation.VisibleForTesting
    internal fun attachedRootForTesting(): android.view.View? = rootContainer

    override fun onCreate() {
        super.onCreate()
        OverlayPreferences.load(applicationContext)
        isCompactMode = OverlayPreferences.config.value.defaultCompactMode
        logEvent("OverlayService onCreate() started (SDK ${Build.VERSION.SDK_INT}, OEM ${Build.MANUFACTURER})")

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Start foreground notification first to satisfy Android 8..15 FGS startup contract
        if (!createNotificationChannelAndStartForeground()) {
            logEvent("Foreground service start failed; stopping overlay safely.")
            _lastOverlayError.value = "Could not start the floating sidebar foreground notification."
            _isServiceRunning.value = false
            _isSessionOwnedOverlay.value = false
            stopSelf()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            logEvent("Overlay permission missing in onCreate -> stopping safely.")
            _lastOverlayError.value = "Display Over Other Apps permission is not granted."
            _isServiceRunning.value = false
            _isSessionOwnedOverlay.value = false
            stopForegroundSafely()
            stopSelf()
            return
        }

        if (!showOverlayTrigger()) {
            _isServiceRunning.value = false
            _isSessionOwnedOverlay.value = false
            stopForegroundSafely()
            stopSelf()
            return
        }

        _isServiceRunning.value = true
        _lastOverlayError.value = null
        startSessionTimer()
        startTelemetryPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground contract is fulfilled if onStartCommand fires after service reset or restart
        if (!isForegroundStarted) {
            if (!createNotificationChannelAndStartForeground()) {
                logEvent("Foreground start failed in onStartCommand -> stopping safely.")
                _lastOverlayError.value = "Unable to keep floating sidebar active in foreground."
                _isServiceRunning.value = false
                _isSessionOwnedOverlay.value = false
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            logEvent("SYSTEM_ALERT_WINDOW missing in onStartCommand -> stopping.")
            _isServiceRunning.value = false
            _isSessionOwnedOverlay.value = false
            detachOverlayViewsSafely()
            stopForegroundSafely()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.hasExtra(EXTRA_AUTO_STARTED_BY_SESSION) == true &&
            intent.getBooleanExtra(EXTRA_AUTO_STARTED_BY_SESSION, false) &&
            !_isServiceRunning.value
        ) {
            _isSessionOwnedOverlay.value = true
        }

        if (rootContainer == null) {
            if (!showOverlayTrigger()) {
                _isServiceRunning.value = false
                _isSessionOwnedOverlay.value = false
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
            _isServiceRunning.value = true
        }

        return START_NOT_STICKY
    }

    private fun resolveOverlayUiContext(): Context {
        val visualContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val displayManager = getSystemService(DisplayManager::class.java)
                val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && defaultDisplay != null) {
                    createWindowContext(
                        defaultDisplay,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        null
                    )
                } else {
                    val displayCtx = if (defaultDisplay != null) createDisplayContext(defaultDisplay) else this
                    displayCtx.createWindowContext(
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        null
                    )
                }
            } catch (t: Throwable) {
                logEvent("WindowContext fallback triggered: ${t.javaClass.simpleName}: ${t.message}")
                this
            }
        } else {
            this
        }
        return ContextThemeWrapper(visualContext, R.style.Theme_MyApplication)
    }

    private fun createNotificationChannelAndStartForeground(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "ZeroX Boost Global Overlay",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the ZeroX Boost floating gaming sidebar active over games"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("ZeroX Boost Sidebar Ready")
                .setContentText("Tap or slide the edge handle over your game to open controls.")
                .setSmallIcon(R.drawable.ic_notification_gamepad)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            isForegroundStarted = true
            logEvent("Foreground notification started cleanly (type=$fgsType)")
            true
        } catch (t: Throwable) {
            isForegroundStarted = false
            logEvent("Foreground start exception: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private fun stopForegroundSafely() {
        if (!isForegroundStarted) return
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        } finally {
            isForegroundStarted = false
        }
    }

    private fun startSessionTimer() {
        serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (isTimerRunning) {
                    sessionSeconds++
                }
            }
        }
    }

    private fun refreshPingNow() {
        serviceScope.launch {
            try {
                val burst = NetworkMonitor.measurePingStability()
                pingMs = burst.avgPing
                jitterMs = burst.jitter
            } catch (_: Exception) {}
        }
    }

    private fun startTelemetryPolling() {
        serviceScope.launch {
            logEvent("Battery-aware telemetry polling started")
            var firstPass = true
            val db = GameBoostDatabase.getDatabase(applicationContext)
            while (isActive) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@GamingOverlayService)) {
                        logEvent("Overlay permission revoked by user. Stopping service safely.")
                        _lastOverlayError.value = "Display Over Other Apps permission was revoked."
                        _isServiceRunning.value = false
                        _isSessionOwnedOverlay.value = false
                        detachOverlayViewsSafely()
                        stopForegroundSafely()
                        stopSelf()
                        break
                    }

                    val stats = withContext(Dispatchers.IO) {
                        DeviceMonitor.getDeviceStats(applicationContext)
                    }
                    memoryUsage = stats.memoryUsagePercent
                    availMemory = stats.availableMemoryGb
                    batteryTemp = stats.batteryTemperatureC
                    batteryLevel = stats.batteryLevelPercent
                    isCharging = stats.isCharging
                    thermalStatus = stats.powerManagerThermalStatus
                    thermalHeadroom = stats.thermalHeadroom
                    refreshRateHz = stats.displayRefreshRate

                    val reminderMins = OverlayPreferences.config.value.longSessionReminderMinutes
                    val protection = GamingProtectionEngine.evaluate(
                        stats = stats,
                        longSessionThresholdMinutes = reminderMins
                    )
                    protectionAlertHeadline = if (protection.overallSeverity != ProtectionSeverity.SAFE) {
                        protection.alerts.firstOrNull()?.title ?: protection.headline
                    } else null

                    val autopilot = SmartGamingManager.autopilotState.value
                    if (autopilot.isSessionActive) {
                        val elapsedSec = if (autopilot.sessionStartTimeMs > 0L) {
                            ((System.currentTimeMillis() - autopilot.sessionStartTimeMs) / 1000L).toInt().coerceAtLeast(0)
                        } else autopilot.sessionDurationSeconds
                        SmartGamingManager.updateSessionTelemetry(
                            stats = stats,
                            sessionSeconds = elapsedSec,
                            longSessionReminderMinutes = reminderMins
                        )
                    }
                    if (autopilot.isAutopilotEnabled && OverlayPreferences.config.value.autoDetectForegroundGame) {
                        val knownProfiles = withContext(Dispatchers.IO) {
                            db.gameSpaceDao().getAllGameProfiles().map { it.toDomain() }
                        }
                        SmartGamingManager.evaluateForegroundDetection(
                            context = applicationContext,
                            knownProfiles = knownProfiles,
                            database = db,
                            selfPackage = packageName
                        )
                    }

                    // Check queued Aim Trainer launch: open ONLY when the user leaves the running game
                    if (isAimTrainerQueued) {
                        val fgPkg = withContext(Dispatchers.IO) {
                            GameSpaceManager.getRecentForegroundPackage(applicationContext)
                        }
                        val leftGame = when {
                            _isAppInForeground.value -> true
                            fgPkg != null && queuedGamePackage != null && fgPkg != queuedGamePackage -> true
                            fgPkg != null && !isKnownGameOrExternalApp(fgPkg, packageName) -> true
                            else -> false
                        }
                        if (leftGame) {
                            isAimTrainerQueued = false
                            aimTrainerStatusHint = null
                            queuedGamePackage = null
                            _pendingAimTrainerOnReturn.value = false
                            launchMainDestination(ROUTE_AIM, AIM_TAB_TRAINER)
                        }
                    }

                    if (firstPass || isSidebarOpen) {
                        val burst = NetworkMonitor.measurePingStability()
                        pingMs = burst.avgPing
                        jitterMs = burst.jitter
                        firstPass = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logEvent("Telemetry polling warning: ${t.javaClass.simpleName}: ${t.message}")
                }
                val baseCadence = SmartGamingManager.autopilotState.value.monitoringIntervalMs
                val pollDelay = when {
                    isAimTrainerQueued -> 1500L
                    isSidebarOpen -> baseCadence.coerceIn(3000L, 8000L)
                    else -> baseCadence.coerceAtLeast(8000L)
                }
                delay(pollDelay)
            }
        }
    }

    private fun getSafeWindowBounds(): SafeWindowBounds {
        val dm = resources.displayMetrics
        val wm = windowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            return try {
                val metrics = wm.currentWindowMetrics
                val rect = metrics.bounds
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                SafeWindowBounds(
                    widthPx = rect.width().coerceAtLeast(dm.widthPixels),
                    heightPx = rect.height().coerceAtLeast(dm.heightPixels),
                    safeLeftPx = insets.left,
                    safeTopPx = insets.top,
                    safeRightPx = insets.right,
                    safeBottomPx = insets.bottom
                )
            } catch (_: Throwable) {
                SafeWindowBounds(dm.widthPixels, dm.heightPixels, 0, 0, 0, 0)
            }
        }
        val statusBarFallback = (24 * dm.density).toInt()
        return SafeWindowBounds(dm.widthPixels, dm.heightPixels, 0, statusBarFallback, 0, statusBarFallback)
    }

    private fun buildLayoutParams(open: Boolean, compact: Boolean): WindowManager.LayoutParams {
        val bounds = getSafeWindowBounds()
        val density = resources.displayMetrics.density
        val isLandscape = bounds.widthPx > bounds.heightPx
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (!open) {
            val triggerGrav = when (OverlayPreferences.config.value.triggerPosition) {
                "Top" -> Gravity.TOP
                "Bottom" -> Gravity.BOTTOM
                else -> Gravity.CENTER_VERTICAL
            }
            val handleHeightPx = (88 * density).toInt()
            savedHandleYOffsetPx = clampHandleY(savedHandleYOffsetPx, bounds, handleHeightPx)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.END or triggerGrav
            params.x = bounds.safeRightPx
            params.y = savedHandleYOffsetPx
        } else if (compact) {
            val maxW = ((bounds.widthPx - bounds.safeLeftPx - bounds.safeRightPx) * 0.60f).toInt()
            params.width = (176 * density).toInt().coerceAtMost(maxW.coerceAtLeast((156 * density).toInt()))
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.END or Gravity.TOP
            params.x = bounds.safeRightPx + (4 * density).toInt()
            params.y = bounds.safeTopPx + (16 * density).toInt()
        } else if (isLandscape) {
            val availW = (bounds.widthPx - bounds.safeLeftPx - bounds.safeRightPx).coerceAtLeast(300)
            val availH = (bounds.heightPx - bounds.safeTopPx - bounds.safeBottomPx).coerceAtLeast(240)

            params.width = availW
            params.height = availH
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.TOP or Gravity.START
            params.x = bounds.safeLeftPx
            params.y = bounds.safeTopPx
        } else {
            val availW = (bounds.widthPx - bounds.safeLeftPx - bounds.safeRightPx).coerceAtLeast(300)
            val targetWidth = (324 * density).toInt().coerceAtMost((availW * 0.90f).toInt())

            params.width = targetWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.x = bounds.safeRightPx
            params.y = 0
        }
        return params
    }

    private fun openSidebar() {
        collapseWindowJob?.cancel()
        collapseWindowJob = null
        if (isSidebarOpen) return
        logEvent("Handle activated -> Expanding gaming sidebar")
        // Resize WindowManager bounds first so Compose lays out inside full panel dimensions immediately
        updateOverlayWindowParams(open = true, compact = isCompactMode)
        isSidebarOpen = true
        rootContainer?.updateTouchMode(open = true)
        refreshPingNow()
    }

    private fun closeSidebar() {
        if (!isSidebarOpen) return
        logEvent("Sidebar minimized -> Collapsing to edge handle")
        isSidebarOpen = false
        rootContainer?.updateTouchMode(open = false)
        collapseWindowJob?.cancel()
        collapseWindowJob = serviceScope.launch {
            delay(170L)
            if (!isSidebarOpen) {
                updateOverlayWindowParams(open = false, compact = isCompactMode)
            }
        }
    }

    private fun handleRequestAimTrainer() {
        val fgPkg = GameSpaceManager.getRecentForegroundPackage(applicationContext)
        val inExternalGame = !_isAppInForeground.value && (fgPkg == null || isKnownGameOrExternalApp(fgPkg, packageName))
        if (inExternalGame) {
            isAimTrainerQueued = true
            queuedGamePackage = fgPkg
            _pendingAimTrainerOnReturn.value = true
            aimTrainerStatusHint = if (fgPkg != null) {
                "Queued — Aim Trainer opens when you exit the game."
            } else {
                "Exit your game & return to ZeroX Boost to open Aim Trainer."
            }
            logEvent("Aim Trainer deferred while game is active (fg=${fgPkg ?: "unknown"})")
        } else {
            isAimTrainerQueued = false
            aimTrainerStatusHint = null
            _pendingAimTrainerOnReturn.value = false
            launchMainDestination(ROUTE_AIM, AIM_TAB_TRAINER)
        }
    }

    private fun launchMainDestination(route: String, aimTab: Int = 0) {
        closeSidebar()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TARGET_ROUTE, route)
            putExtra(EXTRA_AIM_TAB, aimTab)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            logEvent("Failed to launch destination $route: ${t.message}")
        }
    }

    private fun showOverlayTrigger(): Boolean {
        if (rootContainer != null) {
            logEvent("Overlay view already attached")
            return true
        }

        return try {
            val uiContext = resolveOverlayUiContext()
            val resolvedWm = testWindowManagerOverride
                ?: (uiContext.getSystemService(WINDOW_SERVICE) as? WindowManager)
                ?: (getSystemService(WINDOW_SERVICE) as? WindowManager)
                ?: throw IllegalStateException("WindowManager unavailable")
            windowManager = resolvedWm

            val initialParams = buildLayoutParams(open = false, compact = false)

            val container = OverlayTouchContainer(
                context = uiContext,
                onTapOpen = { openSidebar() },
                onSwipeOpen = { openSidebar() },
                onOutsideTap = { closeSidebar() },
                onDragY = { startY, deltaY ->
                    val bounds = getSafeWindowBounds()
                    val handleHeightPx = (88 * resources.displayMetrics.density).toInt()
                    val clamped = clampHandleY(startY + deltaY, bounds, handleHeightPx)
                    savedHandleYOffsetPx = clamped
                    val view = rootContainer ?: return@OverlayTouchContainer
                    val lp = (view.layoutParams as? WindowManager.LayoutParams) ?: return@OverlayTouchContainer
                    lp.y = clamped
                    try {
                        windowManager?.updateViewLayout(view, lp)
                    } catch (_: Throwable) {}
                },
                getCurrentParamY = {
                    (rootContainer?.layoutParams as? WindowManager.LayoutParams)?.y ?: savedHandleYOffsetPx
                }
            ).apply {
                // Attach ViewTree*Owners to the root window view BEFORE ComposeView composition
                setViewTreeLifecycleOwner(this@GamingOverlayService)
                setViewTreeViewModelStoreOwner(this@GamingOverlayService)
                setViewTreeSavedStateRegistryOwner(this@GamingOverlayService)
            }

            val cv = ComposeView(uiContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(this@GamingOverlayService)
                setViewTreeViewModelStoreOwner(this@GamingOverlayService)
                setViewTreeSavedStateRegistryOwner(this@GamingOverlayService)

                setContent {
                    val overlayConfig by OverlayPreferences.config.collectAsState()
                    val autopilot by SmartGamingManager.autopilotState.collectAsState()
                    val autopilotLabel = when {
                        autopilot.isSessionActive -> "Autopilot: ${autopilot.activeProfile.displayName}"
                        autopilot.isAutopilotEnabled -> "Autopilot Armed"
                        else -> "Manual Control"
                    }
                    val activeProfileShort = when (autopilot.activeProfile) {
                        ProfileType.SMOOTHNESS_FOCUS -> "Stability"
                        ProfileType.BATTERY_FRIENDLY -> "Battery"
                        ProfileType.BALANCED -> "Balanced"
                    }

                    GameBoostTheme {
                        GamingSidebarContent(
                            isOpen = isSidebarOpen,
                            onOpen = { openSidebar() },
                            onClose = { closeSidebar() },
                            onModeChange = { compact ->
                                logEvent("Overlay layout switched: compact=$compact")
                                isCompactMode = compact
                                OverlayPreferences.update(applicationContext) { it.copy(defaultCompactMode = compact) }
                                updateOverlayWindowParams(open = true, compact = compact)
                            },
                            onStopService = {
                                logEvent("Stop requested from overlay sidebar")
                                _isSessionOwnedOverlay.value = false
                                stopSelf()
                            },
                            memoryUsage = memoryUsage,
                            availMemory = availMemory,
                            batteryTemp = batteryTemp,
                            batteryLevel = batteryLevel,
                            thermalStatus = thermalStatus,
                            thermalHeadroom = thermalHeadroom,
                            pingMs = pingMs,
                            jitterMs = jitterMs,
                            refreshRateHz = refreshRateHz,
                            isCharging = isCharging,
                            triggerPosition = overlayConfig.triggerPosition,
                            triggerWidthDp = overlayConfig.triggerWidthDp,
                            initialCompactMode = isCompactMode,
                            initialAccentTheme = overlayConfig.accentTheme,
                            initialPanelOpacity = overlayConfig.panelOpacity,
                            autopilotStatusLabel = autopilotLabel,
                            targetGameTitle = autopilot.targetGameTitle,
                            activeProfileLabel = activeProfileShort,
                            protectionAlertHeadline = protectionAlertHeadline,
                            sessionSeconds = sessionSeconds,
                            isTimerRunning = isTimerRunning,
                            onToggleTimer = { isTimerRunning = !isTimerRunning },
                            onResetTimer = { sessionSeconds = 0L },
                            onSelectProfile = { mode ->
                                val target = when (mode) {
                                    "Stability" -> ProfileType.SMOOTHNESS_FOCUS
                                    "Battery" -> ProfileType.BATTERY_FRIENDLY
                                    else -> ProfileType.BALANCED
                                }
                                SmartGamingManager.setTargetProfile(target)
                                logEvent("Overlay profile switched to ${target.displayName}")
                            },
                            onRefreshPing = { refreshPingNow() },
                            onOpenNetworkTools = { launchMainDestination(ROUTE_NETWORK) },
                            onOpenSensitivityCalc = { launchMainDestination(ROUTE_AIM, AIM_TAB_CALCULATOR) },
                            onRequestAimTrainer = { handleRequestAimTrainer() },
                            isAimTrainerQueued = isAimTrainerQueued,
                            aimTrainerStatusHint = aimTrainerStatusHint,
                            onCancelAimTrainerQueue = {
                                isAimTrainerQueued = false
                                aimTrainerStatusHint = null
                                queuedGamePackage = null
                                _pendingAimTrainerOnReturn.value = false
                            },
                            onPersistAppearance = { accent, opacity, compact ->
                                OverlayPreferences.update(applicationContext) {
                                    it.copy(accentTheme = accent, panelOpacity = opacity, defaultCompactMode = compact)
                                }
                            },
                            isSystemOverlay = true
                        )
                    }
                }
            }

            container.addView(
                cv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            resolvedWm.addView(container, initialParams)
            rootContainer = container
            composeView = cv
            logEvent("WindowManager.addView succeeded (type=${initialParams.type}, ctx=${uiContext.javaClass.simpleName})")
            true
        } catch (t: Throwable) {
            val errMsg = "Overlay attach failed (${t.javaClass.simpleName}): ${t.message ?: "WindowManager rejected overlay"}"
            logEvent(errMsg)
            _lastOverlayError.value = errMsg
            detachOverlayViewsSafely()
            false
        }
    }

    private fun updateOverlayWindowParams(open: Boolean, compact: Boolean) {
        val view = rootContainer ?: return
        val wm = windowManager ?: return
        try {
            val updated = buildLayoutParams(open = open, compact = compact)
            val isLandscapeDualWing = open && !compact && updated.gravity == (Gravity.TOP or Gravity.START)
            composeView?.layoutParams = FrameLayout.LayoutParams(
                if (isLandscapeDualWing) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT,
                if (isLandscapeDualWing) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT
            )
            wm.updateViewLayout(view, updated)
            logEvent("Window params updated: open=$open, compact=$compact, w=${updated.width}, h=${updated.height}")
        } catch (t: Throwable) {
            logEvent("WindowManager.updateViewLayout failed: ${t.javaClass.simpleName}: ${t.message}")
            _lastOverlayError.value = "Overlay layout update failed (${t.javaClass.simpleName})."
        }
    }

    private fun detachOverlayViewsSafely() {
        try {
            composeView?.disposeComposition()
        } catch (_: Throwable) {}
        rootContainer?.let { container ->
            try {
                windowManager?.removeViewImmediate(container)
            } catch (_: Throwable) {
                try {
                    windowManager?.removeView(container)
                } catch (_: Throwable) {}
            }
            try {
                container.removeAllViews()
            } catch (_: Throwable) {}
        }
        composeView = null
        rootContainer = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientationLabel = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
        logEvent("Screen rotation detected ($orientationLabel) -> Re-anchoring overlay safely")
        updateOverlayWindowParams(open = isSidebarOpen, compact = isCompactMode)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isServiceRunning.value = false
        _isSessionOwnedOverlay.value = false
        _pendingAimTrainerOnReturn.value = false
        isAimTrainerQueued = false
        collapseWindowJob?.cancel()
        collapseWindowJob = null
        logEvent("OverlayService onDestroy() -> Cleaning up WindowManager & resources")
        serviceScope.cancel()
        // Detach WindowManager & ComposeView while LifecycleRegistry is still valid
        detachOverlayViewsSafely()
        stopForegroundSafely()
        try {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED &&
                lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)
            ) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        } catch (_: Throwable) {}
        store.clear()
        super.onDestroy()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}

/**
 * Raw-coordinate touch router for the floating edge handle and outside-tap dismissal.
 * Eliminates WindowManager drag coordinate feedback loops and Android system edge-gesture hijacking.
 */
internal class OverlayTouchContainer(
    context: Context,
    private val onTapOpen: () -> Unit,
    private val onSwipeOpen: () -> Unit,
    private val onOutsideTap: () -> Unit,
    private val onDragY: (startParamY: Int, deltaYPx: Int) -> Unit,
    private val getCurrentParamY: () -> Int
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeOpenThresholdPx = (24f * context.resources.displayMetrics.density)

    private var isOpenMode = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamY = 0
    private var isDragging = false
    private var openedBySwipe = false

    fun updateTouchMode(open: Boolean) {
        isOpenMode = open
        isDragging = false
        openedBySwipe = false
        updateSystemGestureExclusion(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSystemGestureExclusion(right - left, bottom - top)
    }

    private fun updateSystemGestureExclusion(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        try {
            val rects = if (!isOpenMode) listOf(Rect(0, 0, w, h)) else emptyList()
            ViewCompat.setSystemGestureExclusionRects(this, rects)
        } catch (_: Throwable) {}
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isOpenMode) {
            // Let Compose handle every tap inside the expanded sidebar; outside taps arrive via ACTION_OUTSIDE
            return false
        }
        // When collapsed to the edge handle, handle tap / horizontal swipe / vertical drag directly via raw screen coords
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (isOpenMode) {
                onOutsideTap()
            }
            return true
        }
        if (isOpenMode) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startParamY = getCurrentParamY()
                isDragging = false
                openedBySwipe = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (openedBySwipe) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!isDragging && hypot(dx, dy) > touchSlop) {
                    if (abs(dx) > swipeOpenThresholdPx && abs(dx) > abs(dy) * 1.15f) {
                        openedBySwipe = true
                        onSwipeOpen()
                        return true
                    } else {
                        isDragging = true
                    }
                }
                if (isDragging) {
                    onDragY(startParamY, dy.toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging = isDragging
                val wasSwipe = openedBySwipe
                isDragging = false
                openedBySwipe = false
                if (!wasDragging && !wasSwipe) {
                    onTapOpen()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                openedBySwipe = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
