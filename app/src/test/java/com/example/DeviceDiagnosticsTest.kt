package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceDiagnosticsTest {

    @Test
    fun testFormatBytesUtility() {
        assertEquals("500 B", DeviceDiagnosticsEngine.formatBytes(500L))
        assertEquals("1.5 KB", DeviceDiagnosticsEngine.formatBytes(1536L))
        assertEquals("2.00 MB", DeviceDiagnosticsEngine.formatBytes(2 * 1024 * 1024L))
        assertEquals("3.50 GB", DeviceDiagnosticsEngine.formatBytes((3.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testStorageAnalyzerLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = DeviceDiagnosticsEngine.analyzeStorage(context)

        assertNotNull(storage)
        assertTrue(storage.totalInternalGb >= 0f)
        assertTrue(storage.availableInternalGb >= 0f)
        assertTrue(storage.usagePercent in 0..100)
        assertNotNull(storage.totalAppFootprintFormatted)
        assertTrue(storage.cleanupGuidance.contains("sandbox"))
    }

    @Test
    fun testDisplayAnalyzerLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val display = DeviceDiagnosticsEngine.analyzeDisplay(context)

        assertNotNull(display)
        assertTrue(display.densityDpi > 0)
        assertTrue(display.densityScale > 0f)
        assertTrue(display.currentRefreshRateHz >= 30f)
        assertTrue(display.supportedModes.isNotEmpty())
        assertTrue(display.educationalNote.contains("REFRESH RATE ≠ IN-GAME FPS"))
    }

    @Test
    fun testBatteryDiagnosticsLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val battery = DeviceDiagnosticsEngine.analyzeBattery(context)

        assertNotNull(battery)
        assertTrue(battery.levelPercent in 0..100)
        assertNotNull(battery.status)
        assertNotNull(battery.healthCategory)
        assertTrue(battery.healthDisclaimer.contains("standard Android APIs"))
    }

    @Test
    fun testSystemCompatibilityLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val system = DeviceDiagnosticsEngine.analyzeSystemCompatibility(context)

        assertNotNull(system)
        assertNotNull(system.androidVersion)
        assertTrue(system.sdkInt > 0)
        assertTrue(system.permissions.isNotEmpty())
        assertTrue(system.memoryClassMb > 0)
        assertTrue(system.sandboxExplanations.contains("SELinux"))
    }

    @Test
    fun testDiagnosticReportGeneration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = DeviceDiagnosticsEngine.analyzeStorage(context)
        val display = DeviceDiagnosticsEngine.analyzeDisplay(context)
        val battery = DeviceDiagnosticsEngine.analyzeBattery(context)
        val system = DeviceDiagnosticsEngine.analyzeSystemCompatibility(context)
        val stats = DeviceMonitor.getDeviceStats(context)

        val report = DeviceDiagnosticsEngine.generateDiagnosticReport(
            context = context,
            storage = storage,
            display = display,
            battery = battery,
            system = system,
            deviceStats = stats
        )

        assertNotNull(report)
        assertTrue(report.exportableText.contains("GAMEBOOST DEVICE DIAGNOSTIC REPORT"))
        assertTrue(report.exportableText.contains("STORAGE DIAGNOSTICS"))
        assertTrue(report.exportableText.contains("DISPLAY DIAGNOSTICS"))
        assertTrue(report.exportableText.contains("BATTERY DIAGNOSTICS"))
        assertTrue(report.recommendedActions.isNotEmpty())
    }

    @Test
    fun testTouchscreenEdgeResponseLogic() {
        var edgeState = EdgeResponseState()
        assertFalse(edgeState.allEdgesTested)

        edgeState = edgeState.copy(leftEdgeHit = true)
        assertFalse(edgeState.allEdgesTested)

        edgeState = edgeState.copy(rightEdgeHit = true, topEdgeHit = true, bottomEdgeHit = true)
        assertTrue(edgeState.allEdgesTested)

        val swipe = SwipeMetricResult(
            distancePx = 450f,
            durationMs = 200L,
            velocityPxPerSec = 2250f,
            wasInterrupted = false
        )
        assertEquals(450f, swipe.distancePx, 0.01f)
        assertEquals(200L, swipe.durationMs)
        assertEquals(2250f, swipe.velocityPxPerSec, 0.01f)
        assertFalse(swipe.wasInterrupted)
    }

    @Test
    fun testSafeAppCacheCleaning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = java.io.File(context.cacheDir, "diagnostic_test_temp.tmp")
        testFile.writeText("Temporary test buffer")

        assertTrue(testFile.exists())
        val freed = DeviceDiagnosticsEngine.clearAppCache(context)
        assertTrue(freed >= 0L)
        assertFalse(testFile.exists())
    }

    @Test
    fun testUnavailableBatteryTemperatureReportsNAWithoutFakeFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = DeviceDiagnosticsEngine.analyzeStorage(context)
        val display = DeviceDiagnosticsEngine.analyzeDisplay(context)
        val battery = BatteryDiagnosticData(
            levelPercent = 75,
            status = "Discharging",
            isCharging = false,
            powerSource = "Battery",
            temperatureC = 0f,
            voltageVolts = null,
            currentMilliAmperes = null,
            healthCategory = "Good",
            healthDisclaimer = "Standard Android APIs"
        )
        val system = DeviceDiagnosticsEngine.analyzeSystemCompatibility(context)
        val stats = DeviceMonitor.getDeviceStats(context).copy(batteryTemperatureC = 0f)

        val report = DeviceDiagnosticsEngine.generateDiagnosticReport(
            context = context,
            storage = storage,
            display = display,
            battery = battery,
            system = system,
            deviceStats = stats
        )

        assertTrue(report.batterySummary.contains("Temperature: N/A"))
        assertFalse(report.batterySummary.contains("30.0"))
        assertFalse(report.recommendedActions.any { it.contains("Battery temperature is elevated") })
    }
}
