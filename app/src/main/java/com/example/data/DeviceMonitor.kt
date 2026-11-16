package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import android.view.WindowManager
import java.util.Locale

data class DeviceStats(
    val totalMemoryGb: Float = 0f,
    val availableMemoryGb: Float = 0f,
    val memoryUsagePercent: Int = 0,
    val isLowMemory: Boolean = false,
    val memoryThresholdGb: Float = 0f,
    val batteryLevelPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTemperatureC: Float = 0f,
    val batteryHealth: String = "Good",
    val powerSource: String = "Battery",
    val isPowerSaveMode: Boolean = false,
    val totalStorageGb: Float = 0f,
    val availableStorageGb: Float = 0f,
    val storageUsagePercent: Int = 0,
    val cpuModel: String = Build.HARDWARE,
    val cpuBoard: String = Build.BOARD,
    val deviceModel: String = Build.MODEL,
    val androidVersion: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val supportedAbis: String = Build.SUPPORTED_ABIS.joinToString(", "),
    val powerManagerThermalStatus: String = "Normal",
    val powerManagerThermalStatusCode: Int = 0,
    val isThermalStatusSupported: Boolean = false,
    val thermalHeadroom: Float = -1f, // -1f means unsupported / NaN
    val isThermalHeadroomSupported: Boolean = false,
    val displayRefreshRate: Float = 60f,
    val screenResolution: String = "Unavailable",
    val cpuLoadNote: String = "Per-process CPU clock & cross-app CPU load are isolated by Android SELinux on unrooted devices"
)

object DeviceMonitor {

    fun getDeviceStats(context: Context): DeviceStats {
        // Memory Monitoring via ActivityManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        var totalMemGb = 0f
        var availMemGb = 0f
        var thresholdMemGb = 0f
        var isLowMem = false
        if (activityManager != null) {
            try {
                activityManager.getMemoryInfo(memInfo)
                totalMemGb = memInfo.totalMem / (1024f * 1024f * 1024f)
                availMemGb = memInfo.availMem / (1024f * 1024f * 1024f)
                thresholdMemGb = memInfo.threshold / (1024f * 1024f * 1024f)
                isLowMem = memInfo.lowMemory
            } catch (_: Exception) {}
        }
        val usedMemGb = totalMemGb - availMemGb
        val memUsagePercent = if (totalMemGb > 0f) ((usedMemGb / totalMemGb) * 100).toInt() else 0

        // Battery Monitoring via BatteryManager (sticky system broadcast)
        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (scale > 0 && level >= 0) (level * 100) / scale else 100

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val pluggedInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val powerSource = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Plugged" else "On Battery"
        }

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val batteryHealth = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val tempInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = if (tempInt > 0) tempInt / 10f else 0f

        // Storage Monitoring via StatFs
        var totalStorageGb = 0f
        var availStorageGb = 0f
        var storageUsagePercent = 0
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            totalStorageGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
            availStorageGb = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)
            val usedStorageGb = totalStorageGb - availStorageGb
            storageUsagePercent = if (totalStorageGb > 0f) ((usedStorageGb / totalStorageGb) * 100).toInt() else 0
        } catch (_: Exception) {}

        // Android PowerManager Thermal Status & Battery Saver
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = try {
            powerManager?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
        var thermalStatusCode = -1
        var thermalStatusDesc = "Unavailable (Requires Android 10+)"
        var isThermalStatusSupported = false
        var thermalHeadroom = -1f
        var isThermalHeadroomSupported = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                thermalStatusCode = powerManager.currentThermalStatus
                isThermalStatusSupported = true
                thermalStatusDesc = when (thermalStatusCode) {
                    PowerManager.THERMAL_STATUS_NONE -> "Normal (No throttling reported by OS)"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light (Minor throttling risk reported by OS)"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Throttling may begin depending on OEM)"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe (OS reports thermal throttling active)"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical (Heavy thermal throttling active)"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency (Emergency thermal state)"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown (Thermal shutdown triggered)"
                    else -> "Unknown status ($thermalStatusCode)"
                }

                // Thermal Headroom API (Android 11+ / API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val headroom = powerManager.getThermalHeadroom(30)
                        if (!headroom.isNaN() && headroom.isFinite() && headroom >= 0f) {
                            thermalHeadroom = String.format(Locale.US, "%.2f", headroom).toFloat()
                            isThermalHeadroomSupported = true
                        }
                    } catch (_: Exception) {
                        thermalHeadroom = -1f
                        isThermalHeadroomSupported = false
                    }
                }
            } catch (_: Exception) {
                isThermalStatusSupported = false
                thermalStatusDesc = "Error reading PowerManager thermal status"
            }
        }

        // Display Refresh Rate & Resolution via DisplayManager (works safely with Application context)
        var refreshRate = 60f
        var resolution = "Unavailable"
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                refreshRate = display.mode?.refreshRate ?: display.refreshRate
                val mode = display.mode
                if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                    resolution = "${mode.physicalWidth}x${mode.physicalHeight}"
                }
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                refreshRate = wm?.defaultDisplay?.refreshRate ?: 60f
            }
        } catch (_: Exception) {}

        if (resolution == "Unavailable") {
            try {
                val m = context.resources.displayMetrics
                if (m.widthPixels > 0 && m.heightPixels > 0) {
                    resolution = "${m.widthPixels}x${m.heightPixels}"
                }
            } catch (_: Exception) {}
        }

        return DeviceStats(
            totalMemoryGb = String.format(Locale.US, "%.1f", totalMemGb).toFloat(),
            availableMemoryGb = String.format(Locale.US, "%.1f", availMemGb).toFloat(),
            memoryUsagePercent = memUsagePercent,
            isLowMemory = isLowMem,
            memoryThresholdGb = String.format(Locale.US, "%.1f", thresholdMemGb).toFloat(),
            batteryLevelPercent = batteryPct,
            isCharging = isCharging,
            batteryTemperatureC = if (batteryTemp > 0f) batteryTemp else 0f,
            batteryHealth = batteryHealth,
            powerSource = powerSource,
            isPowerSaveMode = isPowerSaveMode,
            totalStorageGb = String.format(Locale.US, "%.1f", totalStorageGb).toFloat(),
            availableStorageGb = String.format(Locale.US, "%.1f", availStorageGb).toFloat(),
            storageUsagePercent = storageUsagePercent,
            cpuModel = Build.HARDWARE.ifEmpty { "ARMv8-A" },
            cpuBoard = Build.BOARD.ifEmpty { "Generic Board" },
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            powerManagerThermalStatus = thermalStatusDesc,
            powerManagerThermalStatusCode = thermalStatusCode,
            isThermalStatusSupported = isThermalStatusSupported,
            thermalHeadroom = thermalHeadroom,
            isThermalHeadroomSupported = isThermalHeadroomSupported,
            displayRefreshRate = String.format(Locale.US, "%.0f", refreshRate).toFloat(),
            screenResolution = resolution
        )
    }
}
