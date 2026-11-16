package com.example.ads

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.DeviceStats
import com.example.data.EdgeResponseState
import com.example.data.NetworkStats
import com.example.data.ProtectionSeverity
import com.example.smartgaming.AutopilotState
import com.example.ui.components.GamingSidebarContent
import com.example.ui.screens.AimTrainerMiniGameSection
import com.example.ui.screens.BenchmarkLabScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GamingSpaceScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.GameBoostTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdMobManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testOfficialGoogleTestIdsAndManifestStringAreConfigured() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Production AdMob IDs strictly locked (4673454081 = Banner, 1855719057 = Native Advanced)
        assertEquals("ca-app-pub-9880038173911569~6505410503", AdMobConfig.PROD_APP_ID)
        assertEquals("ca-app-pub-9880038173911569/4673454081", AdMobConfig.PROD_BANNER_UNIT_ID)
        assertEquals("ca-app-pub-9880038173911569/1855719057", AdMobConfig.PROD_NATIVE_UNIT_ID)
        assertEquals(AdMobConfig.PROD_APP_ID, context.getString(R.string.admob_app_id))

        // Official Google sample test IDs preserved for development testing
        assertEquals("ca-app-pub-3940256099942544~3347511713", AdMobConfig.TEST_APP_ID)
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdMobConfig.TEST_ADAPTIVE_BANNER_UNIT_ID)
        assertEquals("ca-app-pub-3940256099942544/2247696110", AdMobConfig.TEST_NATIVE_SPONSORED_UNIT_ID)
    }

    @Test
    fun testStrictAdPlacementAndSuppressionRules() {
        val manager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting {
                it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true)
            }
        }

        // Allowed idle browsing screens
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_BANNER)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_NATIVE_SPONSORED)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.GAME_SPACE)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.TOOLS_HUB)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.AIM_TRAINER)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.BENCHMARK_LAB)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.DEVICE_DIAGNOSTICS)))
        assertTrue(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.NETWORK_TOOLS)))

        // Strictly ad-free screens (floating overlay, settings/permissions, loading/dialogs)
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.FLOATING_OVERLAY)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.SETTINGS_OR_PERMISSIONS)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.LOADING_OR_DIALOG)))

        // Forbidden active states on otherwise allowed screens
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD, isGamingSessionActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD, isAutopilotSessionActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD, isBenchmarking = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD, isScanning = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.GAME_SPACE, isGamingSessionActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.GAME_SPACE, isBenchmarking = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.GAME_SPACE, isPermissionOrLoadingActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.TOOLS_HUB, isGamingSessionActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.TOOLS_HUB, isAutopilotSessionActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.AIM_TRAINER, isAimTrainingActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.BENCHMARK_LAB, isLabRunning = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.BENCHMARK_LAB, isBenchmarking = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.DEVICE_DIAGNOSTICS, isScanning = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.NETWORK_TOOLS, isSpeedTesting = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_BANNER, isPermissionOrLoadingActive = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_NATIVE_SPONSORED, hasWarningOrErrorDialog = true)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD, thermalSeverity = ProtectionSeverity.WARNING)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_BANNER, thermalSeverity = ProtectionSeverity.CRITICAL)))
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.BENCHMARK_LAB, thermalSeverity = ProtectionSeverity.WARNING)))
    }

    @Test
    fun testProEntitlementAndConsentAndOfflineHideAds() {
        val proManager = DefaultAdManager(isProEntitlementActive = { true }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true) }
        }
        assertTrue(proManager.isAdFreeEntitled())
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_BANNER)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.GAME_SPACE)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.TOOLS_HUB)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.AIM_TRAINER)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.BENCHMARK_LAB)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.DEVICE_DIAGNOSTICS)))
        assertFalse(proManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.NETWORK_TOOLS)))

        val consentPendingManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = false, isNetworkAvailable = true) }
        }
        assertFalse(consentPendingManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD)))
        assertFalse(consentPendingManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.TOOLS_HUB)))

        val offlineManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = false) }
        }
        assertFalse(offlineManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD)))
        assertFalse(offlineManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.REPORTS_NATIVE_SPONSORED)))
        assertFalse(offlineManager.canDisplayAds(AdVisibilityContext(AdScreenLocation.NETWORK_TOOLS)))
    }

    @Test
    fun testOfflineNetworkRefreshAndFailureStateRecording() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        Shadows.shadowOf(cm).setActiveNetworkInfo(null)

        val manager = DefaultAdManager(isProEntitlementActive = { false })
        manager.updateStateForTesting { it.copy(canRequestAds = true) }
        val online = manager.refreshNetworkStatus(context)
        assertFalse(online)
        assertFalse(manager.adState.value.isNetworkAvailable)
        assertFalse(manager.canDisplayAds(AdVisibilityContext(AdScreenLocation.HOME_DASHBOARD)))

        manager.recordBannerStatus(AdScreenLocation.HOME_DASHBOARD, AdLoadStatus.FAILED, "3: No fill")
        manager.recordNativeStatus(AdLoadStatus.FAILED, "0: Offline")
        assertEquals(AdLoadStatus.FAILED, manager.adState.value.homeBannerStatus)
        assertEquals(AdLoadStatus.FAILED, manager.adState.value.nativeSponsoredStatus)
        assertEquals("0: Offline", manager.adState.value.lastAdError)

        manager.recordBannerStatus(AdScreenLocation.NETWORK_TOOLS, AdLoadStatus.FAILED, "2: Network error")
        assertEquals("2: Network error", manager.adState.value.lastAdError)
    }

    @Test
    fun testAdsHiddenDuringActiveAutopilotSessionAndInsideOverlay() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                DashboardScreen(
                    stats = DeviceStats(),
                    networkStats = NetworkStats(),
                    bottleneckAnalysis = null,
                    autopilotState = AutopilotState(isSessionActive = true),
                    adManager = onlineFreeManager,
                    onRunScanClick = {},
                    onOpenChecklistClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testExpandedScreensHideAdsDuringActiveTasksAndProEntitlement() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                ToolsHubScreen(
                    isPremium = true,
                    adManager = onlineFreeManager,
                    onNavigateToScan = {},
                    onNavigateToNetwork = {},
                    onNavigateToAim = {},
                    onNavigateToLab = {},
                    onNavigateToDeviceControl = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tools_hub_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testGameSpaceBenchmarkDiagnosticsNetworkHideAdsDuringActiveTasks() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                GamingSpaceScreen(
                    profiles = emptyList(),
                    deviceStats = DeviceStats(),
                    isBenchmarking = true,
                    benchmarkSeconds = 12,
                    benchmarkSessions = emptyList(),
                    installedApps = emptyList(),
                    isLoadingApps = false,
                    isPremium = false,
                    adManager = onlineFreeManager,
                    onRefreshApps = {},
                    onAddGameToVault = { _, _ -> },
                    onRemoveGameFromVault = {},
                    onToggleFavorite = {},
                    onUpdateFullProfile = {},
                    onLaunchGame = { _, _ -> false },
                    onStopSession = {},
                    onOpenSidebarPreview = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("game_space_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testBenchmarkLabScanNetworkAndAimTrainerHideAdsWhileActive() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                BenchmarkLabScreen(
                    labResults = emptyList(),
                    isLabRunning = true,
                    labProgress = 0.5f,
                    activeWorkload = "CPU",
                    telemetryTemp = 38f,
                    telemetryThermal = "Normal",
                    isPremium = false,
                    adManager = onlineFreeManager,
                    onRunWorkload = { _, _ -> },
                    onCancelLab = {},
                    onClearHistory = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("benchmark_lab_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testScanAndNetworkHideAdsWhileRunning() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                ScanScreen(
                    isScanning = true,
                    results = emptyList(),
                    bottleneckAnalysis = null,
                    checklist = emptyList(),
                    storageData = null,
                    displayData = null,
                    batteryData = null,
                    systemData = null,
                    diagnosticReport = null,
                    edgeResponseState = EdgeResponseState(),
                    lastSwipeResult = null,
                    onToggleChecklistItem = {},
                    onStartScan = {},
                    onRecordSwipe = {},
                    onRecordEdgeHit = {},
                    onResetEdgeTests = {},
                    onClearAppCache = {},
                    onRefreshDiagnostics = {},
                    isPremium = false,
                    adManager = onlineFreeManager
                )
                NetworkScreen(
                    stats = NetworkStats(),
                    isSpeedTesting = true,
                    speedTestProgress = 0.4f,
                    networkHistory = emptyList(),
                    onTestPing = {},
                    onStartSpeedTest = {},
                    onCancelSpeedTest = {},
                    isPremium = false,
                    adManager = onlineFreeManager
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_diagnostics_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("network_tools_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testAimTrainerHidesBannerOnceTrainingStarts() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                AimTrainerMiniGameSection(
                    isPremium = false,
                    adManager = onlineFreeManager,
                    onSaveSession = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Start Aim Training (30s)").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("aim_trainer_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testOverlaySidebarContainsZeroAdsAndSettingsPrivacyPolicyWorks() {
        val freeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting {
                it.copy(
                    canRequestAds = true,
                    isNetworkAvailable = false,
                    consentStatusMessage = "Consent check offline"
                )
            }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                SettingsScreen(adManager = freeManager)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("settings_privacy_policy_button"))
        composeTestRule.onNodeWithTag("settings_privacy_policy_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GAMEBOOST Privacy Policy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()
    }

    @Test
    fun testOverlaySidebarHasNoAdSlots() {
        composeTestRule.setContent {
            GameBoostTheme {
                GamingSidebarContent(
                    isOpen = true,
                    onClose = {},
                    onOpen = {},
                    memoryUsage = 45,
                    availMemory = 4.2f,
                    batteryTemp = 34.5f,
                    batteryLevel = 82,
                    thermalStatus = "Normal",
                    pingMs = 24,
                    onRefreshPing = {},
                    onPersistAppearance = { _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reports_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reports_native_sponsored_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tools_hub_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("game_space_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("aim_trainer_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("benchmark_lab_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("device_diagnostics_ad_banner_slot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("network_tools_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testReportsHidesAdsDuringAutopilotSession() {
        val onlineFreeManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                ReportsScreen(
                    stats = DeviceStats(),
                    isBenchmarking = false,
                    benchmarkSeconds = 0,
                    benchmarkSessions = emptyList(),
                    isPremium = false,
                    isAutopilotActive = true,
                    adManager = onlineFreeManager,
                    onStartBenchmark = { _, _, _, _, _ -> },
                    onStopBenchmark = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reports_native_sponsored_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reports_ad_banner_slot").assertDoesNotExist()
    }

    @Test
    fun testAdManagerRequestCountersNoFillAndErrorClassification() {
        val manager = DefaultAdManager(isProEntitlementActive = { false })
        assertEquals(0, manager.adState.value.totalRequestsCount)
        assertEquals(0, manager.adState.value.totalLoadedCount)
        assertEquals(0, manager.adState.value.noFillCount)

        manager.recordBannerStatus(AdScreenLocation.HOME_DASHBOARD, AdLoadStatus.LOADING)
        assertEquals(1, manager.adState.value.totalRequestsCount)

        manager.recordBannerStatus(AdScreenLocation.HOME_DASHBOARD, AdLoadStatus.LOADED)
        assertEquals(1, manager.adState.value.totalLoadedCount)
        assertNull(manager.adState.value.lastErrorCode)

        val (noFillStatus, noFillMsg) = classifyAdError(3, "No fill.", "com.google.android.gms.ads")
        assertEquals(AdLoadStatus.NO_FILL, noFillStatus)
        manager.recordNativeStatus(AdLoadStatus.LOADING)
        manager.recordNativeStatus(noFillStatus, noFillMsg, 3)
        assertEquals(2, manager.adState.value.totalRequestsCount)
        assertEquals(1, manager.adState.value.noFillCount)
        assertEquals(3, manager.adState.value.lastErrorCode)
        assertTrue(manager.adState.value.lastAdError!!.contains("NO_FILL"))

        val (netErrStatus, netErrMsg) = classifyAdError(2, "Network error.", "com.google.android.gms.ads")
        assertEquals(AdLoadStatus.FAILED, netErrStatus)
        manager.recordBannerStatus(AdScreenLocation.NETWORK_TOOLS, netErrStatus, netErrMsg, 2)
        assertEquals(1, manager.adState.value.failedCount)
        assertEquals(2, manager.adState.value.lastErrorCode)
        assertEquals(AdScreenLocation.NETWORK_TOOLS, manager.adState.value.lastBannerLocation)
    }

    @Test
    fun testAdaptiveWidthClampsToMeasuredContainerWidth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DefaultAdManager(isProEntitlementActive = { false })
        // Measured container width 310dp must not request a wider banner (prevents "Not enough space to show ad")
        assertEquals(310, manager.computeAnchoredAdaptiveWidthDp(context, availableWidthDp = 310))
        assertEquals(220, manager.computeAnchoredAdaptiveWidthDp(context, availableWidthDp = 180))
        assertEquals(720, manager.computeAnchoredAdaptiveWidthDp(context, availableWidthDp = 960))
    }

    @Test
    fun testSettingsScreenAdMobDiagnosticsCardDisplaysAccurateState() {
        val manager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting {
                it.copy(
                    isSdkInitialized = true,
                    readyAdaptersCount = 1,
                    totalAdaptersCount = 1,
                    canRequestAds = true,
                    isNetworkAvailable = true,
                    isTestMode = false,
                    totalRequestsCount = 5,
                    totalLoadedCount = 3,
                    noFillCount = 2,
                    lastErrorCode = 3,
                    lastAdError = "Code 3 (NO_FILL): Live inventory no fill"
                )
            }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                SettingsScreen(adManager = manager)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("settings_admob_diagnostics_card"))
        composeTestRule.onNodeWithTag("settings_admob_diagnostics_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("AdMob Diagnostics & Status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PRODUCTION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Initialized (1/1 ready)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_retry_admob_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_toggle_test_mode_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_toggle_ad_mapping_button").assertDoesNotExist()
    }

    @Test
    fun testAdMobConfigProductionModeAndTestModeToggleNeverSwapsIds() {
        val manager = DefaultAdManager(isProEntitlementActive = { false })

        // Default is test mode with Google sample test IDs for instant sideload preview
        assertTrue(AdMobConfig.IS_TEST_MODE)
        assertEquals(AdMobConfig.TEST_APP_ID, AdMobConfig.currentAppId)
        assertEquals(AdMobConfig.TEST_ADAPTIVE_BANNER_UNIT_ID, AdMobConfig.currentBannerAdUnitId)
        assertEquals(AdMobConfig.TEST_NATIVE_SPONSORED_UNIT_ID, AdMobConfig.currentNativeAdUnitId)

        // Switching to production mode uses official production IDs (Banner = ...4673454081, Native Advanced = ...1855719057)
        val prodModeActive = !manager.toggleAdMobTestMode()
        assertTrue(prodModeActive)
        assertFalse(AdMobConfig.IS_TEST_MODE)
        assertEquals("ca-app-pub-9880038173911569~6505410503", AdMobConfig.currentAppId)
        assertEquals("ca-app-pub-9880038173911569/4673454081", AdMobConfig.currentBannerAdUnitId)
        assertEquals("ca-app-pub-9880038173911569/1855719057", AdMobConfig.currentNativeAdUnitId)

        // Restoring test mode keeps sample IDs
        val testModeActive = manager.toggleAdMobTestMode()
        assertTrue(testModeActive)
        assertTrue(AdMobConfig.IS_TEST_MODE)
        assertEquals(AdMobConfig.TEST_APP_ID, AdMobConfig.currentAppId)
        assertEquals(AdMobConfig.TEST_ADAPTIVE_BANNER_UNIT_ID, AdMobConfig.currentBannerAdUnitId)
        assertEquals(AdMobConfig.TEST_NATIVE_SPONSORED_UNIT_ID, AdMobConfig.currentNativeAdUnitId)
    }

    @Test
    fun testAllEightIdleBannerPlacementsRenderWhenEligible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val netInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            android.net.NetworkInfo.State.CONNECTED
        )
        Shadows.shadowOf(cm).setActiveNetworkInfo(netInfo)
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)

        val onlineFreeManager = object : DefaultAdManager(isProEntitlementActive = { false }) {
            override fun loadBannerAd(adView: com.google.android.gms.ads.AdView) {
                recordBannerStatus(AdScreenLocation.TOOLS_HUB, AdLoadStatus.LOADED)
            }
        }.apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                androidx.compose.foundation.layout.Column {
                    ToolsHubScreen(
                        isPremium = false,
                        adManager = onlineFreeManager,
                        onNavigateToScan = {},
                        onNavigateToNetwork = {},
                        onNavigateToAim = {},
                        onNavigateToLab = {},
                        onNavigateToDeviceControl = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tools_hub_ad_banner_slot").assertIsDisplayed()
    }

    @Test
    fun testHomeDashboardBannerSlotIsDisplayedWithoutScrolling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val netInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            android.net.NetworkInfo.State.CONNECTED
        )
        Shadows.shadowOf(cm).setActiveNetworkInfo(netInfo)
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)

        val onlineFreeManager = object : DefaultAdManager(isProEntitlementActive = { false }) {
            override fun loadBannerAd(adView: com.google.android.gms.ads.AdView) {
                recordBannerStatus(AdScreenLocation.HOME_DASHBOARD, AdLoadStatus.LOADED)
            }
        }.apply {
            updateStateForTesting { it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true) }
        }

        composeTestRule.setContent {
            GameBoostTheme {
                DashboardScreen(
                    stats = DeviceStats(),
                    networkStats = NetworkStats(),
                    bottleneckAnalysis = null,
                    isPremium = false,
                    adManager = onlineFreeManager,
                    onRunScanClick = {},
                    onOpenChecklistClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_ad_banner_slot").assertIsDisplayed()
    }

    @Test
    fun testRetryConsentAndSdkFlowDoesNotThrow() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val manager = DefaultAdManager(isProEntitlementActive = { false })
        manager.retryConsentAndSdkFlow(activity)
        assertNotNull(manager.adState.value)
    }

    @Test
    fun testReleaseBuildLocksTestModeOffAndResolvesProductionUnitIds() {
        assertFalse(AdMobConfig.resolveDefaultTestMode(isDebugBuild = false))
        assertTrue(AdMobConfig.resolveDefaultTestMode(isDebugBuild = true))
        assertFalse(AdMobConfig.resolveTestModeToggle(currentIsTestMode = false, isDebugBuild = false))
        assertFalse(AdMobConfig.resolveTestModeToggle(currentIsTestMode = true, isDebugBuild = false))
        assertFalse(AdMobConfig.resolveTestModeToggle(currentIsTestMode = true, isDebugBuild = true))
        assertTrue(AdMobConfig.resolveTestModeToggle(currentIsTestMode = false, isDebugBuild = true))
        assertEquals("ca-app-pub-9880038173911569~6505410503", AdMobConfig.PROD_APP_ID)
        assertEquals("ca-app-pub-9880038173911569/4673454081", AdMobConfig.PROD_BANNER_UNIT_ID)
        assertEquals("ca-app-pub-9880038173911569/1855719057", AdMobConfig.PROD_NATIVE_UNIT_ID)
    }
}
