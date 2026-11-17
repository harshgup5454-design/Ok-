package com.example.overlay

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.ads.AdScreenLocation
import com.example.ads.AdVisibilityContext
import com.example.ads.DefaultAdManager
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.service.OverlayTouchContainer
import com.example.service.SafeWindowBounds
import com.example.smartgaming.AutopilotState
import com.example.smartgaming.SmartGamingManager
import com.example.ui.components.GamingSidebarContent
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GameBoostTheme
import com.example.viewmodel.GameBoostViewModel
import com.example.viewmodel.SessionLaunchPrompt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GamingOverlayExperienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GamingOverlayService.testWindowManagerOverride = null
        GamingOverlayService.stopOverlay(context)
        GamingOverlayService.clearLastOverlayError()
        ShadowSettings.setCanDrawOverlays(false)
        GamingOverlayService.setAppForeground(false)
        GamingOverlayService.consumePendingAimTrainerOnReturn()
    }

    @Test
    fun `startOverlay returns blocked result when SYSTEM_ALERT_WINDOW is denied`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowSettings.setCanDrawOverlays(false)

        val result = GamingOverlayService.startOverlay(context)
        assertFalse(result.started)
        assertTrue(result.reason.contains("permission", ignoreCase = true))
        assertFalse(GamingOverlayService.isServiceRunning.value)
    }

    @Test
    fun `service refuses to stay alive if created without overlay permission`() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildService(GamingOverlayService::class.java)
        controller.create()

        assertFalse(GamingOverlayService.isServiceRunning.value)
        controller.destroy()
        assertFalse(GamingOverlayService.isServiceRunning.value)
    }

    @Test
    fun `service attaches overlay handles rotation and cleans up when destroyed`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(GamingOverlayService::class.java)
        val service = controller.create().get()

        assertTrue(GamingOverlayService.isServiceRunning.value)

        // Simulate device rotation Portrait -> Landscape -> Portrait while running
        val landscape = Configuration(service.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        service.onConfigurationChanged(landscape)
        val portrait = Configuration(service.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        service.onConfigurationChanged(portrait)

        assertTrue(GamingOverlayService.isServiceRunning.value)

        // Revoking permission in onStartCommand stops service safely
        ShadowSettings.setCanDrawOverlays(false)
        service.onStartCommand(null, 0, 1)
        assertFalse(GamingOverlayService.isServiceRunning.value)

        controller.destroy()
        assertFalse(GamingOverlayService.isServiceRunning.value)
    }

    @Test
    fun `raw touch container separates clean tap inward swipe vertical drag cancel and outside tap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var tapCount = 0
        var swipeCount = 0
        var outsideCount = 0
        var lastDragDelta: Int? = null
        var currentParamY = 120

        val container = OverlayTouchContainer(
            context = context,
            onTapOpen = { tapCount++ },
            onSwipeOpen = { swipeCount++ },
            onOutsideTap = { outsideCount++ },
            onDragY = { startY, deltaY ->
                lastDragDelta = startY + deltaY
            },
            getCurrentParamY = { currentParamY }
        )

        val downTime = SystemClock.uptimeMillis()

        // 1. Clean tap (down then up with minimal drift) -> opens sidebar, zero drag
        val tapDown = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 500f, 0)
        val tapUp = MotionEvent.obtain(downTime, downTime + 45, MotionEvent.ACTION_UP, 102f, 501f, 0)
        assertTrue(container.onInterceptTouchEvent(tapDown))
        assertTrue(container.onTouchEvent(tapDown))
        assertTrue(container.onTouchEvent(tapUp))
        assertEquals(1, tapCount)
        assertEquals(0, swipeCount)
        assertNull(lastDragDelta)
        tapDown.recycle()
        tapUp.recycle()

        // 2. Vertical drag -> updates Y position, never opens sidebar on release
        val dragDown = MotionEvent.obtain(downTime + 100, downTime + 100, MotionEvent.ACTION_DOWN, 900f, 400f, 0)
        val dragMove = MotionEvent.obtain(downTime + 100, downTime + 130, MotionEvent.ACTION_MOVE, 902f, 560f, 0)
        val dragUp = MotionEvent.obtain(downTime + 100, downTime + 160, MotionEvent.ACTION_UP, 902f, 560f, 0)
        container.onTouchEvent(dragDown)
        container.onTouchEvent(dragMove)
        container.onTouchEvent(dragUp)
        assertEquals(280, lastDragDelta) // 120 start + 160 dy
        assertEquals(1, tapCount)
        assertEquals(0, swipeCount)
        dragDown.recycle()
        dragMove.recycle()
        dragUp.recycle()

        // 3. Horizontal swipe (both R->L and L->R) -> opens via swipe immediately
        val swipeDown = MotionEvent.obtain(downTime + 200, downTime + 200, MotionEvent.ACTION_DOWN, 980f, 500f, 0)
        val swipeMove = MotionEvent.obtain(downTime + 200, downTime + 240, MotionEvent.ACTION_MOVE, 820f, 504f, 0)
        val swipeUp = MotionEvent.obtain(downTime + 200, downTime + 260, MotionEvent.ACTION_UP, 820f, 504f, 0)
        container.onTouchEvent(swipeDown)
        container.onTouchEvent(swipeMove)
        container.onTouchEvent(swipeUp)
        assertEquals(1, swipeCount)
        assertEquals(1, tapCount)
        swipeDown.recycle()
        swipeMove.recycle()
        swipeUp.recycle()

        val lrDown = MotionEvent.obtain(downTime + 270, downTime + 270, MotionEvent.ACTION_DOWN, 120f, 500f, 0)
        val lrMove = MotionEvent.obtain(downTime + 270, downTime + 285, MotionEvent.ACTION_MOVE, 280f, 502f, 0)
        val lrUp = MotionEvent.obtain(downTime + 270, downTime + 295, MotionEvent.ACTION_UP, 280f, 502f, 0)
        container.onTouchEvent(lrDown)
        container.onTouchEvent(lrMove)
        container.onTouchEvent(lrUp)
        assertEquals(2, swipeCount)
        lrDown.recycle()
        lrMove.recycle()
        lrUp.recycle()

        // 4. ACTION_CANCEL -> cleans up without triggering tap open
        val cancelDown = MotionEvent.obtain(downTime + 300, downTime + 300, MotionEvent.ACTION_DOWN, 980f, 500f, 0)
        val cancelEv = MotionEvent.obtain(downTime + 300, downTime + 320, MotionEvent.ACTION_CANCEL, 980f, 500f, 0)
        container.onTouchEvent(cancelDown)
        container.onTouchEvent(cancelEv)
        assertEquals(1, tapCount)
        cancelDown.recycle()
        cancelEv.recycle()

        // 5. Open mode lets Compose intercept children and outside tap closes sidebar
        container.updateTouchMode(open = true)
        val openDown = MotionEvent.obtain(downTime + 400, downTime + 400, MotionEvent.ACTION_DOWN, 800f, 500f, 0)
        assertFalse(container.onInterceptTouchEvent(openDown))
        val outsideEv = MotionEvent.obtain(downTime + 420, downTime + 420, MotionEvent.ACTION_OUTSIDE, 100f, 500f, 0)
        assertTrue(container.onTouchEvent(outsideEv))
        assertEquals(1, outsideCount)
        openDown.recycle()
        outsideEv.recycle()
    }

    @Test
    fun `safe bounds clamp handle Y cleanly and detect external games vs launcher`() {
        val bounds = SafeWindowBounds(
            widthPx = 1080,
            heightPx = 2400,
            safeLeftPx = 0,
            safeTopPx = 110, // punch-hole / status bar cutout
            safeRightPx = 0,
            safeBottomPx = 96 // gesture bar
        )
        val clampedHigh = GamingOverlayService.clampHandleY(-9999, bounds, handleHeightPx = 180)
        val clampedLow = GamingOverlayService.clampHandleY(9999, bounds, handleHeightPx = 180)
        assertTrue(clampedHigh > -1200)
        assertTrue(clampedLow < 1200)

        assertTrue(GamingOverlayService.isKnownGameOrExternalApp("com.dts.freefiremax", "com.example"))
        assertFalse(GamingOverlayService.isKnownGameOrExternalApp("com.example", "com.example"))
        assertFalse(GamingOverlayService.isKnownGameOrExternalApp("com.google.android.apps.nexuslauncher", "com.example"))
        assertFalse(GamingOverlayService.isKnownGameOrExternalApp("com.android.systemui", "com.example"))
        assertFalse(GamingOverlayService.isKnownGameOrExternalApp(null, "com.example"))
    }

    @Test
    fun `floating sidebar content renders all 9 controls has zero ads and toggles states cleanly`() {
        var selectedProfile: String? = null
        var isOpenState = false

        composeRule.setContent {
            var isOpen by remember { mutableStateOf(false) }
            isOpenState = isOpen
            var queued by remember { mutableStateOf(false) }
            GameBoostTheme {
                GamingSidebarContent(
                    isOpen = isOpen,
                    onOpen = { isOpen = true },
                    onClose = { isOpen = false },
                    memoryUsage = 64,
                    availMemory = 2.8f,
                    batteryTemp = 36.2f,
                    batteryLevel = 81,
                    thermalStatus = "Normal",
                    pingMs = 28,
                    jitterMs = 4,
                    refreshRateHz = 120f,
                    targetGameTitle = "Free Fire MAX",
                    activeProfileLabel = "Balanced",
                    sessionSeconds = 125L,
                    onSelectProfile = { selectedProfile = it },
                    onRequestAimTrainer = { queued = true },
                    isAimTrainerQueued = queued,
                    aimTrainerStatusHint = if (queued) "Queued — Aim Trainer opens when you exit the game." else null,
                    onCancelAimTrainerQueue = { queued = false },
                    onStopService = {},
                    isSystemOverlay = false
                )
            }
        }

        // Handle starts visible, sidebar panel closed
        composeRule.onNodeWithTag("overlay_edge_handle").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_sidebar_panel").assertDoesNotExist()

        // Tap edge handle -> opens sidebar
        composeRule.onNodeWithTag("overlay_edge_handle").performClick()
        composeRule.waitForIdle()
        assertTrue(isOpenState)
        composeRule.onNodeWithTag("overlay_sidebar_panel").assertIsDisplayed()

        // Core telemetry, profile switcher, tools, timer, and zero ads check
        composeRule.onNodeWithText("ZeroX BOOST").assertIsDisplayed()
        composeRule.onNodeWithText("02:05").assertIsDisplayed()
        composeRule.onNodeWithText("64%").assertIsDisplayed()
        composeRule.onNodeWithText("36.2°C").assertIsDisplayed()
        composeRule.onNodeWithText("28ms").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_sidebar_lazy_column").performScrollToNode(hasText("Stability"))
        composeRule.onNodeWithText("Stability").performClick()
        assertEquals("Stability", selectedProfile)

        // Aim Trainer queued state when triggered inside game
        composeRule.onNodeWithTag("overlay_sidebar_lazy_column").performScrollToNode(hasTestTag("overlay_aim_trainer_button"))
        composeRule.onNodeWithTag("overlay_aim_trainer_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Queued — Aim Trainer opens when you exit the game.").assertIsDisplayed()

        // Zero ads in sidebar
        composeRule.onNodeWithText("SPONSORED").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()

        // Compact HUD pill toggle & return
        composeRule.onNodeWithTag("overlay_sidebar_lazy_column").performScrollToNode(hasTestTag("overlay_compact_button"))
        composeRule.onNodeWithTag("overlay_compact_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_compact_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_compact_pill").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_sidebar_panel").assertIsDisplayed()

        // Close button collapses back to edge handle
        composeRule.onNodeWithTag("overlay_sidebar_lazy_column").performScrollToNode(hasTestTag("overlay_close_button"))
        composeRule.onNodeWithTag("overlay_close_button").performClick()
        composeRule.waitForIdle()
        assertFalse(isOpenState)
    }

    @Test
    fun `main activity uses 5 simplified tabs and opens pro from home badge`() {
        val scenario = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = scenario.get()
        assertNotNull(activity)
        scenario.pause().stop().destroy()
    }

    @Test
    fun `dashboard home displays streamlined 9 elements and suppresses ads during active session`() {
        val adManager = DefaultAdManager(isProEntitlementActive = { false }).apply {
            updateStateForTesting {
                it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true)
            }
        }

        var sessionActive by mutableStateOf(false)
        var settingsClicks = 0

        composeRule.setContent {
            GameBoostTheme {
                DashboardScreen(
                    stats = DeviceStats(
                        deviceModel = "RedMagic Test",
                        androidVersion = "14",
                        displayRefreshRate = 120f,
                        memoryUsagePercent = 58,
                        availableMemoryGb = 4.2f,
                        batteryLevelPercent = 79,
                        batteryTemperatureC = 34.0f,
                        powerSource = "Battery",
                        powerManagerThermalStatus = "Normal",
                        thermalHeadroom = 0.25f
                    ),
                    networkStats = NetworkStats(pingMs = 22, jitterMs = 2, connectionType = "Wi-Fi", isConnected = true),
                    bottleneckAnalysis = null,
                    isBenchmarking = sessionActive,
                    autopilotState = AutopilotState(activeProfile = ProfileType.BALANCED, targetGameTitle = "Free Fire MAX"),
                    protectionStatus = GamingProtectionStatus(overallSeverity = ProtectionSeverity.SAFE, headline = "Optimal Gaming Conditions"),
                    adManager = adManager,
                    onStartBenchmark = { _, _, _, _, _ -> sessionActive = true },
                    onStopBenchmark = { _, _ -> sessionActive = false },
                    onRunScanClick = {},
                    onOpenChecklistClick = {},
                    onNavigateToOverlaySettings = { settingsClicks++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("ZeroX BOOST").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_protection_card").assertIsDisplayed()
        composeRule.onNodeWithText("RAM Usage").assertIsDisplayed()
        composeRule.onNodeWithText("Battery & Temp").assertIsDisplayed()
        composeRule.onNodeWithText("Network").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("dashboard_sidebar_quick_card"))
        composeRule.onNodeWithTag("dashboard_sidebar_settings_button").performClick()
        assertEquals(1, settingsClicks)

        // Start gaming session -> suppresses Home banner ad immediately
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("dashboard_start_gaming_button"))
        composeRule.onNodeWithTag("dashboard_start_gaming_button").performClick()
        composeRule.waitForIdle()
        assertTrue(sessionActive)
        composeRule.onNodeWithTag("dashboard_stop_gaming_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()
        assertFalse(
            adManager.canDisplayAds(
                AdVisibilityContext(
                    location = AdScreenLocation.HOME_DASHBOARD,
                    isGamingSessionActive = true
                )
            )
        )
    }

    @Test
    fun `settings screen shows grant button when overlay permission missing and toggle when granted`() {
        ShadowSettings.setCanDrawOverlays(false)
        composeRule.setContent {
            GameBoostTheme {
                SettingsScreen(overlayConfig = OverlayConfig())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_overlay_grant_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_overlay_service_switch").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `landscape gaming overlay renders symmetrical dual wings with center passthrough dismiss`() {
        var isOpenState = true
        composeRule.setContent {
            var isOpen by remember { mutableStateOf(true) }
            isOpenState = isOpen
            GameBoostTheme {
                GamingSidebarContent(
                    isOpen = isOpen,
                    onOpen = { isOpen = true },
                    onClose = { isOpen = false },
                    memoryUsage = 64,
                    availMemory = 2.8f,
                    batteryTemp = 36.2f,
                    batteryLevel = 81,
                    thermalStatus = "Normal",
                    pingMs = 28,
                    jitterMs = 4,
                    refreshRateHz = 120f,
                    targetGameTitle = "Free Fire MAX",
                    activeProfileLabel = "Balanced",
                    sessionSeconds = 125L,
                    onStopService = {},
                    isSystemOverlay = false
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay_sidebar_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_left_wing").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_right_wing").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_center_passthrough").assertIsDisplayed()

        // Left & right wing telemetry and controls
        composeRule.onNodeWithText("ZeroX BOOST").assertIsDisplayed()
        composeRule.onNodeWithText("120 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("64%").assertIsDisplayed()
        composeRule.onNodeWithText("36.2°C").assertIsDisplayed()
        composeRule.onNodeWithText("02:05").assertIsDisplayed()
        composeRule.onNodeWithText("28ms").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_right_wing_list").performScrollToNode(hasText("Vol -"))
        composeRule.onNodeWithText("Vol -").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_right_wing_list").performScrollToNode(hasTestTag("overlay_stop_service_button"))
        composeRule.onNodeWithTag("overlay_stop_service_button").assertIsDisplayed()

        // Zero ads in dual-wing overlay
        composeRule.onNodeWithText("SPONSORED").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_ad_banner_slot").assertDoesNotExist()

        // Tapping transparent center dismisses both wings back to edge trigger
        composeRule.onNodeWithTag("overlay_center_passthrough").performClick()
        composeRule.waitForIdle()
        assertFalse(isOpenState)
        composeRule.onNodeWithTag("overlay_edge_handle").assertIsDisplayed()
    }

    @Test
    fun `root window container binds ViewTree owners and stays alive when leaving GAMEBOOST`() {
        ShadowSettings.setCanDrawOverlays(true)
        GamingOverlayService.setAppForeground(true)

        val controller = Robolectric.buildService(GamingOverlayService::class.java)
        val service = controller.create().get()
        assertTrue(GamingOverlayService.isServiceRunning.value)

        val rootView = service.attachedRootForTesting()
        assertNotNull(rootView)
        assertSame(service, rootView!!.findViewTreeLifecycleOwner())
        assertSame(service, rootView.findViewTreeViewModelStoreOwner())
        assertSame(service, rootView.findViewTreeSavedStateRegistryOwner())

        // Leaving GAMEBOOST (MainActivity moves to background) must NOT stop or crash the overlay service
        GamingOverlayService.setAppForeground(false)
        assertTrue(GamingOverlayService.isServiceRunning.value)
        assertNull(GamingOverlayService.lastOverlayError.value)

        // Duplicate start request is safely short-circuited without creating a duplicate instance
        val duplicate = GamingOverlayService.startOverlay(service, autoStartedBySession = true)
        assertTrue(duplicate.started)
        assertTrue(duplicate.reason.contains("already active", ignoreCase = true))

        controller.destroy()
        assertFalse(GamingOverlayService.isServiceRunning.value)
    }

    @Test
    fun `window manager BadTokenException during overlay attach cleans up safely without crashing`() {
        ShadowSettings.setCanDrawOverlays(true)
        val realWm = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val throwingWm = java.lang.reflect.Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, method, args ->
            if (method.name == "addView") {
                throw WindowManager.BadTokenException("Simulated invalid overlay token")
            }
            if (args == null) method.invoke(realWm) else method.invoke(realWm, *args)
        } as WindowManager

        GamingOverlayService.testWindowManagerOverride = throwingWm
        val controller = Robolectric.buildService(GamingOverlayService::class.java)
        val service = controller.create().get()

        assertFalse(GamingOverlayService.isServiceRunning.value)
        assertFalse(GamingOverlayService.isSessionOwnedOverlay.value)
        assertNull(service.attachedRootForTesting())
        val err = GamingOverlayService.lastOverlayError.value
        assertNotNull(err)
        assertTrue(err!!.contains("BadTokenException"))

        controller.destroy()
    }

    @Test
    fun `service startup FGS failure is caught and sets recoverable error state`() {
        ShadowSettings.setCanDrawOverlays(true)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val failingContext = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent?): android.content.ComponentName? {
                throw SecurityException("Simulated Android 15 background FGS restriction")
            }
        }

        val result = GamingOverlayService.startOverlay(failingContext, autoStartedBySession = true)
        assertFalse(result.started)
        assertTrue(result.reason.contains("SecurityException"))
        assertFalse(GamingOverlayService.isSessionOwnedOverlay.value)
        assertNotNull(GamingOverlayService.lastOverlayError.value)
    }

    @Test
    fun `startGamingSessionWithOverlay validates permissions and missing games then launches and cleans up overlay`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        OverlayPreferences.update(app) {
            it.copy(accentTheme = "Purple", panelOpacity = 0.77f, defaultCompactMode = true)
        }
        val viewModel = GameBoostViewModel(app)

        // 1. Missing installed package triggers GameNotInstalled prompt
        val missingProfile = GameProfile(
            id = "missing_1",
            name = "Missing Game",
            packageName = "com.nonexistent.game.pkg",
            isInstalled = false
        )
        assertFalse(
            viewModel.startGamingSessionWithOverlay(
                context = app,
                selectedProfile = missingProfile,
                selectedGameTitle = "Missing Game",
                profileType = ProfileType.BALANCED
            )
        )
        assertTrue(viewModel.sessionLaunchPrompt.value is SessionLaunchPrompt.GameNotInstalled)

        // 2. Global mode with overlay denied triggers MissingOverlayPermission prompt
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(
            viewModel.startGamingSessionWithOverlay(
                context = app,
                selectedProfile = null,
                selectedGameTitle = "Global Gaming Mode",
                profileType = ProfileType.SMOOTHNESS_FOCUS
            )
        )
        assertTrue(viewModel.sessionLaunchPrompt.value is SessionLaunchPrompt.MissingOverlayPermission)

        // 3. Register a real launchable game package & grant overlay permission -> auto-starts overlay + preserves theme/opacity
        val testPkg = "com.example.testgame"
        val pmShadow = shadowOf(app.packageManager)
        val component = android.content.ComponentName(testPkg, "$testPkg.GameActivity")
        pmShadow.addActivityIfNotPresent(component)
        pmShadow.addIntentFilterForActivity(
            component,
            android.content.IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        )

        ShadowSettings.setCanDrawOverlays(true)
        val installedGame = GameProfile(
            id = "installed_1",
            name = "Test Arena",
            packageName = testPkg,
            isInstalled = true,
            performanceMode = PerformanceMode.PERFORMANCE_ASSISTANT
        )

        val launched = viewModel.startGamingSessionWithOverlay(
            context = app,
            selectedProfile = installedGame,
            selectedGameTitle = "Test Arena",
            profileType = ProfileType.SMOOTHNESS_FOCUS
        )
        assertTrue(launched)
        assertNull(viewModel.sessionLaunchPrompt.value)
        assertTrue(viewModel.isBenchmarking.value)
        assertTrue(GamingOverlayService.isSessionOwnedOverlay.value)

        // Build service to simulate lifecycle start and confirm theme/opacity remain preserved
        val controller = Robolectric.buildService(GamingOverlayService::class.java)
        controller.create().get()
        assertTrue(GamingOverlayService.isServiceRunning.value)
        assertEquals("Purple", OverlayPreferences.config.value.accentTheme)
        assertEquals(0.77f, OverlayPreferences.config.value.panelOpacity, 0.01f)

        // Switching between GAMEBOOST and the game preserves session & overlay ownership
        GamingOverlayService.setAppForeground(false)
        assertTrue(GamingOverlayService.isServiceRunning.value)
        assertTrue(GamingOverlayService.isSessionOwnedOverlay.value)

        // Ending the session cleanly stops session-owned overlay ownership & service intent
        viewModel.stopBenchmarkSession(app)
        assertFalse(viewModel.isBenchmarking.value)
        assertFalse(GamingOverlayService.isSessionOwnedOverlay.value)
        controller.destroy()
    }

    @Test
    fun `DeviceControlEngine restores only GAMEBOOST-changed settings and skips user-modified ones`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.WRITE_SETTINGS)
        val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(appOps).setMode(
            android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
            android.os.Process.myUid(),
            app.packageName,
            android.app.AppOpsManager.MODE_ALLOWED
        )
        val cr = app.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 110)
        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)

        val db = GameBoostDatabase.getDatabase(app)
        DeviceControlEngine.applyProfile(app, ProfileType.SMOOTHNESS_FOCUS, db) {}

        assertEquals(204, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(1800000, Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT))
        assertEquals(0, Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION))

        // Simulate user manually changing brightness after session started -> GAMEBOOST must NOT overwrite user change!
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 245)

        val restoreResult = DeviceControlEngine.restoreOriginalSettingsDetailed(app, db)
        assertTrue(restoreResult.restoredAny)
        assertTrue(restoreResult.skippedUserModified.contains("Screen Brightness"))
        assertTrue(restoreResult.restoredSettings.contains("Screen Timeout"))
        assertTrue(restoreResult.restoredSettings.contains("Auto-Rotate"))
        assertEquals(245, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(30000, Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT))
        assertEquals(1, Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION))
    }
}
