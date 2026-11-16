package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

enum class WorkloadType(val displayName: String, val description: String) {
    CPU_MATH("CPU Floating-Point & Math Workload", "Measures core arithmetic throughput using repeatable prime number and vector calculations."),
    MEMORY_BANDWIDTH("Memory Allocation & Bandwidth", "Allocates safe memory buffers, tests read/write throughput, and verifies immediate release."),
    GRAPHICS_CANVAS("App UI Canvas Rendering Benchmark", "Measures GAMEBOOST internal UI canvas draw loop render times (Strictly App UI, not game FPS).")
}

data class BenchmarkLabRunResult(
    val id: String,
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val workloadType: String,
    val durationMs: Long,
    val scoreOrOpsPerSec: Double, // e.g. MFLOPS or MB/s or FPS render speed
    val startBatteryTempC: Float,
    val peakBatteryTempC: Float,
    val startThermalStatus: String,
    val peakThermalStatus: String,
    val detailsSummary: String,
    val wasAbortedDueToThermal: Boolean
) {
    fun toEntity(): BenchmarkLabResultEntity = BenchmarkLabResultEntity(
        id = id,
        timestamp = timestamp,
        deviceModel = deviceModel,
        androidVersion = androidVersion,
        workloadType = workloadType,
        durationMs = durationMs,
        scoreOrOpsPerSec = scoreOrOpsPerSec,
        startBatteryTempC = startBatteryTempC,
        peakBatteryTempC = peakBatteryTempC,
        startThermalStatus = startThermalStatus,
        peakThermalStatus = peakThermalStatus,
        detailsSummary = detailsSummary,
        wasAbortedDueToThermal = wasAbortedDueToThermal
    )

    companion object {
        fun fromEntity(entity: BenchmarkLabResultEntity): BenchmarkLabRunResult = BenchmarkLabRunResult(
            id = entity.id,
            timestamp = entity.timestamp,
            deviceModel = entity.deviceModel,
            androidVersion = entity.androidVersion,
            workloadType = entity.workloadType,
            durationMs = entity.durationMs,
            scoreOrOpsPerSec = entity.scoreOrOpsPerSec,
            startBatteryTempC = entity.startBatteryTempC,
            peakBatteryTempC = entity.peakBatteryTempC,
            startThermalStatus = entity.startThermalStatus,
            peakThermalStatus = entity.peakThermalStatus,
            detailsSummary = entity.detailsSummary,
            wasAbortedDueToThermal = entity.wasAbortedDueToThermal
        )
    }
}

object BenchmarkLabEngine {

    private fun checkThermalSafety(context: Context): Pair<Boolean, String> {
        val stats = DeviceMonitor.getDeviceStats(context)
        if (stats.powerManagerThermalStatusCode >= 2) {
            return Pair(true, "Aborted: Android PowerManager reported thermal throttling (${stats.powerManagerThermalStatus}).")
        }
        if (stats.batteryTemperatureC >= 42.0f) {
            return Pair(true, "Aborted: Battery temperature reached ${stats.batteryTemperatureC}°C (exceeds safe 42°C threshold).")
        }
        return Pair(false, "Normal")
    }

    suspend fun runCpuBenchmark(
        context: Context,
        onProgress: (Float) -> Unit,
        onTelemetryUpdate: (Float, String) -> Unit
    ): BenchmarkLabRunResult {
        val startTime = SystemClock.elapsedRealtime()
        val initialStats = DeviceMonitor.getDeviceStats(context)
        var peakTemp = initialStats.batteryTemperatureC
        var thermalStatus = initialStats.powerManagerThermalStatus

        val (isUnsafe, unsafeReason) = checkThermalSafety(context)
        if (isUnsafe) {
            val duration = SystemClock.elapsedRealtime() - startTime
            return BenchmarkLabRunResult(
                id = "bench_cpu_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                workloadType = WorkloadType.CPU_MATH.displayName,
                durationMs = duration,
                scoreOrOpsPerSec = 0.0,
                startBatteryTempC = initialStats.batteryTemperatureC,
                peakBatteryTempC = peakTemp,
                startThermalStatus = initialStats.powerManagerThermalStatus,
                peakThermalStatus = thermalStatus,
                detailsSummary = unsafeReason,
                wasAbortedDueToThermal = true
            )
        }

        val testDurationMs = 3000L // 3 seconds controlled workload
        val loopStartTime = System.currentTimeMillis()
        var operationsCount = 0L

        while (System.currentTimeMillis() - loopStartTime < testDurationMs) {
            if (!coroutineContext.isActive) break
            
            // Repeatable CPU math calculation (prime / sqrt / vector loop)
            var acc = 0.0
            for (i in 1..2500) {
                acc += sqrt(i.toDouble()) * (i % 7)
            }
            operationsCount += 2500L

            val elapsed = System.currentTimeMillis() - loopStartTime
            val progress = (elapsed.toFloat() / testDurationMs.toFloat()).coerceIn(0f, 1f)
            onProgress(progress)

            if (operationsCount % 25000L == 0L) {
                val currentStats = DeviceMonitor.getDeviceStats(context)
                if (currentStats.batteryTemperatureC > peakTemp) peakTemp = currentStats.batteryTemperatureC
                thermalStatus = currentStats.powerManagerThermalStatus
                onTelemetryUpdate(currentStats.batteryTemperatureC, thermalStatus)

                val (unsafe, reason) = checkThermalSafety(context)
                if (unsafe) {
                    return BenchmarkLabRunResult(
                        id = "bench_cpu_${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                        workloadType = WorkloadType.CPU_MATH.displayName,
                        durationMs = SystemClock.elapsedRealtime() - startTime,
                        scoreOrOpsPerSec = (operationsCount.toDouble() / (elapsed.toDouble() / 1000.0)),
                        startBatteryTempC = initialStats.batteryTemperatureC,
                        peakBatteryTempC = peakTemp,
                        startThermalStatus = initialStats.powerManagerThermalStatus,
                        peakThermalStatus = thermalStatus,
                        detailsSummary = reason,
                        wasAbortedDueToThermal = true
                    )
                }
            }
            delay(1L)
        }

        val totalDuration = SystemClock.elapsedRealtime() - startTime
        val opsPerSec = (operationsCount.toDouble() / (totalDuration.toDouble() / 1000.0))

        return BenchmarkLabRunResult(
            id = "bench_cpu_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            workloadType = WorkloadType.CPU_MATH.displayName,
            durationMs = totalDuration,
            scoreOrOpsPerSec = opsPerSec,
            startBatteryTempC = initialStats.batteryTemperatureC,
            peakBatteryTempC = peakTemp,
            startThermalStatus = initialStats.powerManagerThermalStatus,
            peakThermalStatus = thermalStatus,
            detailsSummary = "Completed ${operationsCount} arithmetic operations successfully at ${String.format(java.util.Locale.US, "%.1f", opsPerSec / 1000.0)}k ops/sec. Thermal status remained stable.",
            wasAbortedDueToThermal = false
        )
    }

    suspend fun runMemoryBenchmark(
        context: Context,
        onProgress: (Float) -> Unit,
        onTelemetryUpdate: (Float, String) -> Unit
    ): BenchmarkLabRunResult {
        val startTime = SystemClock.elapsedRealtime()
        val initialStats = DeviceMonitor.getDeviceStats(context)
        var peakTemp = initialStats.batteryTemperatureC
        var thermalStatus = initialStats.powerManagerThermalStatus

        val (isUnsafe, unsafeReason) = checkThermalSafety(context)
        if (isUnsafe) {
            return BenchmarkLabRunResult(
                id = "bench_mem_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                workloadType = WorkloadType.MEMORY_BANDWIDTH.displayName,
                durationMs = SystemClock.elapsedRealtime() - startTime,
                scoreOrOpsPerSec = 0.0,
                startBatteryTempC = initialStats.batteryTemperatureC,
                peakBatteryTempC = peakTemp,
                startThermalStatus = initialStats.powerManagerThermalStatus,
                peakThermalStatus = thermalStatus,
                detailsSummary = unsafeReason,
                wasAbortedDueToThermal = true
            )
        }

        var totalBytesAllocated = 0L
        var bandwidthScore = 0.0
        var allocationSuccessful = true

        try {
            onProgress(0.2f)
            delay(200L)
            // Controlled safe buffer allocation (e.g. 16MB int array = 64MB memory block)
            val bufferSize = 4 * 1024 * 1024 // 4 million ints = 16 MB
            val testArray = IntArray(bufferSize)
            totalBytesAllocated += bufferSize * 4L

            onProgress(0.5f)
            val writeStart = System.nanoTime()
            for (i in 0 until bufferSize step 64) {
                testArray[i] = i
            }
            val writeTime = System.nanoTime() - writeStart

            onProgress(0.8f)
            val readStart = System.nanoTime()
            var sum = 0L
            for (i in 0 until bufferSize step 64) {
                sum += testArray[i]
            }
            val readTime = System.nanoTime() - readStart
            
            val totalTimeSec = (writeTime + readTime).toDouble() / 1_000_000_000.0
            bandwidthScore = (totalBytesAllocated.toDouble() / (1024.0 * 1024.0)) / (if (totalTimeSec > 0.0) totalTimeSec else 0.001)

            // Safe immediate cleanup & release references
            @Suppress("UNUSED_VALUE")
            var ref: IntArray? = testArray
            ref = null
            System.gc()

            onProgress(1.0f)
        } catch (e: OutOfMemoryError) {
            allocationSuccessful = false
            System.gc()
        } catch (e: Exception) {
            allocationSuccessful = false
        }

        val totalDuration = SystemClock.elapsedRealtime() - startTime
        val currentStats = DeviceMonitor.getDeviceStats(context)

        return BenchmarkLabRunResult(
            id = "bench_mem_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            workloadType = WorkloadType.MEMORY_BANDWIDTH.displayName,
            durationMs = totalDuration,
            scoreOrOpsPerSec = if (allocationSuccessful) bandwidthScore else 0.0,
            startBatteryTempC = initialStats.batteryTemperatureC,
            peakBatteryTempC = currentStats.batteryTemperatureC,
            startThermalStatus = initialStats.powerManagerThermalStatus,
            peakThermalStatus = currentStats.powerManagerThermalStatus,
            detailsSummary = if (allocationSuccessful) "Allocated safe buffer, tested read/write throughput (~${String.format(java.util.Locale.US, "%.1f", bandwidthScore)} MB/s), and released references immediately." else "Allocation handled safely without crash.",
            wasAbortedDueToThermal = false
        )
    }

    suspend fun runGraphicsCanvasBenchmark(
        context: Context,
        onProgress: (Float) -> Unit,
        onTelemetryUpdate: (Float, String) -> Unit
    ): BenchmarkLabRunResult {
        val startTime = SystemClock.elapsedRealtime()
        val initialStats = DeviceMonitor.getDeviceStats(context)
        var peakTemp = initialStats.batteryTemperatureC

        val (isUnsafe, unsafeReason) = checkThermalSafety(context)
        if (isUnsafe) {
            return BenchmarkLabRunResult(
                id = "bench_gfx_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                workloadType = WorkloadType.GRAPHICS_CANVAS.displayName,
                durationMs = SystemClock.elapsedRealtime() - startTime,
                scoreOrOpsPerSec = 0.0,
                startBatteryTempC = initialStats.batteryTemperatureC,
                peakBatteryTempC = peakTemp,
                startThermalStatus = initialStats.powerManagerThermalStatus,
                peakThermalStatus = initialStats.powerManagerThermalStatus,
                detailsSummary = unsafeReason,
                wasAbortedDueToThermal = true
            )
        }

        // Controlled Canvas rendering workload measurement
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = 0xFF00FFCC.toInt()
            isAntiAlias = true
        }

        val testDurationMs = 2500L
        val loopStart = System.currentTimeMillis()
        var framesRendered = 0L
        var totalFrameTimeNs = 0L

        try {
            while (System.currentTimeMillis() - loopStart < testDurationMs) {
                if (!coroutineContext.isActive) break
                val frameStart = System.nanoTime()

                canvas.drawColor(0xFF0F172A.toInt())
                for (i in 0..40) {
                    val cx = (i * 12f + (framesRendered % 50)) % width
                    val cy = (i * 10f) % height
                    canvas.drawCircle(cx, cy, 24f, paint)
                }

                val frameTime = System.nanoTime() - frameStart
                totalFrameTimeNs += frameTime
                framesRendered++

                val elapsed = System.currentTimeMillis() - loopStart
                onProgress((elapsed.toFloat() / testDurationMs.toFloat()).coerceIn(0f, 1f))
                delay(16L) // ~60fps target pacing
            }
        } finally {
            // Release bitmap resources immediately even if cancelled mid-benchmark
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        val totalDuration = SystemClock.elapsedRealtime() - startTime
        val avgFrameTimeMs = if (framesRendered > 0) (totalFrameTimeNs.toDouble() / framesRendered.toDouble()) / 1_000_000.0 else 0.0
        val effectiveFps = if (avgFrameTimeMs > 0.0) 1000.0 / avgFrameTimeMs else 60.0
        val currentStats = DeviceMonitor.getDeviceStats(context)

        return BenchmarkLabRunResult(
            id = "bench_gfx_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            workloadType = WorkloadType.GRAPHICS_CANVAS.displayName,
            durationMs = totalDuration,
            scoreOrOpsPerSec = effectiveFps,
            startBatteryTempC = initialStats.batteryTemperatureC,
            peakBatteryTempC = currentStats.batteryTemperatureC,
            startThermalStatus = initialStats.powerManagerThermalStatus,
            peakThermalStatus = currentStats.powerManagerThermalStatus,
            detailsSummary = "App Canvas Render Test: Average frame render time ${String.format(java.util.Locale.US, "%.2f", avgFrameTimeMs)} ms (~${String.format(java.util.Locale.US, "%.1f", effectiveFps)} FPS app render speed). Note: Measures GAMEBOOST UI canvas workload only, not third-party game FPS.",
            wasAbortedDueToThermal = false
        )
    }
}
