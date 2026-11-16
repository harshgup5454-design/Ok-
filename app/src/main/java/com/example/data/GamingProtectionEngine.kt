package com.example.data

enum class ProtectionSeverity {
    SAFE, ADVISORY, WARNING, CRITICAL
}

data class ProtectionAlert(
    val id: String,
    val title: String,
    val message: String,
    val recommendation: String,
    val severity: ProtectionSeverity
)

data class GamingProtectionStatus(
    val overallSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    val headline: String = "Device Conditions Optimal",
    val alerts: List<ProtectionAlert> = emptyList(),
    val batteryHealthLabel: String = "Good",
    val isThermalThrottling: Boolean = false
)

object GamingProtectionEngine {

    fun evaluate(
        stats: DeviceStats,
        sessionSeconds: Int = 0,
        longSessionThresholdMinutes: Int = 45
    ): GamingProtectionStatus {
        val alerts = mutableListOf<ProtectionAlert>()

        // 1. OS PowerManager Thermal Status
        val isThrottling = stats.isThermalStatusSupported && stats.powerManagerThermalStatusCode >= 2
        if (stats.isThermalStatusSupported) {
            when {
                stats.powerManagerThermalStatusCode >= 3 -> {
                    alerts.add(
                        ProtectionAlert(
                            id = "os_thermal_critical",
                            title = "Severe OS Thermal Throttling",
                            message = "Android PowerManager reports ${stats.powerManagerThermalStatus}. Hardware thermal safeguards are actively reducing clock speeds.",
                            recommendation = "Pause intensive gaming for 5–10 minutes and let the device cool down. Never bypass thermal limits.",
                            severity = ProtectionSeverity.CRITICAL
                        )
                    )
                }
                stats.powerManagerThermalStatusCode == 2 -> {
                    alerts.add(
                        ProtectionAlert(
                            id = "os_thermal_moderate",
                            title = "Moderate Thermal Load",
                            message = "OS reports moderate thermal pressure. Extended sessions may trigger frame rate drops.",
                            recommendation = "Lower in-game shadow/shader settings or take a short cooldown break.",
                            severity = ProtectionSeverity.WARNING
                        )
                    )
                }
            }
        }

        // 2. Battery Temperature Monitoring
        when {
            stats.batteryTemperatureC >= 42.0f -> {
                alerts.add(
                    ProtectionAlert(
                        id = "battery_temp_critical",
                        title = "High Battery Temperature (${stats.batteryTemperatureC}°C)",
                        message = "Battery sensor exceeds 42°C. Sustained heat accelerates battery wear and triggers DVFS throttling.",
                        recommendation = "Remove protective phone case, unplug charger immediately, and allow device to cool.",
                        severity = ProtectionSeverity.CRITICAL
                    )
                )
            }
            stats.batteryTemperatureC >= 39.5f -> {
                alerts.add(
                    ProtectionAlert(
                        id = "battery_temp_warn",
                        title = "Elevated Battery Heat (${stats.batteryTemperatureC}°C)",
                        message = "Device temperature is rising toward thermal throttling thresholds.",
                        recommendation = "Improve airflow, dim screen slightly, or switch Autopilot to Balanced/Battery Friendly.",
                        severity = ProtectionSeverity.WARNING
                    )
                )
            }
        }

        // 3. Charging while gaming
        if (stats.isCharging && stats.batteryTemperatureC >= 37.0f) {
            alerts.add(
                ProtectionAlert(
                    id = "charging_heat",
                    title = "Charging While Gaming",
                    message = "Fast charging adds battery chemical heat on top of SoC heat (${stats.batteryTemperatureC}°C via ${stats.powerSource}).",
                    recommendation = "Unplug charger during matches when battery charge is sufficient.",
                    severity = ProtectionSeverity.WARNING
                )
            )
        }

        // 4. Low Battery & Battery Saver Alerts
        if (stats.batteryLevelPercent <= 15 && !stats.isCharging) {
            alerts.add(
                ProtectionAlert(
                    id = "battery_critical",
                    title = "Critically Low Battery (${stats.batteryLevelPercent}%)",
                    message = "Low voltage can cause OEM clock throttling or sudden shutdown.",
                    recommendation = "Save match progress and charge before starting a new game.",
                    severity = ProtectionSeverity.CRITICAL
                )
            )
        } else if (stats.batteryLevelPercent <= 20 && !stats.isCharging) {
            alerts.add(
                ProtectionAlert(
                    id = "battery_low",
                    title = "Low Battery Warning (${stats.batteryLevelPercent}%)",
                    message = "Android/OEM power managers often throttle background & GPU budgets below 20%.",
                    recommendation = "Switch to Battery Friendly Autopilot mode or lower in-game graphics.",
                    severity = ProtectionSeverity.WARNING
                )
            )
        }

        if (stats.isPowerSaveMode) {
            alerts.add(
                ProtectionAlert(
                    id = "battery_saver_on",
                    title = "System Battery Saver Active",
                    message = "Android Battery Saver limits background sync and may cap display refresh rate or CPU frequency.",
                    recommendation = "Disable System Battery Saver in Android Settings only if plugged in or above 30% charge.",
                    severity = ProtectionSeverity.ADVISORY
                )
            )
        }

        // 5. Battery Health Alert
        if (stats.batteryHealth == "Overheat" || stats.batteryHealth == "Dead" || stats.batteryHealth == "Over Voltage") {
            alerts.add(
                ProtectionAlert(
                    id = "battery_health_alert",
                    title = "Battery Health Warning: ${stats.batteryHealth}",
                    message = "Android BatteryManager reported health status '${stats.batteryHealth}'.",
                    recommendation = "Stop gaming immediately and inspect charger/device temperature.",
                    severity = ProtectionSeverity.CRITICAL
                )
            )
        }

        // 6. Long Session Reminder
        val thresholdSeconds = longSessionThresholdMinutes.coerceAtLeast(15) * 60
        if (sessionSeconds >= thresholdSeconds) {
            val mins = sessionSeconds / 60
            alerts.add(
                ProtectionAlert(
                    id = "long_session",
                    title = "Long Gaming Session (${mins}m)",
                    message = "Continuous gameplay over $longSessionThresholdMinutes minutes increases heat soak and eye strain.",
                    recommendation = "Take a 5-minute break to rest your eyes and let device temperature stabilize.",
                    severity = ProtectionSeverity.ADVISORY
                )
            )
        }

        val overall = when {
            alerts.any { it.severity == ProtectionSeverity.CRITICAL } -> ProtectionSeverity.CRITICAL
            alerts.any { it.severity == ProtectionSeverity.WARNING } -> ProtectionSeverity.WARNING
            alerts.any { it.severity == ProtectionSeverity.ADVISORY } -> ProtectionSeverity.ADVISORY
            else -> ProtectionSeverity.SAFE
        }

        val headline = when (overall) {
            ProtectionSeverity.CRITICAL -> "Thermal / Battery Action Needed"
            ProtectionSeverity.WARNING -> "Protection Warning Active"
            ProtectionSeverity.ADVISORY -> "Power Advisory Active"
            ProtectionSeverity.SAFE -> "Thermal & Power Safe"
        }

        return GamingProtectionStatus(
            overallSeverity = overall,
            headline = headline,
            alerts = alerts,
            batteryHealthLabel = stats.batteryHealth,
            isThermalThrottling = isThrottling
        )
    }
}
