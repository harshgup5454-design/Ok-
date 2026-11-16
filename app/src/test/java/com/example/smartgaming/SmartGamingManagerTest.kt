package com.example.smartgaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.ProfileType
import com.example.service.GamingOverlayService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartGamingManagerTest {

    @Test
    fun testAutopilotMasterSwitchAndProfileState() {
        SmartGamingManager.setAutopilotMasterEnabled(true)
        assertTrue(SmartGamingManager.autopilotState.value.isAutopilotEnabled)

        SmartGamingManager.setTargetProfile(ProfileType.SMOOTHNESS_FOCUS)
        assertEquals(ProfileType.SMOOTHNESS_FOCUS, SmartGamingManager.autopilotState.value.activeProfile)

        SmartGamingManager.setTargetGame("Free Fire MAX", "com.dts.freefiremax")
        assertEquals("Free Fire MAX", SmartGamingManager.autopilotState.value.targetGameTitle)
        assertEquals("com.dts.freefiremax", SmartGamingManager.autopilotState.value.targetGamePackage)

        SmartGamingManager.setAutopilotMasterEnabled(false)
        assertFalse(SmartGamingManager.autopilotState.value.isAutopilotEnabled)
    }

    @Test
    fun testBackgroundAppScannerSafeOnSandbox() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SmartGamingManager.refreshBackgroundProcesses(context)
        assertNotNull(SmartGamingManager.backgroundApps.value)
    }

    @Test
    fun testOverlayDiagnosticLogging() {
        GamingOverlayService.logEvent("Test overlay initialization check")
        assertTrue(GamingOverlayService.diagnosticLogs.value.any { it.contains("Test overlay initialization check") })
    }

    @Test
    fun testMainActivityStartupNoCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun testSessionStartStopReentrancyGuards() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.GameBoostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            assertFalse(SmartGamingManager.autopilotState.value.isSessionActive)
            SmartGamingManager.startSmartGamingSession(context, ProfileType.BALANCED, db) {}
            assertTrue(SmartGamingManager.autopilotState.value.isSessionActive)

            SmartGamingManager.stopSmartGamingSession(context, db)
            assertFalse(SmartGamingManager.autopilotState.value.isSessionActive)

            // Repeated stop when already inactive should be safe (no-op)
            SmartGamingManager.stopSmartGamingSession(context, db)
            assertFalse(SmartGamingManager.autopilotState.value.isSessionActive)
        } finally {
            db.close()
        }
    }

    @Test
    fun testForegroundDetectionLoadsSavedGameProfileAndStopsOnExit() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.GameBoostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val profiles = listOf(
                com.example.data.GameProfile(
                    id = "ff",
                    name = "Free Fire MAX",
                    packageName = "com.dts.freefiremax",
                    performanceMode = com.example.data.PerformanceMode.PERFORMANCE_ASSISTANT,
                    isInstalled = true,
                    isInLibrary = true
                ),
                com.example.data.GameProfile(
                    id = "subway",
                    name = "Subway Surfers",
                    packageName = "com.kiloo.subwaysurf",
                    performanceMode = com.example.data.PerformanceMode.BATTERY_SAVER,
                    isInstalled = true,
                    isInLibrary = true
                )
            )
            SmartGamingManager.setAutopilotMasterEnabled(true)

            // Launching Free Fire MAX loads its saved Smoothness Focus / Maximum Stability profile
            SmartGamingManager.evaluateForegroundPackage(context, "com.dts.freefiremax", profiles, db)
            val active = SmartGamingManager.autopilotState.value
            assertTrue(active.isSessionActive)
            assertTrue(active.wasAutoDetected)
            assertEquals("com.dts.freefiremax", active.targetGamePackage)
            assertEquals(ProfileType.SMOOTHNESS_FOCUS, active.activeProfile)

            // Returning briefly to GAMEBOOST itself must not stop the detected session
            SmartGamingManager.evaluateForegroundPackage(context, context.packageName, profiles, db)
            assertTrue(SmartGamingManager.autopilotState.value.isSessionActive)

            // Switching to a non-game app ends and restores the auto-detected session
            SmartGamingManager.evaluateForegroundPackage(context, "com.android.launcher", profiles, db)
            val ended = SmartGamingManager.autopilotState.value
            assertFalse(ended.isSessionActive)
            assertFalse(ended.wasAutoDetected)
        } finally {
            SmartGamingManager.stopSmartGamingSession(context, db)
            SmartGamingManager.setAutopilotMasterEnabled(false)
            db.close()
        }
    }

    @Test
    fun testManualSessionNotStoppedByForegroundSwitchAndUsageDenialSafe() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.GameBoostDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            com.example.data.OverlayPreferences.update(context) { it.copy(autoDetectForegroundGame = true) }
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            org.robolectric.Shadows.shadowOf(appOps).setMode(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
                android.app.AppOpsManager.MODE_IGNORED
            )
            SmartGamingManager.setAutopilotMasterEnabled(true)
            val hasUsage = SmartGamingManager.refreshUsageAccessStatus(context)
            assertFalse(hasUsage)
            assertFalse(SmartGamingManager.autopilotState.value.usageAccessGranted)

            // Evaluating foreground detection without Usage Access permission handles denial safely
            SmartGamingManager.evaluateForegroundDetection(context, emptyList(), db)
            assertFalse(SmartGamingManager.autopilotState.value.isSessionActive)

            // Manual session should NOT be auto-stopped by switching to a non-game package
            SmartGamingManager.startSmartGamingSession(context, ProfileType.BATTERY_FRIENDLY, db, wasAutoDetected = false) {}
            assertTrue(SmartGamingManager.autopilotState.value.isSessionActive)
            assertFalse(SmartGamingManager.autopilotState.value.wasAutoDetected)

            SmartGamingManager.evaluateForegroundPackage(context, "com.android.settings", emptyList(), db)
            assertTrue(SmartGamingManager.autopilotState.value.isSessionActive)

            // Honest skipped settings notice & network N/A handling
            val skipped = SmartGamingManager.autopilotState.value.skippedSettings
            assertTrue(skipped.any { it.contains("In-game graphics", ignoreCase = true) })
            SmartGamingManager.updateNetworkTelemetry(com.example.data.NetworkStats(pingMs = -1, connectionType = "Offline"))
            assertEquals(-1, SmartGamingManager.autopilotState.value.networkLatencyMs)
        } finally {
            SmartGamingManager.stopSmartGamingSession(context, db)
            SmartGamingManager.setAutopilotMasterEnabled(false)
            db.close()
        }
    }
}


