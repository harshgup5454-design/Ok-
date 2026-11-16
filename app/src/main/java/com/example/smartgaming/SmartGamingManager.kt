package com.example.smartgaming

import android.app.ActivityManager
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
        val hasUsage = GameSpaceManager.hasUsageStatsPermission(context)
        val autoDetectEnabled = OverlayPreferences.config.value.autoDetectForegroundGame
        val statusText = when {
            !autoDetectEnabled -> "Auto-detection off (Manual & Game Space launch active)"
            !hasUsage -> "Usage Access unavailable / not granted — grant in Settings for auto-launch detection"
            else -> "Usage Access active — monitoring supported games"
        }
        _autopilotState.update { current ->
            current.copy(
                usageAccessGranted = hasUsage,
                detectionStatus = statusText
            )
        }
        return hasUsage
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

        val hasUsage = GameSpaceManager.hasUsageStatsPermission(context)

        _autopilotState.update { current ->
            current.copy(
                isSessionActive = true,
                wasAutoDetected = wasAutoDetected,
                usageAccessGranted = hasUsage,
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
     * Evaluates foreground app state using UsageStatsManager when permitted,
     * updating detection status and triggering profile-matched session start/stop.
     */
    suspend fun evaluateForegroundDetection(
        context: Context,
        knownProfiles: List<GameProfile>,
        database: GameBoostDatabase,
        selfPackage: String = context.packageName
    ) {
        val current = _autopilotState.value
        val autoDetect = OverlayPreferences.config.value.autoDetectForegroundGame
        if (!current.isAutopilotEnabled || !autoDetect) {
            refreshUsageAccessStatus(context)
            return
        }

        val hasUsage = GameSpaceManager.hasUsageStatsPermission(context)
        if (!hasUsage) {
            _autopilotState.update {
                it.copy(
                    usageAccessGranted = false,
                    detectionStatus = "Usage Access denied or revoked — using manual launch mode"
                )
            }
            return
        }

        _autopilotState.update {
            it.copy(
                usageAccessGranted = true,
                detectionStatus = "Usage Access active — monitoring supported games"
            )
        }

        val fgPkg = GameSpaceManager.getRecentForegroundPackage(context)
        evaluateForegroundPackage(
            context = context,
            foregroundPackage = fgPkg,
            knownProfiles = knownProfiles,
            database = database,
            selfPackage = selfPackage
        )
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
     * Scans for running applications using public Android ActivityManager APIs.
     * Identifies background processes and allows opening system settings.
     */
    fun refreshBackgroundProcesses(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.packageManager
        val list = mutableListOf<BackgroundAppItem>()

        if (activityManager != null) {
            try {
                val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
                val myPkg = context.packageName

                for (proc in runningProcesses) {
                    if (proc.pkgList != null) {
                        for (pkg in proc.pkgList) {
                            if (pkg == myPkg || pkg.startsWith("com.android.") || pkg.startsWith("android")) continue

                            val appLabel = try {
                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                pkg
                            }

                            val importance = when (proc.importance) {
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Active Foreground"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible UI"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Background Service"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached / Standby"
                                else -> "Background Process"
                            }

                            val advice = when {
                                pkg.contains("music") || pkg.contains("spotify") -> "Background audio stream consuming network/audio buffer."
                                pkg.contains("social") || pkg.contains("instagram") || pkg.contains("facebook") -> "Heavy background push sync and RAM consumption."
                                pkg.contains("browser") || pkg.contains("chrome") -> "Multiple WebKit render processes consuming available RAM."
                                else -> "Consider restricting background battery activity in App Details."
                            }

                            list.add(
                                BackgroundAppItem(
                                    packageName = pkg,
                                    appName = appLabel,
                                    importanceCategory = importance,
                                    advice = advice
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If list is empty due to Android 11+ process isolation, provide known heavy apps if installed
        if (list.isEmpty()) {
            val commonApps = listOf(
                Pair("com.google.android.youtube", "YouTube"),
                Pair("com.whatsapp", "WhatsApp"),
                Pair("com.instagram.android", "Instagram"),
                Pair("com.facebook.katana", "Facebook"),
                Pair("com.spotify.music", "Spotify")
            )
            for ((pkg, label) in commonApps) {
                if (GameSpaceManager.isPackageInstalled(context, pkg)) {
                    list.add(
                        BackgroundAppItem(
                            packageName = pkg,
                            appName = label,
                            importanceCategory = "Installed App",
                            advice = "May sync data in background. Open App Settings to manage battery restrictions."
                        )
                    )
                }
            }
        }

        _backgroundApps.value = list.distinctBy { it.packageName }
    }

    /**
     * Opens official Android App Details Settings where the user can manage background activity,
     * restricted battery usage, or force stop the app themselves.
     */
    fun openAppDetailsSettings(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
