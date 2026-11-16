package com.example.data

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ==========================================
// 1. DATA MODELS
// ==========================================

data class OemGamingSpaceInfo(
    val name: String,
    val packageName: String,
    val activityName: String? = null,
    val isAvailable: Boolean = false,
    val note: String = "GAMEBOOST does not control OEM features directly; it provides direct 1-tap launching to the official manufacturer utility."
)

data class DeviceCapabilities(
    val androidVersion: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val currentRefreshRateHz: Float = 60f,
    val supportedRefreshRates: List<Float> = listOf(60f),
    val isBatterySaverOn: Boolean = false,
    val thermalStatusDesc: String = "Normal",
    val thermalStatusCode: Int = 0,
    val totalRamGb: Float = 0f,
    val availRamGb: Float = 0f,
    val canWriteSettings: Boolean = false,
    val canAccessDnd: Boolean = false,
    val isWifiLockSupported: Boolean = true,
    val oemGamingSpace: OemGamingSpaceInfo? = null
)

data class SystemControlState(
    val brightnessMode: Int = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
    val brightnessLevel: Int = 128,
    val screenTimeoutMs: Int = 60000,
    val autoRotate: Int = 1,
    val dndFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL,
    val isWifiLockAcquired: Boolean = false
)

enum class ProfileType(val displayName: String, val autopilotTitle: String) {
    BALANCED("Balanced Profile", "Balanced Gaming"),
    SMOOTHNESS_FOCUS("Smoothness Focus Profile", "Maximum Stability"),
    BATTERY_FRIENDLY("Battery Friendly Profile", "Battery Friendly")
}

data class RestoreResult(
    val restoredAny: Boolean,
    val restoredSettings: List<String> = emptyList(),
    val skippedUserModified: List<String> = emptyList(),
    val clearedBaseline: Boolean = false
)

data class OptimizationProfile(
    val type: ProfileType,
    val displayName: String,
    val description: String,
    val targetBrightnessMode: Int,
    val targetBrightnessLevel: Int,
    val targetTimeoutMs: Int,
    val targetAutoRotate: Int,
    val targetDndFilter: Int,
    val enableWifiLock: Boolean,
    val appliedAuthorizedSettings: List<String>,
    val manualRecommendations: List<String>
)

// ==========================================
// 2. DEVICE CONTROL ENGINE IMPLEMENTATION
// ==========================================

object DeviceControlEngine {

    private var wifiLock: WifiManager.WifiLock? = null
    private const val PREFS_APPLIED_TARGETS = "gameboost_applied_targets"
    private const val KEY_APPLIED_BRIGHTNESS_MODE = "applied_brightness_mode"
    private const val KEY_APPLIED_BRIGHTNESS_LEVEL = "applied_brightness_level"
    private const val KEY_APPLIED_TIMEOUT_MS = "applied_timeout_ms"
    private const val KEY_APPLIED_AUTO_ROTATE = "applied_auto_rotate"
    private const val KEY_APPLIED_DND_FILTER = "applied_dnd_filter"

    fun mapGamePerformanceModeToProfile(mode: PerformanceMode): ProfileType = when (mode) {
        PerformanceMode.PERFORMANCE_ASSISTANT -> ProfileType.SMOOTHNESS_FOCUS
        PerformanceMode.BATTERY_SAVER, PerformanceMode.THERMAL_PROTECTION -> ProfileType.BATTERY_FRIENDLY
        PerformanceMode.BALANCED -> ProfileType.BALANCED
    }

    fun mapProfileToGamePerformanceMode(profile: ProfileType): PerformanceMode = when (profile) {
        ProfileType.SMOOTHNESS_FOCUS -> PerformanceMode.PERFORMANCE_ASSISTANT
        ProfileType.BALANCED -> PerformanceMode.BALANCED
        ProfileType.BATTERY_FRIENDLY -> PerformanceMode.BATTERY_SAVER
    }

    // Known OEM Gaming Mode packages and launcher entry points
    private val KNOWN_OEM_PACKAGES = listOf(
        Triple("Samsung Game Launcher", "com.samsung.android.game.gamehome", null),
        Triple("Xiaomi Game Turbo", "com.miui.securitycenter", "com.miui.gamebooster.ui.GameBoosterMainLaunchActivity"),
        Triple("OnePlus / OPPO Game Space", "com.heos.gameSpace", null),
        Triple("ColorOS / Realme Games", "com.coloros.gamespace", null),
        Triple("Vivo Game Cube", "com.vivo.gamecube", null),
        Triple("ASUS ROG Armoury Crate", "com.asus.armourycrate", null),
        Triple("Motorola Game Time", "com.motorola.gametime", null),
        Triple("RedMagic Game Space", "com.cn.redmagic.gamecenter", null),
        Triple("ZTE Game Space", "com.redmagic.gamespace", null)
    )

    fun detectCapabilities(context: Context): DeviceCapabilities {
        var currentHz = 60f
        var supportedRates = listOf(60f)
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                currentHz = display.refreshRate
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val modeRates = display.supportedModes.map { it.refreshRate.toInt().toFloat() }
                    if (modeRates.isNotEmpty()) {
                        supportedRates = modeRates.distinct().sortedDescending()
                    }
                } else {
                    supportedRates = listOf(currentHz, 60f).distinct().sortedDescending()
                }
            }
        } catch (_: Exception) {
            currentHz = 60f
            supportedRates = listOf(60f)
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatterySaverOn = powerManager?.isPowerSaveMode ?: false

        var thermalStatusCode = 0
        var thermalStatusDesc = "Normal (No OS throttling)"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                thermalStatusCode = powerManager.currentThermalStatus
                thermalStatusDesc = when (thermalStatusCode) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normal (Level 0)"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light Thermal Load (Level 1)"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Load (Level 2)"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe Thermal Load (Level 3)"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling (Level 4)"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Cooling (Level 5)"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown Imminent (Level 6)"
                    else -> "Thermal Level $thermalStatusCode"
                }
            } catch (_: Exception) {}
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        var totalRamGb = 0f
        var availRamGb = 0f
        if (activityManager != null) {
            try {
                activityManager.getMemoryInfo(memInfo)
                totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
                availRamGb = memInfo.availMem / (1024f * 1024f * 1024f)
            } catch (_: Exception) {}
        }

        val canWriteSettings = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true
        } catch (_: Exception) { false }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val canAccessDnd = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                notificationManager.isNotificationPolicyAccessGranted
            } else true
        } catch (_: Exception) { false }

        val oemInfo = detectOemGamingSpace(context)

        return DeviceCapabilities(
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            currentRefreshRateHz = currentHz,
            supportedRefreshRates = supportedRates,
            isBatterySaverOn = isBatterySaverOn,
            thermalStatusDesc = thermalStatusDesc,
            thermalStatusCode = thermalStatusCode,
            totalRamGb = totalRamGb,
            availRamGb = availRamGb,
            canWriteSettings = canWriteSettings,
            canAccessDnd = canAccessDnd,
            isWifiLockSupported = true,
            oemGamingSpace = oemInfo
        )
    }

    private fun detectOemGamingSpace(context: Context): OemGamingSpaceInfo? {
        val pm = context.packageManager
        for ((name, pkg, activity) in KNOWN_OEM_PACKAGES) {
            try {
                if (activity != null) {
                    val intent = Intent().setClassName(pkg, activity)
                    if (intent.resolveActivity(pm) != null) {
                        return OemGamingSpaceInfo(name, pkg, activity, true)
                    }
                }
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    return OemGamingSpaceInfo(name, pkg, null, true)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun launchOemGamingSpace(context: Context, info: OemGamingSpaceInfo): Boolean {
        if (!info.isAvailable) return false
        val pm = context.packageManager
        try {
            if (info.activityName != null) {
                val intent = Intent().apply {
                    setClassName(info.packageName, info.activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    return true
                }
            }
            val launchIntent = pm.getLaunchIntentForPackage(info.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    fun getCurrentControlState(context: Context): SystemControlState {
        val contentResolver = context.contentResolver

        val brightnessMode = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (_: Exception) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }

        val brightnessLevel = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }

        val timeoutMs = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Exception) {
            60000
        }

        val autoRotate = try {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION)
        } catch (_: Exception) {
            1
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dndFilter = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.currentInterruptionFilter
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
        } catch (_: Exception) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        return SystemControlState(
            brightnessMode = brightnessMode,
            brightnessLevel = brightnessLevel,
            screenTimeoutMs = timeoutMs,
            autoRotate = autoRotate,
            dndFilter = dndFilter,
            isWifiLockAcquired = wifiLock?.isHeld == true
        )
    }

    fun getProfileDefinition(type: ProfileType): OptimizationProfile {
        return when (type) {
            ProfileType.BALANCED -> OptimizationProfile(
                type = ProfileType.BALANCED,
                displayName = "Balanced Profile",
                description = "Optimized balance between display clarity, notification moderation, and battery longevity.",
                targetBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                targetBrightnessLevel = 150,
                targetTimeoutMs = 120000, // 2 minutes
                targetAutoRotate = 1, // Auto-rotate ON
                targetDndFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                enableWifiLock = false,
                appliedAuthorizedSettings = listOf(
                    "Set Screen Timeout to 2 Minutes",
                    "Enable Automatic Screen Brightness",
                    "Enable Accelerometer Auto-Rotate",
                    "Set Do Not Disturb to Priority Notifications"
                ),
                manualRecommendations = listOf(
                    "In Game Menu: Set graphics to Medium/High with 60 FPS cap.",
                    "Developer Options: Set Window & Transition animation scales to 0.5x."
                )
            )

            ProfileType.SMOOTHNESS_FOCUS -> OptimizationProfile(
                type = ProfileType.SMOOTHNESS_FOCUS,
                displayName = "Smoothness Focus Profile",
                description = "Maximum gameplay stability: locks screen rotation, extends screen timeout to 30 minutes, enables strict DND, and locks High-Performance Wi-Fi.",
                targetBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                targetBrightnessLevel = 204, // ~80%
                targetTimeoutMs = 1800000, // 30 minutes
                targetAutoRotate = 0, // Auto-rotate LOCKED
                targetDndFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS,
                enableWifiLock = true,
                appliedAuthorizedSettings = listOf(
                    "Lock Screen Timeout to 30 Minutes (Prevents screen off during long matches)",
                    "Lock Display Brightness at 80% (Prevents automatic dimming)",
                    "Lock Orientation / Disable Auto-Rotate (Prevents accidental tilt rotation)",
                    "Enable Strict Do Not Disturb (Blocks non-alarm popups)",
                    "Acquire High-Performance Wi-Fi Low Latency Lock"
                ),
                manualRecommendations = listOf(
                    "In Game Menu: Enable Target High Frame Rate in Graphics Settings.",
                    "Device Display Settings: Select highest available panel refresh rate (90Hz / 120Hz).",
                    "Developer Options: Set Animation Scale to 0.5x; Set USB to Charging Only."
                )
            )

            ProfileType.BATTERY_FRIENDLY -> OptimizationProfile(
                type = ProfileType.BATTERY_FRIENDLY,
                displayName = "Battery Friendly Profile",
                description = "Extends gaming session time: lowers screen brightness to 35%, sets 1-minute timeout, and disables unnecessary locks.",
                targetBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                targetBrightnessLevel = 90, // ~35%
                targetTimeoutMs = 60000, // 1 minute
                targetAutoRotate = 0,
                targetDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
                enableWifiLock = false,
                appliedAuthorizedSettings = listOf(
                    "Set Screen Brightness to 35% Manual",
                    "Set Screen Timeout to 1 Minute",
                    "Disable Auto-Rotate",
                    "Restore Normal Notifications",
                    "Release Wi-Fi Low-Latency Lock"
                ),
                manualRecommendations = listOf(
                    "In Game Menu: Lower Graphic Resolution and Cap FPS to 30/45 FPS.",
                    "Android System Settings: Enable System Battery Saver Mode."
                )
            )
        }
    }

    suspend fun applyProfile(
        context: Context,
        profileType: ProfileType,
        database: GameBoostDatabase,
        onPermissionRequired: (String) -> Unit
    ): Boolean {
        val (allAuthorizedApplied, missingPermissions) = withContext(Dispatchers.IO) {
            val currentState = getCurrentControlState(context)
            val dao = database.deviceControlDao()
            val profile = getProfileDefinition(profileType)

            val canWrite = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
            } catch (_: Exception) { false }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val canDnd = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                    notificationManager.isNotificationPolicyAccessGranted
                } else true
            } catch (_: Exception) { false }

            val willChangeBrightnessLevel = canWrite && profile.targetBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

            // 1. Record original baseline ONLY for settings GAMEBOOST will actually modify
            val existingSavedState = dao.getSavedSystemState()
            val baselineState = if (existingSavedState == null) {
                SavedSystemStateEntity(
                    id = "original_state",
                    timestamp = System.currentTimeMillis(),
                    originalBrightnessMode = if (canWrite) currentState.brightnessMode else -1,
                    originalBrightnessLevel = if (willChangeBrightnessLevel) currentState.brightnessLevel else -1,
                    originalScreenTimeoutMs = if (canWrite) currentState.screenTimeoutMs else -1,
                    originalAutoRotate = if (canWrite) currentState.autoRotate else -1,
                    originalDndFilter = if (canDnd && notificationManager != null) currentState.dndFilter else -1,
                    appliedProfileName = profileType.displayName,
                    lastAppliedTimestamp = System.currentTimeMillis()
                )
            } else {
                // Preserve existing original baselines and backfill any newly authorized fields
                existingSavedState.copy(
                    originalBrightnessMode = if (existingSavedState.originalBrightnessMode != -1) existingSavedState.originalBrightnessMode else if (canWrite) currentState.brightnessMode else -1,
                    originalBrightnessLevel = if (existingSavedState.originalBrightnessLevel != -1) existingSavedState.originalBrightnessLevel else if (willChangeBrightnessLevel) currentState.brightnessLevel else -1,
                    originalScreenTimeoutMs = if (existingSavedState.originalScreenTimeoutMs != -1) existingSavedState.originalScreenTimeoutMs else if (canWrite) currentState.screenTimeoutMs else -1,
                    originalAutoRotate = if (existingSavedState.originalAutoRotate != -1) existingSavedState.originalAutoRotate else if (canWrite) currentState.autoRotate else -1,
                    originalDndFilter = if (existingSavedState.originalDndFilter != -1) existingSavedState.originalDndFilter else if (canDnd && notificationManager != null) currentState.dndFilter else -1,
                    appliedProfileName = profileType.displayName,
                    lastAppliedTimestamp = System.currentTimeMillis()
                )
            }
            dao.saveSystemState(baselineState)

            var allApplied = true
            val missingPerms = mutableListOf<String>()
            var appliedBrightnessMode = -1
            var appliedBrightnessLevel = -1
            var appliedTimeoutMs = -1
            var appliedAutoRotate = -1
            var appliedDndFilter = -1

            // 2. Apply WRITE_SETTINGS adjustments if authorized
            if (canWrite) {
                try {
                    val cr = context.contentResolver
                    if (Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, profile.targetBrightnessMode)) {
                        appliedBrightnessMode = profile.targetBrightnessMode
                    }
                    if (profile.targetBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                        if (Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, profile.targetBrightnessLevel)) {
                            appliedBrightnessLevel = profile.targetBrightnessLevel
                        }
                    }
                    if (Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, profile.targetTimeoutMs)) {
                        appliedTimeoutMs = profile.targetTimeoutMs
                    }
                    if (Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, profile.targetAutoRotate)) {
                        appliedAutoRotate = profile.targetAutoRotate
                    }
                } catch (_: Exception) {
                    allApplied = false
                }
            } else {
                allApplied = false
                missingPerms.add("android.permission.WRITE_SETTINGS")
            }

            // 3. Apply DND Policy adjustments if authorized
            if (canDnd && notificationManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        notificationManager.setInterruptionFilter(profile.targetDndFilter)
                        appliedDndFilter = profile.targetDndFilter
                    }
                } catch (_: Exception) {
                    allApplied = false
                }
            } else if (!canDnd) {
                allApplied = false
                missingPerms.add("android.permission.ACCESS_NOTIFICATION_POLICY")
            }

            saveAppliedTargets(
                context = context,
                brightnessMode = appliedBrightnessMode,
                brightnessLevel = appliedBrightnessLevel,
                timeoutMs = appliedTimeoutMs,
                autoRotate = appliedAutoRotate,
                dndFilter = appliedDndFilter
            )

            // 4. Wi-Fi Low Latency Lock
            if (profile.enableWifiLock) {
                acquireWifiLock(context)
            } else {
                releaseWifiLock()
            }

            Pair(allApplied, missingPerms.distinct())
        }

        if (missingPermissions.isNotEmpty()) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                missingPermissions.forEach { onPermissionRequired(it) }
            } else {
                withContext(Dispatchers.Main) {
                    missingPermissions.forEach { onPermissionRequired(it) }
                }
            }
        }

        return allAuthorizedApplied
    }

    suspend fun restoreOriginalSettings(
        context: Context,
        database: GameBoostDatabase
    ): Boolean = restoreOriginalSettingsDetailed(context, database).let { it.restoredAny || it.clearedBaseline }

    suspend fun restoreOriginalSettingsDetailed(
        context: Context,
        database: GameBoostDatabase
    ): RestoreResult = withContext(Dispatchers.IO) {
        val dao = database.deviceControlDao()
        val savedState = dao.getSavedSystemState() ?: run {
            releaseWifiLock()
            clearAppliedTargets(context)
            return@withContext RestoreResult(restoredAny = false)
        }

        val canWrite = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
        } catch (_: Exception) { false }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val canDnd = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                notificationManager.isNotificationPolicyAccessGranted
            } else true
        } catch (_: Exception) { false }

        val currentState = getCurrentControlState(context)
        val targets = readAppliedTargets(context)

        val restoredSettings = mutableListOf<String>()
        val skippedUserModified = mutableListOf<String>()
        val cr = context.contentResolver

        if (canWrite) {
            try {
                // Only restore brightness if user didn't manually change brightness mode or manual brightness level
                val userModifiedMode = targets.brightnessMode != -1 && currentState.brightnessMode != targets.brightnessMode
                val userModifiedLevel = targets.brightnessLevel != -1 && currentState.brightnessLevel != targets.brightnessLevel
                if (savedState.originalBrightnessMode != -1 || savedState.originalBrightnessLevel != -1) {
                    if (userModifiedMode || userModifiedLevel) {
                        skippedUserModified.add("Screen Brightness")
                    } else {
                        var didRestoreBrightness = false
                        if (savedState.originalBrightnessMode != -1) {
                            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, savedState.originalBrightnessMode)
                            didRestoreBrightness = true
                        }
                        if (savedState.originalBrightnessLevel != -1) {
                            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, savedState.originalBrightnessLevel)
                            didRestoreBrightness = true
                        }
                        if (didRestoreBrightness) restoredSettings.add("Screen Brightness")
                    }
                }

                if (savedState.originalScreenTimeoutMs != -1) {
                    if (targets.timeoutMs != -1 && currentState.screenTimeoutMs != targets.timeoutMs) {
                        skippedUserModified.add("Screen Timeout")
                    } else {
                        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, savedState.originalScreenTimeoutMs)
                        restoredSettings.add("Screen Timeout")
                    }
                }

                if (savedState.originalAutoRotate != -1) {
                    if (targets.autoRotate != -1 && currentState.autoRotate != targets.autoRotate) {
                        skippedUserModified.add("Auto-Rotate")
                    } else {
                        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, savedState.originalAutoRotate)
                        restoredSettings.add("Auto-Rotate")
                    }
                }
            } catch (_: Exception) {}
        }

        if (canDnd && notificationManager != null && savedState.originalDndFilter != -1) {
            try {
                if (targets.dndFilter != -1 && currentState.dndFilter != targets.dndFilter) {
                    skippedUserModified.add("Do Not Disturb")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notificationManager.setInterruptionFilter(savedState.originalDndFilter)
                    restoredSettings.add("Do Not Disturb")
                }
            } catch (_: Exception) {}
        }

        val hadWifiLock = wifiLock?.isHeld == true
        releaseWifiLock()
        if (hadWifiLock) {
            restoredSettings.add("Wi-Fi Low-Latency Lock")
        }

        val hasWriteSaved = savedState.originalBrightnessMode != -1 ||
            savedState.originalBrightnessLevel != -1 ||
            savedState.originalScreenTimeoutMs != -1 ||
            savedState.originalAutoRotate != -1
        val hasDndSaved = savedState.originalDndFilter != -1
        val writeHandled = !hasWriteSaved || canWrite
        val dndHandled = !hasDndSaved || canDnd

        if (writeHandled && dndHandled) {
            dao.clearSavedSystemState()
            clearAppliedTargets(context)
            return@withContext RestoreResult(
                restoredAny = restoredSettings.isNotEmpty(),
                restoredSettings = restoredSettings,
                skippedUserModified = skippedUserModified,
                clearedBaseline = true
            )
        } else {
            // Keep only the settings whose permissions were revoked/missing so they aren't lost
            val updatedBaseline = savedState.copy(
                originalBrightnessMode = if (writeHandled) -1 else savedState.originalBrightnessMode,
                originalBrightnessLevel = if (writeHandled) -1 else savedState.originalBrightnessLevel,
                originalScreenTimeoutMs = if (writeHandled) -1 else savedState.originalScreenTimeoutMs,
                originalAutoRotate = if (writeHandled) -1 else savedState.originalAutoRotate,
                originalDndFilter = if (dndHandled) -1 else savedState.originalDndFilter
            )
            dao.saveSystemState(updatedBaseline)
            return@withContext RestoreResult(
                restoredAny = restoredSettings.isNotEmpty(),
                restoredSettings = restoredSettings,
                skippedUserModified = skippedUserModified,
                clearedBaseline = false
            )
        }
    }

    private data class AppliedTargets(
        val brightnessMode: Int = -1,
        val brightnessLevel: Int = -1,
        val timeoutMs: Int = -1,
        val autoRotate: Int = -1,
        val dndFilter: Int = -1
    )

    private fun saveAppliedTargets(
        context: Context,
        brightnessMode: Int,
        brightnessLevel: Int,
        timeoutMs: Int,
        autoRotate: Int,
        dndFilter: Int
    ) {
        try {
            context.applicationContext.getSharedPreferences(PREFS_APPLIED_TARGETS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_APPLIED_BRIGHTNESS_MODE, brightnessMode)
                .putInt(KEY_APPLIED_BRIGHTNESS_LEVEL, brightnessLevel)
                .putInt(KEY_APPLIED_TIMEOUT_MS, timeoutMs)
                .putInt(KEY_APPLIED_AUTO_ROTATE, autoRotate)
                .putInt(KEY_APPLIED_DND_FILTER, dndFilter)
                .apply()
        } catch (_: Exception) {}
    }

    private fun readAppliedTargets(context: Context): AppliedTargets {
        return try {
            val p = context.applicationContext.getSharedPreferences(PREFS_APPLIED_TARGETS, Context.MODE_PRIVATE)
            AppliedTargets(
                brightnessMode = p.getInt(KEY_APPLIED_BRIGHTNESS_MODE, -1),
                brightnessLevel = p.getInt(KEY_APPLIED_BRIGHTNESS_LEVEL, -1),
                timeoutMs = p.getInt(KEY_APPLIED_TIMEOUT_MS, -1),
                autoRotate = p.getInt(KEY_APPLIED_AUTO_ROTATE, -1),
                dndFilter = p.getInt(KEY_APPLIED_DND_FILTER, -1)
            )
        } catch (_: Exception) {
            AppliedTargets()
        }
    }

    private fun clearAppliedTargets(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREFS_APPLIED_TARGETS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (_: Exception) {}
    }

    fun setBrightness(context: Context, mode: Int, level: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) return false
        return try {
            val cr = context.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(1, 255))
            }
            true
        } catch (_: Exception) { false }
    }

    fun setScreenTimeout(context: Context, timeoutMs: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            true
        } catch (_: Exception) { false }
    }

    fun setAutoRotate(context: Context, rotate: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, rotate)
            true
        } catch (_: Exception) { false }
    }

    fun setDndFilter(context: Context, filter: Int): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.setInterruptionFilter(filter)
            }
            true
        } catch (_: Exception) { false }
    }

    fun acquireWifiLock(context: Context) {
        if (wifiLock?.isHeld == true) return
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager.createWifiLock(mode, "GAMEBOOST_WifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {}
    }

    fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }
}
