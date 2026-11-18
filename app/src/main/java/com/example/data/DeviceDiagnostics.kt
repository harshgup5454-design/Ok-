package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import java.io.File

// ==========================================
// 1. STORAGE ANALYZER MODELS
// ==========================================
data class AppAccessibleFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val directoryCategory: String
)

data class StorageDiagnosticData(
    val totalInternalGb: Float,
    val availableInternalGb: Float,
    val usedInternalGb: Float,
    val usagePercent: Int,
    val appInternalCacheBytes: Long,
    val appExternalCacheBytes: Long,
    val appFilesBytes: Long,
    val totalAppFootprintFormatted: String,
    val largestAccessibleFiles: List<AppAccessibleFile>,
    val cleanupGuidance: String
)

// ==========================================
// 2. DISPLAY ANALYZER MODELS
// ==========================================
data class SupportedDisplayMode(
    val modeId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float
)

data class DisplayDiagnosticData(
    val resolutionPx: String,
    val resolutionDp: String,
    val densityDpi: Int,
    val densityScale: Float,
    val densityCategory: String,
    val currentRefreshRateHz: Float,
    val supportedModes: List<SupportedDisplayMode>,
    val isHdrSupported: Boolean,
    val hdrTypes: String,
    val isWideColorGamut: Boolean,
    val orientation: String,
    val educationalNote: String = "DISPLAY REFRESH RATE ≠ IN-GAME FPS:\n" +
            "• Refresh Rate (Hz): The hardware panel's fixed scanning speed per second.\n" +
            "• Render FPS: How fast the game engine actually finishes rendering frames.\n" +
            "• A 120Hz display with a 30 FPS game simply draws each rendered frame 4 times."
)

// ==========================================
// 3. BATTERY DIAGNOSTICS MODELS
// ==========================================
data class BatteryDiagnosticData(
    val levelPercent: Int,
    val status: String,
    val isCharging: Boolean,
    val powerSource: String,
    val temperatureC: Float,
    val voltageVolts: Float?, // null if unavailable
    val currentMilliAmperes: Int?, // null if hardware sensor doesn't expose current
    val healthCategory: String,
    val healthDisclaimer: String = "Android standard SDK exposes categorical health status (e.g. Good/Overheat). " +
            "Battery wear percentage is not exposed by standard Android APIs without OEM-specific vendor tools."
)

// ==========================================
// 4. SYSTEM COMPATIBILITY MODELS
// ==========================================
data class PermissionCheckItem(
    val permissionName: String,
    val description: String,
    val isGranted: Boolean,
    val isRequiredForCore: Boolean
)

data class SystemCompatibilityData(
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val overlayPermissionGranted: Boolean,
    val thermalStatusApiAvailable: Boolean,
    val thermalHeadroomApiAvailable: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isPowerSaveMode: Boolean,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val permissions: List<PermissionCheckItem>,
    val sandboxExplanations: String
)

// ==========================================
// 5. TOUCHSCREEN METRICS
// ==========================================
data class TouchPointMetric(
    val id: Int,
    val x: Float,
    val y: Float,
    val isDown: Boolean
)

data class SwipeMetricResult(
    val distancePx: Float,
    val durationMs: Long,
    val velocityPxPerSec: Float,
    val wasInterrupted: Boolean
)

data class EdgeResponseState(
    val leftEdgeHit: Boolean = false,
    val rightEdgeHit: Boolean = false,
    val topEdgeHit: Boolean = false,
    val bottomEdgeHit: Boolean = false
) {
    val allEdgesTested: Boolean
        get() = leftEdgeHit && rightEdgeHit && topEdgeHit && bottomEdgeHit
}

// ==========================================
// 6. OVERALL DIAGNOSTIC REPORT
// ==========================================
data class DeviceDiagnosticReport(
    val timestamp: Long,
    val deviceModel: String,
    val storageSummary: String,
    val displaySummary: String,
    val batterySummary: String,
    val thermalSummary: String,
    val compatibilitySummary: String,
    val recommendedActions: List<String>,
    val exportableText: String
)

// ==========================================
// DEVICE DIAGNOSTICS ENGINE
// ==========================================
object DeviceDiagnosticsEngine {

    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Storage Analyzer using official StatFs and app-accessible sandbox paths.
     */
    fun analyzeStorage(context: Context): StorageDiagnosticData {
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
        val availGb = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)
        val usedGb = totalGb - availGb
        val pct = if (totalGb > 0f) ((usedGb / totalGb) * 100).toInt() else 0

        var internalCacheBytes = 0L
        val accessibleFiles = mutableListOf<AppAccessibleFile>()

        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val len = file.length()
                        internalCacheBytes += len
                        if (len > 50 * 1024) { // >50KB
                            accessibleFiles.add(
                                AppAccessibleFile(
                                    name = file.name,
                                    path = file.path,
                                    sizeBytes = len,
                                    sizeFormatted = formatBytes(len),
                                    directoryCategory = "Internal Cache"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        var externalCacheBytes = 0L
        try {
            val extCache = context.externalCacheDir
            if (extCache != null && extCache.exists()) {
                extCache.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val len = file.length()
                        externalCacheBytes += len
                        if (len > 50 * 1024) {
                            accessibleFiles.add(
                                AppAccessibleFile(
                                    name = file.name,
                                    path = file.path,
                                    sizeBytes = len,
                                    sizeFormatted = formatBytes(len),
                                    directoryCategory = "External Cache"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        var appFilesBytes = 0L
        try {
            val filesDir = context.filesDir
            if (filesDir.exists()) {
                filesDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val len = file.length()
                        appFilesBytes += len
                        if (len > 50 * 1024) {
                            accessibleFiles.add(
                                AppAccessibleFile(
                                    name = file.name,
                                    path = file.path,
                                    sizeBytes = len,
                                    sizeFormatted = formatBytes(len),
                                    directoryCategory = "App Data"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val totalAppFootprint = internalCacheBytes + externalCacheBytes + appFilesBytes
        val sortedFiles = accessibleFiles.sortedByDescending { it.sizeBytes }.take(10)

        val guidance = "Android enforces strict sandbox security. Ordinary applications cannot view or delete files " +
                "belonging to other apps or system partitions. To safely reclaim device storage, visit Android System Settings > " +
                "Storage. ZeroX Boost never deletes personal files or game content automatically."

        return StorageDiagnosticData(
            totalInternalGb = String.format(java.util.Locale.US, "%.1f", totalGb).toFloat(),
            availableInternalGb = String.format(java.util.Locale.US, "%.1f", availGb).toFloat(),
            usedInternalGb = String.format(java.util.Locale.US, "%.1f", usedGb).toFloat(),
            usagePercent = pct,
            appInternalCacheBytes = internalCacheBytes,
            appExternalCacheBytes = externalCacheBytes,
            appFilesBytes = appFilesBytes,
            totalAppFootprintFormatted = formatBytes(totalAppFootprint),
            largestAccessibleFiles = sortedFiles,
            cleanupGuidance = guidance
        )
    }

    /**
     * Display Analyzer extracting resolution, density, supported modes, and color capabilities.
     */
    fun analyzeDisplay(context: Context): DisplayDiagnosticData {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { context.display } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION")
            wm?.defaultDisplay
        }

        val metrics = context.resources.displayMetrics
        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val densityScale = metrics.density

        val widthDp = (widthPx / densityScale).toInt()
        val heightDp = (heightPx / densityScale).toInt()

        val densityCategory = when {
            densityDpi >= 560 -> "xxxhdpi (ultra high)"
            densityDpi >= 400 -> "xxhdpi (extra high)"
            densityDpi >= 280 -> "xhdpi (high)"
            densityDpi >= 200 -> "hdpi (medium)"
            else -> "mdpi / ldpi (standard)"
        }

        val currentRefreshRate = try {
            display?.mode?.refreshRate ?: 60f
        } catch (_: Exception) {
            60f
        }

        val supportedModes = mutableListOf<SupportedDisplayMode>()
        if (display != null) {
            try {
                display.supportedModes.forEach { mode ->
                    supportedModes.add(
                        SupportedDisplayMode(
                            modeId = mode.modeId,
                            widthPx = mode.physicalWidth,
                            heightPx = mode.physicalHeight,
                            refreshRateHz = String.format(java.util.Locale.US, "%.1f", mode.refreshRate).toFloat()
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        if (supportedModes.isEmpty()) {
            supportedModes.add(
                SupportedDisplayMode(
                    modeId = 1,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    refreshRateHz = 60f
                )
            )
        }

        val isHdrSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && display != null) {
            try {
                val hdrCaps = display.hdrCapabilities
                hdrCaps != null && hdrCaps.supportedHdrTypes.isNotEmpty()
            } catch (_: Exception) { false }
        } else false

        val hdrTypes = if (isHdrSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && display != null) {
            try {
                display.hdrCapabilities?.supportedHdrTypes?.joinToString { type ->
                    when (type) {
                        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                        else -> "HDR Type $type"
                    }
                } ?: "Supported"
            } catch (_: Exception) { "Supported" }
        } else {
            "Standard Dynamic Range (SDR)"
        }

        val isWideGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && display != null) {
            try { display.isWideColorGamut } catch (_: Exception) { false }
        } else false

        val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "Landscape"
        } else {
            "Portrait"
        }

        return DisplayDiagnosticData(
            resolutionPx = "${widthPx} × ${heightPx} px",
            resolutionDp = "${widthDp} × ${heightDp} dp",
            densityDpi = densityDpi,
            densityScale = densityScale,
            densityCategory = densityCategory,
            currentRefreshRateHz = String.format(java.util.Locale.US, "%.0f", currentRefreshRate).toFloat(),
            supportedModes = supportedModes.distinctBy { "${it.widthPx}x${it.heightPx}@${it.refreshRateHz}" },
            isHdrSupported = isHdrSupported,
            hdrTypes = hdrTypes,
            isWideColorGamut = isWideGamut,
            orientation = orientation
        )
    }

    /**
     * Battery Diagnostics reading official BatteryManager parameters without fabricated health percentages.
     */
    fun analyzeBattery(context: Context): BatteryDiagnosticData {
        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = if (scale > 0 && level >= 0) ((level * 100) / scale).coerceIn(0, 100) else 100

        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL
        val statusStr = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging (On Battery)"
            BatteryManager.BATTERY_STATUS_FULL -> "Full (Plugged in)"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val pluggedInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val powerSource = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Qi Charger"
            else -> if (isCharging) "Plugged in" else "Unplugged"
        }

        val tempInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = if (tempInt > 0) tempInt / 10f else 0f

        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltageV = if (voltageMv > 0) String.format(java.util.Locale.US, "%.2f", voltageMv / 1000f).toFloat() else null

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var currentMa: Int? = null
        if (bm != null) {
            try {
                val currentProp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                if (currentProp != 0 && currentProp != Integer.MIN_VALUE && currentProp != Integer.MAX_VALUE) {
                    // Usually reported in microamperes (uA), convert to mA
                    currentMa = if (Math.abs(currentProp) > 10_000) currentProp / 1000 else currentProp
                }
            } catch (_: Exception) {}
        }

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat (High Temperature Risk)"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold (Low Temperature)"
            else -> "Good / Normal"
        }

        return BatteryDiagnosticData(
            levelPercent = pct,
            status = statusStr,
            isCharging = isCharging,
            powerSource = powerSource,
            temperatureC = tempC,
            voltageVolts = voltageV,
            currentMilliAmperes = currentMa,
            healthCategory = healthStr
        )
    }

    /**
     * System Compatibility verifying permissions, overlay availability, and thermal APIs.
     */
    fun analyzeSystemCompatibility(context: Context): SystemCompatibilityData {
        val pm = context.packageManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val thermalStatusAvail = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val thermalHeadroomAvail = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val isIgnoringBatteryOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            try { powerManager.isIgnoringBatteryOptimizations(context.packageName) } catch (_: Exception) { false }
        } else false

        val isPowerSave = powerManager?.isPowerSaveMode ?: false

        val memoryClass = activityManager?.memoryClass ?: 192
        val largeMemoryClass = activityManager?.largeMemoryClass ?: memoryClass

        val permissionsList = listOf(
            PermissionCheckItem(
                permissionName = "SYSTEM_ALERT_WINDOW",
                description = "Required for Floating Gaming Sidebar overlay tool over games",
                isGranted = overlayGranted,
                isRequiredForCore = false
            ),
            PermissionCheckItem(
                permissionName = "INTERNET & NETWORK_STATE",
                description = "Required for TCP gateway latency diagnostics & stability testing",
                isGranted = true, // Normal permissions declared in manifest
                isRequiredForCore = true
            ),
            PermissionCheckItem(
                permissionName = "PACKAGE_VISIBILITY (<queries>)",
                description = "Used to discover launchable installed games without QUERY_ALL_PACKAGES",
                isGranted = true,
                isRequiredForCore = true
            )
        )

        val sandboxExplanation = "Android SELinux isolates app process memories and restricts arbitrary hardware manipulation. " +
                "ZeroX Boost operates in full compliance with Android security policies without root access or dangerous permissions."

        return SystemCompatibilityData(
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            overlayPermissionGranted = overlayGranted,
            thermalStatusApiAvailable = thermalStatusAvail,
            thermalHeadroomApiAvailable = thermalHeadroomAvail,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOpt,
            isPowerSaveMode = isPowerSave,
            memoryClassMb = memoryClass,
            largeMemoryClassMb = largeMemoryClass,
            permissions = permissionsList,
            sandboxExplanations = sandboxExplanation
        )
    }

    /**
     * Generates a complete Diagnostic Report with exportable text format.
     */
    fun generateDiagnosticReport(
        context: Context,
        storage: StorageDiagnosticData,
        display: DisplayDiagnosticData,
        battery: BatteryDiagnosticData,
        system: SystemCompatibilityData,
        deviceStats: DeviceStats
    ): DeviceDiagnosticReport {
        val timestamp = System.currentTimeMillis()
        val deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"

        val storageSummary = "Internal Storage: ${storage.availableInternalGb} GB free / ${storage.totalInternalGb} GB total (${storage.usagePercent}% used). App cache: ${storage.totalAppFootprintFormatted}."
        val displaySummary = "Display: ${display.resolutionPx} (${display.densityDpi} DPI, ${display.densityCategory}) at ${display.currentRefreshRateHz.toInt()} Hz."
        val tempText = if (battery.temperatureC > 0f) "${battery.temperatureC}°C" else "N/A"
        val batterySummary = "Battery: ${battery.levelPercent}% (${battery.status}). Temperature: $tempText. Health: ${battery.healthCategory}." +
                if (battery.voltageVolts != null) " Voltage: ${battery.voltageVolts}V." else ""
        val thermalSummary = "PowerManager Thermal: ${deviceStats.powerManagerThermalStatus} (Code: ${deviceStats.powerManagerThermalStatusCode})."
        val compatibilitySummary = "Android ${system.androidVersion} (API ${system.sdkInt}). Overlay Permission: ${if (system.overlayPermissionGranted) "Granted" else "Pending"}. Power Save: ${if (system.isPowerSaveMode) "Active" else "Off"}."

        val actions = mutableListOf<String>()
        if (battery.temperatureC >= 40.0f) {
            actions.add("Battery temperature is elevated ($tempText). Remove heavy protective cases during extended gameplay and avoid fast-charging while gaming.")
        }
        if (deviceStats.powerManagerThermalStatusCode >= 2) {
            actions.add("OS signaled active thermal throttling. Reduce in-game graphic shader complexity to sustain steady frame rates.")
        }
        if (storage.usagePercent >= 90) {
            actions.add("Internal storage headroom is low (${storage.availableInternalGb} GB free). Free up storage via Android Settings to prevent asset streaming stutters.")
        }
        if (!system.overlayPermissionGranted) {
            actions.add("Grant 'Display over other apps' permission to enable the ZeroX Boost in-game floating toolbox.")
        }
        if (display.currentRefreshRateHz > 60f && battery.temperatureC >= 38f) {
            actions.add("Display is currently set to ${display.currentRefreshRateHz.toInt()} Hz. Switching to 60 Hz in Display Settings saves ~15% GPU/display power envelope.")
        }
        if (actions.isEmpty()) {
            actions.add("All hardware diagnostics are within optimal parameters for mobile gaming.")
        }

        val exportable = StringBuilder().apply {
            appendLine("=== ZEROX BOOST DEVICE DIAGNOSTIC REPORT ===")
            appendLine("Generated: ${java.util.Date(timestamp)}")
            appendLine("Device: $deviceModel")
            appendLine("OS: Android ${system.androidVersion} (SDK ${system.sdkInt}, Patch ${system.securityPatch})")
            appendLine("SoC / Board: ${deviceStats.cpuModel} (${deviceStats.cpuCores} cores)")
            appendLine("------------------------------------------")
            appendLine("1. STORAGE DIAGNOSTICS")
            appendLine("   • Internal: ${storage.availableInternalGb} GB available / ${storage.totalInternalGb} GB total (${storage.usagePercent}% used)")
            appendLine("   • ZeroX Boost Cache: ${storage.totalAppFootprintFormatted}")
            appendLine("2. DISPLAY DIAGNOSTICS")
            appendLine("   • Resolution: ${display.resolutionPx} (${display.resolutionDp})")
            appendLine("   • Density: ${display.densityDpi} DPI (${display.densityCategory})")
            appendLine("   • Refresh Rate: ${display.currentRefreshRateHz.toInt()} Hz (Supported: ${display.supportedModes.joinToString { "${it.refreshRateHz.toInt()}Hz" }})")
            appendLine("   • HDR Support: ${display.hdrTypes}")
            appendLine("3. BATTERY DIAGNOSTICS")
            appendLine("   • Level: ${battery.levelPercent}% • State: ${battery.status}")
            appendLine("   • Sensor Temperature: $tempText")
            if (battery.voltageVolts != null) appendLine("   • Voltage: ${battery.voltageVolts} V")
            if (battery.currentMilliAmperes != null) appendLine("   • Current: ${battery.currentMilliAmperes} mA")
            appendLine("   • Health Status: ${battery.healthCategory} (Categorical Android SDK Source)")
            appendLine("4. THERMAL & DVFS STATUS")
            appendLine("   • PowerManager Thermal Status: ${deviceStats.powerManagerThermalStatus}")
            appendLine("5. SYSTEM & PERMISSIONS")
            appendLine("   • Overlay Permission (Alert Window): ${if (system.overlayPermissionGranted) "Granted" else "Not Granted"}")
            appendLine("   • Memory Class: ${system.memoryClassMb} MB (Large Heap: ${system.largeMemoryClassMb} MB)")
            appendLine("------------------------------------------")
            appendLine("RECOMMENDED ACTIONS:")
            actions.forEach { act -> appendLine(" • $act") }
            appendLine("==========================================")
        }.toString()

        return DeviceDiagnosticReport(
            timestamp = timestamp,
            deviceModel = deviceModel,
            storageSummary = storageSummary,
            displaySummary = displaySummary,
            batterySummary = batterySummary,
            thermalSummary = thermalSummary,
            compatibilitySummary = compatibilitySummary,
            recommendedActions = actions,
            exportableText = exportable
        )
    }

    /**
     * Safely clears GAMEBOOST's own internal cache directory.
     */
    fun clearAppCache(context: Context): Long {
        var freed = 0L
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                freed += file.length()
                file.deleteRecursively()
            }
        } catch (_: Exception) {}
        return freed
    }
}
