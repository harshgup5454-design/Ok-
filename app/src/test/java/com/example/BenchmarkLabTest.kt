package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BenchmarkLabTest {

    @Test
    fun testCpuBenchmarkExecutionAndPersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = GameSpaceRepository(context)

        val result = BenchmarkLabEngine.runCpuBenchmark(
            context = context,
            onProgress = {},
            onTelemetryUpdate = { _, _ -> }
        )

        assertNotNull(result)
        assertEquals(WorkloadType.CPU_MATH.displayName, result.workloadType)

        repository.saveBenchmarkLabResult(result)
        val results = repository.benchmarkLabResultsFlow.first()
        assertTrue(results.isNotEmpty())
        assertEquals(result.id, results[0].id)
    }

    @Test
    fun testMemoryBenchmarkExecution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = BenchmarkLabEngine.runMemoryBenchmark(
            context = context,
            onProgress = {},
            onTelemetryUpdate = { _, _ -> }
        )

        assertNotNull(result)
        assertEquals(WorkloadType.MEMORY_BANDWIDTH.displayName, result.workloadType)
        assertTrue(result.scoreOrOpsPerSec >= 0.0)
    }

    @Test
    fun testGraphicsCanvasBenchmarkExecution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = BenchmarkLabEngine.runGraphicsCanvasBenchmark(
            context = context,
            onProgress = {},
            onTelemetryUpdate = { _, _ -> }
        )

        assertNotNull(result)
        assertEquals(WorkloadType.GRAPHICS_CANVAS.displayName, result.workloadType)
        assertTrue(result.scoreOrOpsPerSec > 0.0)
    }
}
