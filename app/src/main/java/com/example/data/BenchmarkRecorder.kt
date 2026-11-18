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

    fun getAllSessions(): List<BenchmarkSessionReport> = sessions.toList()

    fun addSession(report: BenchmarkSessionReport) {
        sessions.add(0, report)
    }

    fun clearHistory() {
        sessions.clear()
    }
}
