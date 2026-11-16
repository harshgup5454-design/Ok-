package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NetworkMonitor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkTest {

    @Test
    fun testGetNetworkStatsOfflineOrOnline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stats = NetworkMonitor.getNetworkStats(context)
        assertNotNull(stats.connectionType)
    }

    @Test
    fun testSpeedTestCancellation() = runBlocking {
        var progressReported = 0f
        val result = NetworkMonitor.runSpeedTest(
            onProgress = { prog -> progressReported = prog },
            isActive = { false } // simulate immediate cancellation
        )

        assertTrue(result.isCancelled)
        assertEquals(0.0f, result.downloadMbps, 0.1f)
    }

    @Test
    fun testAdvancedDiagnosticsExecution() = runBlocking {
        val burst = NetworkMonitor.measureAdvancedDiagnostics()
        assertNotNull(burst.rating)
        assertTrue(burst.successRate >= 0f)
    }
}
