package com.example.smartgaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Potentially resource-intensive background app information.
 */
data class BackgroundAppItem(
    val packageName: String,
    val appName: String,
    val importanceCategory: String,
    val advice: String
)

/**
 * Live Autopilot State
 */
data class AutopilotState(
    val isAutopilotEnabled: Boolean = false,
    val activeProfile: ProfileType = ProfileType.BALANCED,
    val targetGameTitle: String = "Free Fire MAX",
    val targetGamePackage: String = "com.dts.freefiremax",
    val isSessionActive: Boolean = false,
    val wasAutoDetected: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val detectionStatus: String = "Manual launch mode",
    val sessionStartTimeMs: Long = 0L,
    val sessionDurationSeconds: Int = 0,
    val monitoringIntervalMs: Long = 6000L,
    val appliedSettings: List<String> = emptyList(),
    val skippedSettings: List<String> = emptyList(),
    val restoredDetails: List<String> = emptyList(),
    val missingPermissions: List<String> = emptyList(),
    val thermalStatus: String = "Normal",
    val isThermalThrottlingRisk: Boolean = false,
    val ramAvailableGb: Float = 0f,
    val batteryLevel: Int = 100,
    val networkLatencyMs: Int = -1,
    val networkJitterMs: Int = 0,
    val networkType: String = "Unknown",
    val protectionStatus: GamingProtectionStatus = GamingProtectionStatus(),
    val lastSessionSummary: String = "Autopilot idle. Switch Master ON to auto-optimize supported system settings for gaming."
)

object SmartGamingManager {

    private val _autopilotState = MutableStateFlow(AutopilotState())
    val autopilotState: StateFlow<AutopilotState> = _autopilotState.asStateFlow()

    private val _backgroundApps = MutableStateFlow<List<BackgroundAppItem>>(emptyList())
    val backgroundApps: StateFlow<List<BackgroundAppItem>> = _backgroundApps.asStateFlow()

    fun getIntervalForProfile(profile: ProfileType): Long = when (profile) {
        ProfileType.SMOOTHNESS_FOCUS -> 3000L
        ProfileType.BALANCED -> 6000L
        ProfileType.BATTERY_FRIENDLY -> 15000L
    }

    fun setAutopilotMasterEnabled(enabled: Boolean) {
        _autopilotState.update { it.copy(isAutopilotEnabled = enabled) }
    }

    fun setTargetProfile(profile: ProfileType) {
        _autopilotState.update {
            it.copy(
                activeProfile = profile,
                monitoringIntervalMs = getIntervalForProfile(profile)
            )
        }
    }

    fun setTargetGame(title: String, packageName: String) {
        _autopilotState.update {
            it.copy(targetGameTitle = title, targetGamePackage = packageName)
        }
    }

    fun updateNetworkTelemetry(netStats: NetworkStats) {
        _autopilotState.update { current ->
            current.copy(
                networkLatencyMs = netStats.pingMs,
                networkJitterMs = netStats.jitterMs,
                networkType = if (netStats.isConnected) netStats.connectionType else "Offline"
            )
        }
    }

    fun refreshUsageAccessStatus(context: Context): Boolean {
        _autopilotState.update { current ->
            current.copy(
                usageAccessGranted = false,
                detectionStatus = "Direct Gaming Space & Autopilot launch active (zero usage tracking)"
            )
        }
        return false
    }

    fun updateSessionTelemetry(
        stats: DeviceStats,
        sessionSeconds: Int = _autopilotState.value.sessionDurationSeconds,
        longSessionReminderMinutes: Int = 45
    ) {
        val protection = GamingProtectionEngine.evaluate(
            stats = stats,
            sessionSeconds = sessionSeconds,
            longSessionThresholdMinutes = longSessionReminderMinutes
        )
        _autopilotState.update { current ->
            current.copy(
                sessionDurationSeconds = sessionSeconds,
                thermalStatus = stats.powerManagerThermalStatus,
                isThermalThrottlingRisk = protection.isThermalThrottling || protection.overallSeverity == ProtectionSeverity.CRITICAL,
                ramAvailableGb = stats.availableMemoryGb,
                batteryLevel = stats.batteryLevelPercent,
                protectionStatus = protection
            )
        }
    }

    suspend fun startSmartGamingSession(
        context: Context,
        profileType: ProfileType,
        database: GameBoostDatabase,
        wasAutoDetected: Boolean = false,
        onPermissionRequired: (String) -> Unit
    ): Boolean {
        if (_autopilotState.value.isSessionActive) return true

        val profileDef = DeviceControlEngine.getProfileDefinition(profileType)
        val success = DeviceControlEngine.applyProfile(context, profileType, database, onPermissionRequired)
        val stats = DeviceMonitor.getDeviceStats(context)
        val protection = GamingProtectionEngine.evaluate(stats, 0)
        val missingPerms = mutableListOf<String>()
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        val canWrite = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
        } catch (_: Exception) { false }
        if (canWrite) {
            applied.add(
                if (profileDef.targetBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    "Display brightness set to Automatic"
                } else {
                    "Display brightness locked at ${(profileDef.targetBrightnessLevel * 100) / 255}% Manual"
                }
            )
            applied.add("Screen timeout set to ${profileDef.targetTimeoutMs / 60000} min")
            applied.add(if (profileDef.targetAutoRotate == 0) "Auto-rotate locked" else "Auto-rotate enabled")
        } else {
            missingPerms.add("Modify System Settings (for brightness, screen timeout & rotation)")
            skipped.add("Display brightness, screen timeout & auto-rotate skipped (permission missing)")
        }

        val canDnd = try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) notificationManager?.isNotificationPolicyAccessGranted == true else true
        } catch (_: Exception) { false }
        if (canDnd) {
            applied.add("Do Not Disturb policy applied")
        } else {
            missingPerms.add("Do Not Disturb Access (to mute mid-game call rings)")
            skipped.add("Do Not Disturb filter skipped (Notification Policy access missing)")
        }

        if (profileDef.enableWifiLock) {
            applied.add("Wi-Fi Low-Latency lock acquired")
        } else {
            applied.add("Standard Wi-Fi power profile maintained")
        }
        val intervalMs = getIntervalForProfile(profileType)
        applied.add("Thermal & battery telemetry monitoring (${intervalMs / 1000}s cadence)")
        skipped.add("In-game graphics, sensitivity & FPS caps: Read-only (OS sandbox)")

        _autopilotState.update { current ->
            current.copy(
                isSessionActive = true,
                wasAutoDetected = wasAutoDetected,
                usageAccessGranted = false,
                activeProfile = profileType,
                monitoringIntervalMs = intervalMs,
                sessionStartTimeMs = System.currentTimeMillis(),
                sessionDurationSeconds = 0,
                appliedSettings = applied,
                skippedSettings = skipped,
                restoredDetails = emptyList(),
                missingPermissions = missingPerms,
                thermalStatus = stats.powerManagerThermalStatus,
                isThermalThrottlingRisk = protection.isThermalThrottling || protection.overallSeverity == ProtectionSeverity.CRITICAL,
                ramAvailableGb = stats.availableMemoryGb,
                batteryLevel = stats.batteryLevelPercent,
                protectionStatus = protection,
                lastSessionSummary = "Active (${if (wasAutoDetected) "Auto-Detected" else "Manual"}): ${profileType.autopilotTitle} loaded for ${current.targetGameTitle}."
            )
        }
        return success
    }

    suspend fun stopSmartGamingSession(
        context: Context,
        database: GameBoostDatabase
    ) {
        if (!_autopilotState.value.isSessionActive) return
        com.example.service.GamingOverlayService.stopAutoStartedOverlay(context)
        val restoreResult = DeviceControlEngine.restoreOriginalSettingsDetailed(context, database)
        val duration = if (_autopilotState.value.sessionStartTimeMs > 0L) {
            ((System.currentTimeMillis() - _autopilotState.value.sessionStartTimeMs) / 1000L).toInt()
        } else 0

        val summary = buildString {
            append("Session ended (${duration}s). ")
            when {
                restoreResult.restoredSettings.isNotEmpty() && restoreResult.skippedUserModified.isNotEmpty() -> {
                    append("Restored: ${restoreResult.restoredSettings.joinToString(", ")}. Preserved manual changes: ${restoreResult.skippedUserModified.joinToString(", ")}.")
                }
                restoreResult.restoredSettings.isNotEmpty() -> {
                    append("Restored: ${restoreResult.restoredSettings.joinToString(", ")}.")
                }
                restoreResult.skippedUserModified.isNotEmpty() -> {
                    append("Preserved manual user changes (${restoreResult.skippedUserModified.joinToString(", ")}).")
                }
                !restoreResult.clearedBaseline -> {
                    append("Saved baseline retained (system permissions revoked before restore).")
                }
                else -> {
                    append("No system settings required restoration.")
                }
            }
        }

        _autopilotState.update { current ->
            current.copy(
                isSessionActive = false,
                wasAutoDetected = false,
                sessionDurationSeconds = duration,
                appliedSettings = emptyList(),
                skippedSettings = emptyList(),
                restoredDetails = restoreResult.restoredSettings,
                lastSessionSummary = summary
            )
        }
    }

    /**
     * Cross-app UsageStatsManager polling has been removed for privacy minimization;
     * refreshes honest manual session status without reading other apps' usage history.
     */
    suspend fun evaluateForegroundDetection(
        context: Context,
        knownProfiles: List<GameProfile>,
        database: GameBoostDatabase,
        selfPackage: String = context.packageName
    ) {
        refreshUsageAccessStatus(context)
    }

    /**
     * Deterministic package transition handler (also directly testable in unit/Robolectric tests).
     */
    suspend fun evaluateForegroundPackage(
        context: Context,
        foregroundPackage: String?,
        knownProfiles: List<GameProfile>,
        database: GameBoostDatabase,
        selfPackage: String = context.packageName
    ) {
        if (foregroundPackage.isNullOrBlank() || foregroundPackage == selfPackage) return

        val matchedGame = knownProfiles.firstOrNull {
            it.packageName == foregroundPackage && (it.isInLibrary || it.isInstalled)
        }

        val current = _autopilotState.value
        if (matchedGame != null) {
            val savedProfileType = DeviceControlEngine.mapGamePerformanceModeToProfile(matchedGame.performanceMode)
            setTargetGame(matchedGame.name, matchedGame.packageName)
            setTargetProfile(savedProfileType)
            if (!current.isSessionActive) {
                startSmartGamingSession(
                    context = context,
                    profileType = savedProfileType,
                    database = database,
                    wasAutoDetected = true,
                    onPermissionRequired = {}
                )
            }
        } else if (
            current.isSessionActive &&
            current.wasAutoDetected &&
            current.targetGamePackage.isNotBlank() &&
            foregroundPackage != current.targetGamePackage
        ) {
            stopSmartGamingSession(context, database)
        }
    }

    /**
     * Populates privacy-preserving system shortcuts for managing background battery restrictions
     * without scanning runningAppProcesses or probing unrelated third-party apps.
     */
    fun refreshBackgroundProcesses(context: Context) {
        _backgroundApps.value = listOf(
            BackgroundAppItem(
                packageName = SYSTEM_BATTERY_OPTIMIZATION_SETTINGS,
                appName = "Android Battery Optimization Settings",
                importanceCategory = "System Control",
                advice = "Open Android battery optimization settings to manage background restrictions per app."
            ),
            BackgroundAppItem(
                packageName = SYSTEM_MANAGE_APPS_SETTINGS,
                appName = "Installed Apps & Background Activity",
                importanceCategory = "System Control",
                advice = "Review installed apps in Android Settings and set heavy apps to 'Restricted' before gaming."
            )
        )
    }

    private const val SYSTEM_BATTERY_OPTIMIZATION_SETTINGS = "system:battery_optimization"
    private const val SYSTEM_MANAGE_APPS_SETTINGS = "system:manage_apps"

    /**
     * Opens official Android App Details or System Battery/Apps Settings where the user can
     * manage background activity or force stop apps manually.
     */
    fun openAppDetailsSettings(context: Context, packageName: String): Boolean {
        return try {
            val intent = when (packageName) {
                SYSTEM_BATTERY_OPTIMIZATION_SETTINGS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    }
                }
                SYSTEM_MANAGE_APPS_SETTINGS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
