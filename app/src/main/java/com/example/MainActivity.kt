package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ads.GoogleAdMobManager
import com.example.billing.GooglePlayBillingManager
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.ui.components.GamingSidebarContainer
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.GameBoostViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Profiles : Screen("space", "Games", Icons.Default.SportsEsports)
    object Tools : Screen("tools", "Tools", Icons.Default.Build)
    object Reports : Screen("reports", "Reports", Icons.Default.Assessment)
    object Premium : Screen("premium", "Pro", Icons.Default.Stars)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Sub-screens launched from Tools hub, Dashboard, Settings, or Overlay Sidebar
    object Scan : Screen("scan", "Scanner", Icons.Default.Security)
    object Network : Screen("network", "Network", Icons.Default.Wifi)
    object Aim : Screen("aim", "Aim Lab", Icons.Default.GpsFixed)
    object BenchmarkLab : Screen("lab", "Lab", Icons.Default.Speed)
    object DeviceControl : Screen("device_control", "Control Engine", Icons.Default.Tune)
    object SmartGaming : Screen("smart_gaming", "Smart Gaming", Icons.Default.Gamepad)
    object PermissionCenter : Screen("permission_center", "Permissions", Icons.Default.Security)
    object GamingSession : Screen("gaming_session", "Session", Icons.Default.FlashOn)
    object About : Screen("about", "About", Icons.Default.Info)
    object HelpFaq : Screen("help_faq", "Help", Icons.Default.HelpOutline)
}

class MainActivity : ComponentActivity() {
    private val pendingRouteState = mutableStateOf<String?>(null)
    private val pendingAimTabState = mutableIntStateOf(0)

    private fun handleDeepLinkIntent(intent: Intent?) {
        val target = intent?.getStringExtra(GamingOverlayService.EXTRA_TARGET_ROUTE)
        if (!target.isNullOrBlank()) {
            pendingAimTabState.intValue = intent.getIntExtra(GamingOverlayService.EXTRA_AIM_TAB, 0)
            pendingRouteState.value = target
            intent.removeExtra(GamingOverlayService.EXTRA_TARGET_ROUTE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)
        setContent {
            GameBoostTheme {
                val viewModel: GameBoostViewModel = viewModel()
                val navController = rememberNavController()

                val lifecycleOwner = LocalLifecycleOwner.current
                val pendingRoute by pendingRouteState
                val pendingAimTab by pendingAimTabState
                var showSplash by remember { mutableStateOf(!isRunningUnderRobolectric()) }
                var isOnboardingCompleted by remember {
                    mutableStateOf(OnboardingPreferences.isCompleted(applicationContext))
                }

                LaunchedEffect(Unit) {
                    GooglePlayBillingManager.initialize(applicationContext)
                    GoogleAdMobManager.startConsentAndSdkFlow(this@MainActivity)
                    viewModel.refreshStats(applicationContext)
                    viewModel.loadInstalledApps(applicationContext)
                    viewModel.refreshDeviceControlData(applicationContext)
                    viewModel.initSmartAimEngine(applicationContext)
                }

                LaunchedEffect(pendingRoute, isOnboardingCompleted) {
                    if (!isOnboardingCompleted) return@LaunchedEffect
                    val target = pendingRoute ?: return@LaunchedEffect
                    val destination = when (target) {
                        GamingOverlayService.ROUTE_NETWORK -> Screen.Network.route
                        GamingOverlayService.ROUTE_AIM -> Screen.Aim.route
                        else -> target
                    }
                    navController.navigate(destination) {
                        launchSingleTop = true
                    }
                    pendingRouteState.value = null
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                GamingOverlayService.setAppForeground(true)
                                GooglePlayBillingManager.onAppForegrounded()
                                viewModel.refreshStats(applicationContext)
                                viewModel.refreshDeviceControlData(applicationContext)
                                viewModel.initSmartAimEngine(applicationContext)
                                val online = GoogleAdMobManager.refreshNetworkStatus(applicationContext)
                                if (online && !GoogleAdMobManager.adState.value.canRequestAds) {
                                    GoogleAdMobManager.startConsentAndSdkFlow(this@MainActivity)
                                }
                                if (GamingOverlayService.consumePendingAimTrainerOnReturn()) {
                                    pendingAimTabState.intValue = GamingOverlayService.AIM_TAB_TRAINER
                                    pendingRouteState.value = GamingOverlayService.ROUTE_AIM
                                }
                            }
                            Lifecycle.Event.ON_PAUSE -> {
                                GamingOverlayService.setAppForeground(false)
                                viewModel.stopSmartAimSensors()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        GamingOverlayService.setAppForeground(false)
                        viewModel.stopSmartAimSensors()
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Clean 5-item bottom bar: Home, Games, Tools, Reports, Settings (Pro accessible from Home, Tools & Settings)
                val bottomBarItems = listOf(
                    Screen.Dashboard,
                    Screen.Profiles,
                    Screen.Tools,
                    Screen.Reports,
                    Screen.Settings
                )

                var isSidebarOpen by remember { mutableStateOf(false) }
                val deviceStats by viewModel.deviceStats.collectAsState()
                val networkStats by viewModel.networkStats.collectAsState()
                val gameProfiles by viewModel.gameProfiles.collectAsState()
                val installedApps by viewModel.installedApps.collectAsState()
                val isLoadingApps by viewModel.isLoadingApps.collectAsState()
                val savedProfiles by viewModel.savedProfiles.collectAsState()
                val calibrationHistory by viewModel.calibrationHistory.collectAsState()
                val trainingHistory by viewModel.trainingHistory.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val diagnosticResults by viewModel.diagnosticResults.collectAsState()
                val bottleneckAnalysis by viewModel.bottleneckAnalysis.collectAsState()
                val preGameChecklist by viewModel.preGameChecklist.collectAsState()
                val isBenchmarking by viewModel.isBenchmarking.collectAsState()
                val benchmarkSeconds by viewModel.benchmarkSeconds.collectAsState()
                val isSpeedTesting by viewModel.isSpeedTesting.collectAsState()
                val speedTestProgress by viewModel.speedTestProgress.collectAsState()
                val networkHistory by viewModel.networkHistory.collectAsState()
                val benchmarkSessions by viewModel.benchmarkSessions.collectAsState()
                val benchmarkLabResults by viewModel.benchmarkLabResults.collectAsState()
                val isLabRunning by viewModel.isLabRunning.collectAsState()
                val labProgress by viewModel.labProgress.collectAsState()
                val activeLabWorkload by viewModel.activeLabWorkload.collectAsState()
                val labTelemetryTemp by viewModel.labTelemetryTemp.collectAsState()
                val labTelemetryThermal by viewModel.labTelemetryThermal.collectAsState()
                val storageData by viewModel.storageData.collectAsState()
                val displayData by viewModel.displayData.collectAsState()
                val batteryData by viewModel.batteryData.collectAsState()
                val systemData by viewModel.systemData.collectAsState()
                val diagnosticReport by viewModel.diagnosticReport.collectAsState()
                val edgeResponseState by viewModel.edgeResponseState.collectAsState()
                val lastSwipeResult by viewModel.lastSwipeResult.collectAsState()
                val activeRecommendations by viewModel.activeRecommendations.collectAsState()
                val deviceCapabilities by viewModel.deviceCapabilities.collectAsState()
                val currentControlState by viewModel.currentControlState.collectAsState()
                val savedSystemState by viewModel.savedSystemState.collectAsState()
                val profileApplyMessage by viewModel.profileApplyMessage.collectAsState()
                val smartAimControlState by viewModel.smartAimControlState.collectAsState()
                val gyroscopeState by viewModel.gyroscopeState.collectAsState()
                val aimDeviceSpecs by viewModel.aimDeviceSpecs.collectAsState()
                val autopilotState by viewModel.autopilotState.collectAsState()
                val backgroundApps by viewModel.backgroundApps.collectAsState()
                val protectionStatus by viewModel.protectionStatus.collectAsState()
                val overlayConfig by viewModel.overlayConfig.collectAsState()
                val sessionLaunchPrompt by viewModel.sessionLaunchPrompt.collectAsState()
                val entitlement by MembershipManager.entitlement.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.refreshUsageAccessStatus(applicationContext)
                }

                val activeProfileShort = when (autopilotState.activeProfile) {
                    ProfileType.SMOOTHNESS_FOCUS -> "Stability"
                    ProfileType.BATTERY_FRIENDLY -> "Battery"
                    ProfileType.BALANCED -> "Balanced"
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isOnboardingCompleted) {
                        PermissionSetupScreen(
                            onFinishSetup = { isOnboardingCompleted = true },
                            isOnboardingMode = true
                        )
                    } else {
                        GamingSidebarContainer(
                            isOpen = isSidebarOpen,
                            onOpen = { isSidebarOpen = true },
                            onClose = { isSidebarOpen = false },
                            deviceStats = deviceStats,
                            networkStats = networkStats,
                            overlayConfig = overlayConfig,
                            autopilotStatusLabel = when {
                                autopilotState.isSessionActive -> "Autopilot: ${autopilotState.activeProfile.displayName}"
                                autopilotState.isAutopilotEnabled -> "Autopilot Armed"
                                else -> "Manual Control"
                            },
                            targetGameTitle = autopilotState.targetGameTitle,
                            activeProfileLabel = activeProfileShort,
                            protectionAlertHeadline = if (protectionStatus.overallSeverity != ProtectionSeverity.SAFE) protectionStatus.headline else null,
                            onSelectProfile = { mode ->
                                val target = when (mode) {
                                    "Stability" -> ProfileType.SMOOTHNESS_FOCUS
                                    "Battery" -> ProfileType.BATTERY_FRIENDLY
                                    else -> ProfileType.BALANCED
                                }
                                viewModel.setAutopilotProfile(target)
                            },
                            onRefreshPing = { viewModel.testNetworkPing() },
                            onOpenNetworkTools = {
                                isSidebarOpen = false
                                navController.navigate(Screen.Network.route) { launchSingleTop = true }
                            },
                            onOpenSensitivityCalc = {
                                isSidebarOpen = false
                                pendingAimTabState.intValue = GamingOverlayService.AIM_TAB_CALCULATOR
                                navController.navigate(Screen.Aim.route) { launchSingleTop = true }
                            },
                            onRequestAimTrainer = {
                                isSidebarOpen = false
                                pendingAimTabState.intValue = GamingOverlayService.AIM_TAB_TRAINER
                                navController.navigate(Screen.Aim.route) { launchSingleTop = true }
                            },
                            onPersistAppearance = { accent, opacity, compact ->
                                viewModel.updateOverlayConfig(applicationContext) {
                                    it.copy(accentTheme = accent, panelOpacity = opacity, defaultCompactMode = compact)
                                }
                            }
                        ) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = DarkBackground,
                                bottomBar = {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            NeonPurple.copy(alpha = 0.55f),
                                                            NeonCyan.copy(alpha = 0.55f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )
                                        NavigationBar(
                                            containerColor = Color(0xFF080712),
                                            contentColor = NeonPurpleBright,
                                            tonalElevation = 0.dp
                                        ) {
                                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                                            val currentRoute = navBackStackEntry?.destination?.route

                                            bottomBarItems.forEach { screen ->
                                                val isSelected = currentRoute == screen.route
                                                NavigationBarItem(
                                                    modifier = Modifier.testTag("nav_tab_${screen.route}"),
                                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                                    label = {
                                                        Text(
                                                            text = screen.title,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (currentRoute != screen.route) {
                                                            navController.navigate(screen.route) {
                                                                popUpTo(navController.graph.findStartDestination().id) {
                                                                    saveState = true
                                                                }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                    },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = NeonPurpleBright,
                                                        selectedTextColor = TextPrimary,
                                                        unselectedIconColor = TextMuted,
                                                        unselectedTextColor = TextMuted,
                                                        indicatorColor = NeonPurple.copy(alpha = 0.22f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(DarkBackground)
                                        .padding(innerPadding),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = Screen.Dashboard.route,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .widthIn(max = 720.dp)
                                    ) {
                                    composable(Screen.Dashboard.route) {
                                        DashboardScreen(
                                            stats = deviceStats,
                                            networkStats = networkStats,
                                            bottleneckAnalysis = bottleneckAnalysis,
                                            activeRecommendations = activeRecommendations,
                                            installedGames = gameProfiles,
                                            benchmarkSessions = benchmarkSessions,
                                            isBenchmarking = isBenchmarking,
                                            isScanning = isScanning,
                                            benchmarkSeconds = benchmarkSeconds,
                                            autopilotState = autopilotState,
                                            smartAimControlState = smartAimControlState,
                                            protectionStatus = protectionStatus,
                                            sessionLaunchPrompt = sessionLaunchPrompt,
                                            isPremium = entitlement.isPremium,
                                            onToggleAutopilot = { enabled ->
                                                viewModel.setAutopilotMasterEnabled(enabled)
                                            },
                                            onSelectAutopilotProfile = { profile ->
                                                viewModel.setAutopilotProfile(profile)
                                            },
                                            onSetSmartAimMode = { mode ->
                                                viewModel.setSmartAimMode(mode)
                                            },
                                            onLaunchGame = { game ->
                                                viewModel.launchGame(applicationContext, game, true)
                                            },
                                            onStartGamingSession = { selectedProfile, gameTitle, profileType, allowWithoutOverlay, allowWithoutLaunch ->
                                                viewModel.startGamingSessionWithOverlay(
                                                    context = applicationContext,
                                                    selectedProfile = selectedProfile,
                                                    selectedGameTitle = gameTitle,
                                                    profileType = profileType,
                                                    allowWithoutOverlay = allowWithoutOverlay,
                                                    allowWithoutGameLaunch = allowWithoutLaunch
                                                )
                                            },
                                            onDismissSessionPrompt = {
                                                viewModel.dismissSessionLaunchPrompt()
                                            },
                                            onOpenPermissionCenter = {
                                                navController.navigate(Screen.PermissionCenter.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onStartBenchmark = { label, game, graphics, fpsSource, notes ->
                                                viewModel.startBenchmarkSession(
                                                    context = applicationContext,
                                                    label = label,
                                                    gameTitle = game,
                                                    graphicsSettings = graphics,
                                                    fpsSource = fpsSource,
                                                    testNotes = notes
                                                )
                                            },
                                            onStopBenchmark = { avgFps, minFps ->
                                                viewModel.stopBenchmarkSession(applicationContext, avgFps, minFps)
                                            },
                                            onDismissRecommendation = { ruleId -> viewModel.dismissAdvisorRecommendation(ruleId) },
                                            onResetDismissedRules = { viewModel.resetDismissedAdvisorRules() },
                                            onRunScanClick = {
                                                navController.navigate(Screen.Scan.route)
                                                viewModel.runDiagnosticScan(applicationContext)
                                            },
                                            onOpenChecklistClick = {
                                                navController.navigate(Screen.Scan.route)
                                            },
                                            onNavigateToGamingSpace = {
                                                navController.navigate(Screen.Profiles.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onNavigateToOverlay = {
                                                isSidebarOpen = true
                                            },
                                            onNavigateToOverlaySettings = {
                                                navController.navigate(Screen.Settings.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onNavigateToNetwork = {
                                                navController.navigate(Screen.Network.route)
                                            },
                                            onNavigateToBenchmark = {
                                                navController.navigate(Screen.BenchmarkLab.route)
                                            },
                                            onNavigateToAim = {
                                                pendingAimTabState.intValue = 0
                                                navController.navigate(Screen.Aim.route)
                                            },
                                            onNavigateToReports = {
                                                navController.navigate(Screen.Reports.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onNavigateToPremium = {
                                                navController.navigate(Screen.Premium.route)
                                            },
                                            onNavigateToSmartGaming = {
                                                navController.navigate(Screen.SmartGaming.route)
                                            },
                                            onNavigateToSessionHud = {
                                                navController.navigate(Screen.GamingSession.route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    }
                                    composable(Screen.GamingSession.route) {
                                        GamingSessionScreen(
                                            stats = deviceStats,
                                            networkStats = networkStats,
                                            autopilotState = autopilotState,
                                            protectionStatus = protectionStatus,
                                            isBenchmarking = isBenchmarking,
                                            benchmarkSeconds = benchmarkSeconds,
                                            onSelectProfile = { profile ->
                                                viewModel.setAutopilotProfile(profile)
                                            },
                                            onStartSession = {
                                                viewModel.startBenchmarkSession(
                                                    context = applicationContext,
                                                    label = "Live Session",
                                                    gameTitle = autopilotState.targetGameTitle.ifBlank { "Global Gaming Mode" },
                                                    graphicsSettings = autopilotState.activeProfile.displayName,
                                                    fpsSource = "PowerManager Thermal Telemetry",
                                                    testNotes = "Started from Gaming Session HUD"
                                                )
                                            },
                                            onStopAndSaveSession = { avgFps, minFps ->
                                                viewModel.stopBenchmarkSession(applicationContext, avgFps, minFps)
                                            },
                                            onRefreshPing = { viewModel.testNetworkPing() },
                                            onOpenOverlayPreview = { isSidebarOpen = true },
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Tools.route) {
                                        ToolsHubScreen(
                                            isPremium = entitlement.isPremium,
                                            isGamingSessionActive = isBenchmarking || autopilotState.isSessionActive,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                                            onNavigateToNetwork = { navController.navigate(Screen.Network.route) },
                                            onNavigateToAim = {
                                                pendingAimTabState.intValue = 0
                                                navController.navigate(Screen.Aim.route)
                                            },
                                            onNavigateToLab = { navController.navigate(Screen.BenchmarkLab.route) },
                                            onNavigateToDeviceControl = { navController.navigate(Screen.DeviceControl.route) },
                                            onNavigateToSmartGaming = { navController.navigate(Screen.SmartGaming.route) },
                                            onNavigateToPremium = { navController.navigate(Screen.Premium.route) }
                                        )
                                    }
                                    composable(Screen.DeviceControl.route) {
                                        DeviceControlScreen(
                                            capabilities = deviceCapabilities,
                                            controlState = currentControlState,
                                            savedSystemState = savedSystemState,
                                            profileApplyMessage = profileApplyMessage,
                                            onRefreshCapabilities = { ctx -> viewModel.refreshDeviceControlData(ctx) },
                                            onApplyProfile = { ctx, profileType, onPermReq ->
                                                viewModel.applyOptimizationProfile(ctx, profileType, onPermReq)
                                            },
                                            onRestoreOriginals = { ctx -> viewModel.restoreOriginalSystemSettings(ctx) },
                                            onUpdateBrightness = { ctx, mode, level -> viewModel.updateBrightness(ctx, mode, level) },
                                            onUpdateTimeout = { ctx, timeoutMs -> viewModel.updateScreenTimeout(ctx, timeoutMs) },
                                            onUpdateAutoRotate = { ctx, rotate -> viewModel.updateAutoRotate(ctx, rotate) },
                                            onUpdateDndFilter = { ctx, filter -> viewModel.updateDndFilter(ctx, filter) },
                                            onLaunchOemGamingSpace = { ctx -> viewModel.launchOemGamingSpace(ctx) },
                                            onClearMessage = { viewModel.clearProfileApplyMessage() },
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Premium.route) {
                                        PremiumScreen(
                                            billingManager = GooglePlayBillingManager,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.SmartGaming.route) {
                                        var autopilotPermFeedback by remember { mutableStateOf<String?>(null) }
                                        SmartGamingDashboard(
                                            deviceStats = deviceStats,
                                            autopilotState = autopilotState,
                                            protectionStatus = protectionStatus,
                                            backgroundApps = backgroundApps,
                                            gameProfiles = gameProfiles,
                                            permissionActionBanner = autopilotPermFeedback,
                                            onDismissPermissionBanner = { autopilotPermFeedback = null },
                                            onToggleAutopilotMaster = { enabled ->
                                                viewModel.setAutopilotMasterEnabled(enabled)
                                            },
                                            onSelectProfile = { profile ->
                                                viewModel.setAutopilotProfile(profile)
                                            },
                                            onSelectTargetGame = { game ->
                                                viewModel.selectAutopilotGame(game)
                                            },
                                            onStartSession = { profile ->
                                                autopilotPermFeedback = null
                                                viewModel.startAutopilotSession(applicationContext, profile) { missingPerm ->
                                                    val friendly = when (missingPerm) {
                                                        "android.permission.WRITE_SETTINGS" -> "Modify System Settings"
                                                        "android.permission.ACCESS_NOTIFICATION_POLICY" -> "Do Not Disturb Access"
                                                        else -> missingPerm
                                                    }
                                                    autopilotPermFeedback = "Optional permission '$friendly' not granted — remaining authorized adjustments applied. Expand Official Android Permissions Status below to enable."
                                                }
                                            },
                                            onStopSession = {
                                                autopilotPermFeedback = null
                                                viewModel.stopAutopilotSession(applicationContext)
                                            },
                                            onRefreshBackgroundApps = { ctx ->
                                                viewModel.refreshBackgroundApps(ctx)
                                            },
                                            onRefreshUsageStatus = { ctx ->
                                                viewModel.refreshUsageAccessStatus(ctx)
                                            },
                                            onOpenAppSettings = { ctx, pkg ->
                                                viewModel.openAppDetailsSettings(ctx, pkg)
                                            },
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Scan.route) {
                                        ScanScreen(
                                            isScanning = isScanning,
                                            results = diagnosticResults,
                                            bottleneckAnalysis = bottleneckAnalysis,
                                            checklist = preGameChecklist,
                                            storageData = storageData,
                                            displayData = displayData,
                                            batteryData = batteryData,
                                            systemData = systemData,
                                            diagnosticReport = diagnosticReport,
                                            edgeResponseState = edgeResponseState,
                                            lastSwipeResult = lastSwipeResult,
                                            onToggleChecklistItem = { id -> viewModel.toggleChecklistItem(id) },
                                            onStartScan = { viewModel.runDiagnosticScan(applicationContext) },
                                            onRecordSwipe = { swipe -> viewModel.recordSwipeMetric(swipe) },
                                            onRecordEdgeHit = { edge -> viewModel.recordEdgeHit(edge) },
                                            onResetEdgeTests = { viewModel.resetEdgeTests() },
                                            onClearAppCache = { viewModel.clearAppCache(applicationContext) },
                                            onRefreshDiagnostics = { viewModel.refreshDiagnostics(applicationContext) },
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Network.route) {
                                        NetworkScreen(
                                            stats = networkStats,
                                            isSpeedTesting = isSpeedTesting,
                                            speedTestProgress = speedTestProgress,
                                            networkHistory = networkHistory,
                                            onTestPing = { viewModel.testNetworkPing() },
                                            onStartSpeedTest = { viewModel.startSpeedTest() },
                                            onCancelSpeedTest = { viewModel.cancelSpeedTest() },
                                            onClearHistory = { viewModel.clearNetworkHistory() },
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Profiles.route) {
                                        GamingSpaceScreen(
                                            profiles = gameProfiles,
                                            deviceStats = deviceStats,
                                            isBenchmarking = isBenchmarking,
                                            benchmarkSeconds = benchmarkSeconds,
                                            benchmarkSessions = benchmarkSessions,
                                            installedApps = installedApps,
                                            isLoadingApps = isLoadingApps,
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            onRefreshApps = { viewModel.loadInstalledApps(applicationContext) },
                                            onAddGameToVault = { name, pkg -> viewModel.addGameToVault(applicationContext, name, pkg) },
                                            onRemoveGameFromVault = { id -> viewModel.removeGameFromVault(id) },
                                            onToggleFavorite = { id -> viewModel.toggleGameFavorite(id) },
                                            onUpdateFullProfile = { updated -> viewModel.updateFullGameProfile(updated) },
                                            onRestorePreviousProfile = { id -> viewModel.restorePreviousGameProfile(id) },
                                            onDeleteProfilePermanently = { id -> viewModel.deleteGameProfilePermanently(id) },
                                            onLaunchGame = { profile, autoTrack -> viewModel.launchGame(applicationContext, profile, autoTrack) },
                                            onStopSession = { viewModel.stopBenchmarkSession(applicationContext) },
                                            onOpenSidebarPreview = { isSidebarOpen = true }
                                        )
                                    }
                                    composable(Screen.Aim.route) {
                                        AimIntelligenceScreen(
                                            savedProfiles = savedProfiles,
                                            trainingHistory = trainingHistory,
                                            calibrationHistory = calibrationHistory,
                                            onSaveProfile = { profile -> viewModel.saveSensitivityProfile(profile) },
                                            onDeleteProfile = { id -> viewModel.deleteSensitivityProfile(id) },
                                            onDuplicateProfile = { profile -> viewModel.duplicateSensitivityProfile(profile) },
                                            onSaveCalibration = { attempt -> viewModel.saveCalibrationAttempt(attempt) },
                                            onSaveTrainingSession = { stats -> viewModel.saveTrainingSession(stats) },
                                            smartAimControlState = smartAimControlState,
                                            gyroscopeState = gyroscopeState,
                                            deviceSpecs = aimDeviceSpecs,
                                            onMasterSwitchChanged = { enabled -> viewModel.setSmartAimMasterEnabled(enabled) },
                                            onModeSelected = { mode -> viewModel.setSmartAimMode(mode) },
                                            onGameSelected = { game -> viewModel.setSmartAimGame(game) },
                                            onApplyPreset = { game, mode -> viewModel.applyAimPreset(game, mode) },
                                            onCalibrateGyro = { viewModel.startGyroCalibration() },
                                            onSelectSavedProfile = { profile -> viewModel.selectSensitivityProfile(profile) },
                                            onExportProfilesJson = { viewModel.exportSensitivityProfilesJson() },
                                            onImportProfilesJson = { raw -> viewModel.importSensitivityProfilesJson(raw) },
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            initialTab = pendingAimTab,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.BenchmarkLab.route) {
                                        BenchmarkLabScreen(
                                            labResults = benchmarkLabResults,
                                            isLabRunning = isLabRunning,
                                            labProgress = labProgress,
                                            activeWorkload = activeLabWorkload,
                                            telemetryTemp = labTelemetryTemp,
                                            telemetryThermal = labTelemetryThermal,
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            thermalSeverity = protectionStatus.overallSeverity,
                                            onRunWorkload = { ctx, workload -> viewModel.runBenchmarkLabWorkload(ctx, workload) },
                                            onCancelLab = { viewModel.cancelBenchmarkLab() },
                                            onClearHistory = { viewModel.clearBenchmarkLabHistory() },
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Reports.route) {
                                        ReportsScreen(
                                            stats = deviceStats,
                                            isBenchmarking = isBenchmarking,
                                            benchmarkSeconds = benchmarkSeconds,
                                            benchmarkSessions = benchmarkSessions,
                                            isPremium = entitlement.isPremium,
                                            isAutopilotActive = autopilotState.isSessionActive,
                                            isScanning = isScanning,
                                            protectionStatus = protectionStatus,
                                            onStartBenchmark = { label, game, graphics, fpsSource, notes ->
                                                viewModel.startBenchmarkSession(
                                                    context = applicationContext,
                                                    label = label,
                                                    gameTitle = game,
                                                    graphicsSettings = graphics,
                                                    fpsSource = fpsSource,
                                                    testNotes = notes
                                                )
                                            },
                                            onStopBenchmark = { avgFps, minFps ->
                                                viewModel.stopBenchmarkSession(applicationContext, avgFps, minFps)
                                            },
                                            onNavigateToPremium = {
                                                navController.navigate(Screen.Premium.route)
                                            }
                                        )
                                    }
                                    composable(Screen.Settings.route) {
                                        SettingsScreen(
                                            overlayConfig = overlayConfig,
                                            onUpdateOverlayConfig = { transform ->
                                                viewModel.updateOverlayConfig(applicationContext, transform)
                                            },
                                            onNavigateToPremium = {
                                                navController.navigate(Screen.Premium.route)
                                            },
                                            onOpenPermissionCenter = {
                                                navController.navigate(Screen.PermissionCenter.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onOpenAbout = {
                                                navController.navigate(Screen.About.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onOpenHelpFaq = {
                                                navController.navigate(Screen.HelpFaq.route) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    }
                                    composable(Screen.PermissionCenter.route) {
                                        PermissionSetupScreen(
                                            onFinishSetup = { navController.popBackStack() },
                                            isOnboardingMode = false
                                        )
                                    }
                                    composable(Screen.About.route) {
                                        AboutGameBoostScreen(
                                            onBackClick = { navController.popBackStack() },
                                            onOpenHelpFaq = {
                                                navController.navigate(Screen.HelpFaq.route) { launchSingleTop = true }
                                            },
                                            onOpenPermissionCenter = {
                                                navController.navigate(Screen.PermissionCenter.route) { launchSingleTop = true }
                                            }
                                        )
                                    }
                                    composable(Screen.HelpFaq.route) {
                                        HelpFaqScreen(
                                            onBackClick = { navController.popBackStack() },
                                            onOpenPermissionCenter = {
                                                navController.navigate(Screen.PermissionCenter.route) { launchSingleTop = true }
                                            }
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    SplashTransitionOverlay(
                        visible = showSplash,
                        onDismiss = { showSplash = false }
                    )
                }
            }
        }
    }
}
