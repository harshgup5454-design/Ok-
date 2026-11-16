package com.example.data

data class TelemetrySnapshot(
    val elapsedSeconds: Int,
    val batteryTempC: Float,
    val thermalStatus: String,
    val availRamGb: Float,
    val pingMs: Int,
    val jitterMs: Int,
    val refreshRateHz: Float
)

data class BenchmarkSessionReport(
    val id: String,
    val sessionLabel: String,
    val gameTitle: String,
    val packageName: String = "",
    val graphicsSettings: String = "Not Specified",
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long = System.currentTimeMillis(),
    val autopilotMode: String = "Manual",
    val durationSeconds: Int,
    val startTempC: Float,
    val peakTempC: Float,
    val startBatteryLevel: Int = 100,
    val endBatteryLevel: Int = 100,
    val startThermalStatus: String = "Normal",
    val peakThermalStatus: String = "Normal",
    val avgAvailRamGb: Float,
    val minAvailRamGb: Float,
    val avgPingMs: Int,
    val avgJitterMs: Int,
    val displayRefreshRateHz: Float,
    val fpsMeasurementSource: String = "None (FPS Unavailable)",
    val measuredAvgFps: Float? = null, // null = FPS Unavailable
    val measuredMinFps: Float? = null, // null = Unavailable
    val measuredFrameTimeMs: Float? = null, // null = Unavailable
    val throttlingObserved: Boolean = false,
    val testNotes: String = "",
    val conclusion: String,
    val snapshots: List<TelemetrySnapshot> = emptyList()
) {
    val estimatedBatteryDeltaPercent: Int
        get() = startBatteryLevel - endBatteryLevel

    companion object {
        fun serializeSnapshots(snapshots: List<TelemetrySnapshot>): String {
            if (snapshots.isEmpty()) return ""
            return snapshots.take(60).joinToString(";") { s ->
                "${s.elapsedSeconds}|${s.batteryTempC}|${s.thermalStatus.replace("|", " ").replace(";", " ")}|${s.availRamGb}|${s.pingMs}|${s.jitterMs}|${s.refreshRateHz}"
            }
        }

        fun deserializeSnapshots(raw: String): List<TelemetrySnapshot> {
            if (raw.isBlank()) return emptyList()
            return raw.split(";").mapNotNull { item ->
                val parts = item.split("|")
                if (parts.size < 7) null
                else try {
                    TelemetrySnapshot(
                        elapsedSeconds = parts[0].toInt(),
                        batteryTempC = parts[1].toFloat(),
                        thermalStatus = parts[2],
                        availRamGb = parts[3].toFloat(),
                        pingMs = parts[4].toInt(),
                        jitterMs = parts[5].toInt(),
                        refreshRateHz = parts[6].toFloat()
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

data class SessionAggregateSummary(
    val dailySessionCount: Int,
    val dailyTotalDurationSec: Int,
    val weeklySessionCount: Int,
    val weeklyTotalDurationSec: Int,
    val averageDurationSec: Int,
    val averageEstimatedDrainPercent: Float?,
    val averagePeakTempC: Float?
)

object SessionAnalyticsAggregator {
    fun summarize(
        sessions: List<BenchmarkSessionReport>,
        nowMs: Long = System.currentTimeMillis()
    ): SessionAggregateSummary {
        if (sessions.isEmpty()) {
            return SessionAggregateSummary(0, 0, 0, 0, 0, null, null)
        }
        val oneDayMs = 24L * 60L * 60L * 1000L
        val sevenDaysMs = 7L * oneDayMs

        val daily = sessions.filter { nowMs - it.endTimeMs <= oneDayMs }
        val weekly = sessions.filter { nowMs - it.endTimeMs <= sevenDaysMs }

        val avgDuration = sessions.map { it.durationSeconds }.average().toInt()
        val discharging = sessions.filter { it.startBatteryLevel >= it.endBatteryLevel }
        val avgDrain = if (discharging.isNotEmpty()) {
            discharging.map { (it.startBatteryLevel - it.endBatteryLevel).toFloat() }.average().toFloat()
        } else null
        val avgPeakTemp = sessions.filter { it.peakTempC > 0f }
            .takeIf { it.isNotEmpty() }
            ?.map { it.peakTempC }
            ?.average()
            ?.toFloat()

        return SessionAggregateSummary(
            dailySessionCount = daily.size,
            dailyTotalDurationSec = daily.sumOf { it.durationSeconds },
            weeklySessionCount = weekly.size,
            weeklyTotalDurationSec = weekly.sumOf { it.durationSeconds },
            averageDurationSec = avgDuration,
            averageEstimatedDrainPercent = avgDrain,
            averagePeakTempC = avgPeakTemp
        )
    }
}

object BenchmarkRepository {
    private val sessions = mutableListOf<BenchmarkSessionReport>()

    init {
        // Sample validation sessions demonstrating both OEM-observed and sandbox-unavailable scenarios
        sessions.add(
            BenchmarkSessionReport(
                id = "baseline_1",
                sessionLabel = "Validation Run A: High Graphics while Charging",
                gameTitle = "BGMI / PUBG Mobile",
                graphicsSettings = "HD Graphics + High Frame Cap + Shadows ON",
                durationSeconds = 600,
                startTempC = 33.2f,
                peakTempC = 42.4f,
                startBatteryLevel = 65,
                endBatteryLevel = 74,
                startThermalStatus = "Normal (No throttling reported by OS)",
                peakThermalStatus = "Severe (OS reports thermal throttling active)",
                avgAvailRamGb = 1.2f,
                minAvailRamGb = 0.7f,
                avgPingMs = 62,
                avgJitterMs = 26,
                displayRefreshRateHz = 60f,
                fpsMeasurementSource = "OEM Game Space Overlay (Observed)",
                measuredAvgFps = 23.5f,
                measuredMinFps = 17.0f,
                measuredFrameTimeMs = 42.5f,
                throttlingObserved = true,
                testNotes = "Device was connected to fast charger in a 27°C ambient room.",
                conclusion = "Device battery temperature rose by +9.2°C and PowerManager signaled Severe thermal status after 7 minutes. Observed frame rate declined from 30 FPS into the 20–24 FPS range as thermal load accumulated."
            )
        )
        sessions.add(
            BenchmarkSessionReport(
                id = "baseline_2",
                sessionLabel = "Validation Run B: Smooth Graphics on Battery",
                gameTitle = "BGMI / PUBG Mobile",
                graphicsSettings = "Smooth Graphics + Ultra Cap + Shadows OFF",
                durationSeconds = 600,
                startTempC = 32.1f,
                peakTempC = 36.9f,
                startBatteryLevel = 85,
                endBatteryLevel = 80,
                startThermalStatus = "Normal (No throttling reported by OS)",
                peakThermalStatus = "Normal (No throttling reported by OS)",
                avgAvailRamGb = 2.3f,
                minAvailRamGb = 1.9f,
                avgPingMs = 41,
                avgJitterMs = 7,
                displayRefreshRateHz = 60f,
                fpsMeasurementSource = "OEM Game Space Overlay (Observed)",
                measuredAvgFps = 30.6f,
                measuredMinFps = 28.0f,
                measuredFrameTimeMs = 32.6f,
                throttlingObserved = false,
                testNotes = "Tested off-charger under identical room ambient conditions.",
                conclusion = "Lowering shader workload and playing on battery kept thermal rise under +4.8°C. PowerManager reported Normal throughout. Observed frame rate held closer to the 30 FPS target."
            )
        )
        sessions.add(
            BenchmarkSessionReport(
                id = "baseline_3",
                sessionLabel = "Validation Run C: Standard Session (No OEM Tool)",
                gameTitle = "Free Fire MAX",
                graphicsSettings = "Standard Preset",
                durationSeconds = 480,
                startTempC = 31.5f,
                peakTempC = 35.8f,
                startBatteryLevel = 70,
                endBatteryLevel = 66,
                startThermalStatus = "Normal (No throttling reported by OS)",
                peakThermalStatus = "Normal (No throttling reported by OS)",
                avgAvailRamGb = 2.1f,
                minAvailRamGb = 1.8f,
                avgPingMs = 38,
                avgJitterMs = 8,
                displayRefreshRateHz = 60f,
                fpsMeasurementSource = "None (Android Sandbox)",
                measuredAvgFps = null, // Real unrooted state
                measuredMinFps = null,
                measuredFrameTimeMs = null,
                throttlingObserved = false,
                testNotes = "No OEM floating FPS utility was active on this device.",
                conclusion = "FPS Unavailable: Third-party game rendering is sandboxed by Android security policies. Hardware battery temperature and memory remained within safe operational margins."
            )
        )
    }

    fun getAllSessions(): List<BenchmarkSessionReport> = sessions.toList()

    fun addSession(report: BenchmarkSessionReport) {
        sessions.add(0, report)
    }

    fun clearHistory() {
        sessions.clear()
    }
}
