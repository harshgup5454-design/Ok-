package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.smartgaming.SmartGamingManager
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.GameBoostTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameBoost3UpgradeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMainActivityStartup() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun testProtectionEngineSafeAndAlertThresholds() {
        val safeStats = DeviceStats(
            batteryTemperatureC = 33.0f,
            batteryLevelPercent = 78,
            isCharging = false,
            powerManagerThermalStatusCode = 0,
            isThermalStatusSupported = true,
            batteryHealth = "Good",
            isPowerSaveMode = false
        )
        val safe = GamingProtectionEngine.evaluate(safeStats, sessionSeconds = 300, longSessionThresholdMinutes = 45)
        assertEquals(ProtectionSeverity.SAFE, safe.overallSeverity)
        assertTrue(safe.alerts.isEmpty())

        val criticalStats = safeStats.copy(
            batteryTemperatureC = 43.0f,
            batteryLevelPercent = 12,
            isCharging = false,
            powerManagerThermalStatusCode = 3
        )
        val critical = GamingProtectionEngine.evaluate(criticalStats, sessionSeconds = 3000, longSessionThresholdMinutes = 45)
        assertEquals(ProtectionSeverity.CRITICAL, critical.overallSeverity)
        assertTrue(critical.isThermalThrottling)
        assertTrue(critical.alerts.any { it.id == "os_thermal_critical" })
        assertTrue(critical.alerts.any { it.id == "battery_temp_critical" })
        assertTrue(critical.alerts.any { it.id == "battery_critical" })
        assertTrue(critical.alerts.any { it.id == "long_session" })
    }

    @Test
    fun testProtectionEngineWarningAndChargingHeat() {
        val warnStats = DeviceStats(
            batteryTemperatureC = 39.8f,
            batteryLevelPercent = 55,
            isCharging = true,
            powerSource = "USB/AC Charger",
            powerManagerThermalStatusCode = 1,
            isThermalStatusSupported = true,
            isPowerSaveMode = true
        )
        val status = GamingProtectionEngine.evaluate(warnStats, sessionSeconds = 600, longSessionThresholdMinutes = 45)
        assertEquals(ProtectionSeverity.WARNING, status.overallSeverity)
        assertTrue(status.alerts.any { it.id == "battery_temp_warn" })
        assertTrue(status.alerts.any { it.id == "charging_heat" })
        assertTrue(status.alerts.any { it.id == "battery_saver_on" })
    }

    @Test
    fun testSessionAnalyticsAggregatorSummary() {
        val now = 1_750_000_000_000L
        val sessions = listOf(
            BenchmarkSessionReport(
                id = "s1",
                sessionLabel = "Recent Run",
                gameTitle = "Free Fire MAX",
                startTimeMs = now - 3600_000L,
                endTimeMs = now - 3000_000L,
                durationSeconds = 600,
                startTempC = 32.0f,
                peakTempC = 38.0f,
                startBatteryLevel = 90,
                endBatteryLevel = 84,
                avgAvailRamGb = 2.4f,
                minAvailRamGb = 2.0f,
                avgPingMs = 30,
                avgJitterMs = 4,
                displayRefreshRateHz = 120f,
                conclusion = "Stable"
            ),
            BenchmarkSessionReport(
                id = "s2",
                sessionLabel = "Week Run",
                gameTitle = "BGMI",
                startTimeMs = now - 3 * 24 * 3600_000L,
                endTimeMs = now - 3 * 24 * 3600_000L + 300_000L,
                durationSeconds = 300,
                startTempC = 33.0f,
                peakTempC = 40.0f,
                startBatteryLevel = 70,
                endBatteryLevel = 66,
                avgAvailRamGb = 2.1f,
                minAvailRamGb = 1.8f,
                avgPingMs = 42,
                avgJitterMs = 8,
                displayRefreshRateHz = 60f,
                conclusion = "Warm"
            )
        )

        val summary = SessionAnalyticsAggregator.summarize(sessions, nowMs = now)
        assertEquals(1, summary.dailySessionCount)
        assertEquals(600, summary.dailyTotalDurationSec)
        assertEquals(2, summary.weeklySessionCount)
        assertEquals(900, summary.weeklyTotalDurationSec)
        assertEquals(450, summary.averageDurationSec)
        assertEquals(5.0f, summary.averageEstimatedDrainPercent ?: 0f, 0.01f)
        assertEquals(39.0f, summary.averagePeakTempC ?: 0f, 0.01f)

        val empty = SessionAnalyticsAggregator.summarize(emptyList(), nowMs = now)
        assertEquals(0, empty.dailySessionCount)
        assertNull(empty.averageEstimatedDrainPercent)
    }

    @Test
    fun testOverlayPreferencesPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OverlayPreferences.update(context) {
            it.copy(
                triggerPosition = "Top",
                triggerWidthDp = 32,
                panelOpacity = 0.78f,
                accentTheme = "Purple",
                defaultCompactMode = true,
                autoDetectForegroundGame = true,
                longSessionReminderMinutes = 60
            )
        }
        val config = OverlayPreferences.config.value
        assertEquals("Top", config.triggerPosition)
        assertEquals(32, config.triggerWidthDp)
        assertEquals(0.78f, config.panelOpacity, 0.01f)
        assertEquals("Purple", config.accentTheme)
        assertTrue(config.defaultCompactMode)
        assertTrue(config.autoDetectForegroundGame)
        assertEquals(60, config.longSessionReminderMinutes)
    }

    @Test
    fun testAutopilotModeCadenceAndUsagePermissionFallback() {
        assertEquals(3000L, SmartGamingManager.getIntervalForProfile(ProfileType.SMOOTHNESS_FOCUS))
        assertEquals(6000L, SmartGamingManager.getIntervalForProfile(ProfileType.BALANCED))
        assertEquals(15000L, SmartGamingManager.getIntervalForProfile(ProfileType.BATTERY_FRIENDLY))

        val context = ApplicationProvider.getApplicationContext<Context>()
        // In Robolectric, UsageStatsManager has no usage events and returns null safely without throwing
        assertNull(GameSpaceManager.getRecentForegroundPackage(context))
    }

    @Test
    fun testSmartAimJsonExportImportRoundTrip() {
        val profile = SmartAimOptimizerEngine.generatePresetProfile(
            game = "Free Fire MAX",
            mode = AimOptimizationMode.ACCURACY,
            dpiTier = 440
        )
        val json = SmartAimOptimizerEngine.exportProfilesJson(listOf(profile))
        val imported = SmartAimOptimizerEngine.importProfilesJson(json)
        assertEquals(1, imported.size)
        assertEquals(profile.profileName, imported[0].profileName)
        assertEquals(profile.gameName, imported[0].gameName)
        assertEquals(profile.general, imported[0].general)
        assertEquals(profile.redDot, imported[0].redDot)
    }

    @Test
    fun testDashboardAdSuppressionAndControlCenterUi() {
        val fakeManager = object : com.example.ads.DefaultAdManager(isProEntitlementActive = { false }) {
            override fun refreshNetworkStatus(context: Context): Boolean = adState.value.isNetworkAvailable
        }.apply {
            updateStateForTesting {
                it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true)
            }
        }
        composeTestRule.setContent {
            GameBoostTheme {
                DashboardScreen(
                    stats = DeviceStats(deviceModel = "TestPhone", displayRefreshRate = 120f),
                    networkStats = NetworkStats(pingMs = 24, jitterMs = 3, connectionType = "Wi-Fi"),
                    bottleneckAnalysis = null,
                    isPremium = false,
                    isBenchmarking = false,
                    protectionStatus = GamingProtectionStatus(overallSeverity = ProtectionSeverity.SAFE),
                    adManager = fakeManager,
                    onRunScanClick = {},
                    onOpenChecklistClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ZeroX BOOST").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_protection_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("dashboard_start_gaming_button"))
        composeTestRule.onNodeWithTag("dashboard_start_gaming_button").assertIsDisplayed()
        // Fake placeholder ad text must never appear
        composeTestRule.onNodeWithText("Non-Intrusive Sponsored Space").assertDoesNotExist()
        // AdManager authorizes Home Dashboard only when idle, consented, online, and non-Pro
        assertTrue(
            fakeManager.canDisplayAds(
                com.example.ads.AdVisibilityContext(location = com.example.ads.AdScreenLocation.HOME_DASHBOARD)
            )
        )
    }

    @Test
    fun testDashboardSuppressesBannerAdWhenPremiumOrBenchmarking() {
        val fakeManager = com.example.ads.DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting {
                it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true)
            }
        }
        composeTestRule.setContent {
            GameBoostTheme {
                DashboardScreen(
                    stats = DeviceStats(deviceModel = "TestPhone", displayRefreshRate = 120f),
                    networkStats = NetworkStats(),
                    bottleneckAnalysis = null,
                    isPremium = false,
                    isBenchmarking = true,
                    protectionStatus = GamingProtectionStatus(overallSeverity = ProtectionSeverity.SAFE),
                    adManager = fakeManager,
                    onRunScanClick = {},
                    onOpenChecklistClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithText("Non-Intrusive Sponsored Space").assertDoesNotExist()
    }

    @Test
    fun testReportsScreenSuppressesSponsoredCardWhenThermalWarningOrPremium() {
        composeTestRule.setContent {
            GameBoostTheme {
                ReportsScreen(
                    stats = DeviceStats(),
                    isBenchmarking = false,
                    benchmarkSeconds = 0,
                    benchmarkSessions = BenchmarkRepository.getAllSessions(),
                    isPremium = true,
                    protectionStatus = GamingProtectionStatus(overallSeverity = ProtectionSeverity.WARNING),
                    onStartBenchmark = { _, _, _, _, _ -> },
                    onStopBenchmark = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("reports_analytics_overview_card"))
        composeTestRule.onNodeWithTag("reports_analytics_overview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_native_sponsored_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reports_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithText("SPONSORED").assertDoesNotExist()
    }

    @Test
    fun testSmartGamingDashboardUiRendersProfilesAndHonestTelemetry() {
        composeTestRule.setContent {
            GameBoostTheme {
                com.example.ui.screens.SmartGamingDashboard(
                    deviceStats = DeviceStats(
                        availableMemoryGb = 3.2f,
                        batteryTemperatureC = 34.5f,
                        batteryLevelPercent = 82,
                        powerManagerThermalStatus = "Normal"
                    ),
                    autopilotState = com.example.smartgaming.AutopilotState(
                        isAutopilotEnabled = true,
                        activeProfile = ProfileType.SMOOTHNESS_FOCUS,
                        targetGameTitle = "Free Fire MAX",
                        networkLatencyMs = -1
                    ),
                    gameProfiles = listOf(
                        GameProfile(
                            id = "ff",
                            name = "Free Fire MAX",
                            packageName = "com.dts.freefiremax",
                            performanceMode = PerformanceMode.PERFORMANCE_ASSISTANT,
                            isInLibrary = true
                        )
                    )
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ZEROX GAMING AUTOPILOT").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_gaming_lazy_column")
            .performScrollToNode(hasText("N/A (Not Tested)"))
        composeTestRule.onNodeWithText("N/A (Not Tested)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_gaming_lazy_column")
            .performScrollToNode(hasTestTag("autopilot_profile_smoothness_focus"))
        composeTestRule.onNodeWithText("Maximum Stability").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balanced Gaming").assertIsDisplayed()
        composeTestRule.onNodeWithText("Battery Friendly").assertIsDisplayed()
    }

    @Test
    fun testBenchmarkRepositoryStartsWithoutFabricatedSessions() {
        val initialSessions = BenchmarkRepository.getAllSessions()
        assertTrue(initialSessions.isEmpty())
    }
}

