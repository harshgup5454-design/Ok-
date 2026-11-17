package com.example.ui.screens

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ads.AdManager
import com.example.ads.AdScreenLocation
import com.example.ads.AdVisibilityContext
import com.example.ads.GoogleAdMobManager
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.smartgaming.AutopilotState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.SessionLaunchPrompt
import java.util.Locale

@Composable
fun DashboardScreen(
    stats: DeviceStats,
    networkStats: NetworkStats,
    bottleneckAnalysis: BottleneckAnalysis?,
    activeRecommendations: List<AdvisorRecommendation> = emptyList(),
    installedGames: List<GameProfile> = emptyList(),
    benchmarkSessions: List<BenchmarkSessionReport> = emptyList(),
    isBenchmarking: Boolean = false,
    isScanning: Boolean = false,
    benchmarkSeconds: Int = 0,
    autopilotState: AutopilotState = AutopilotState(),
    smartAimControlState: SmartAimControlState = SmartAimControlState(),
    protectionStatus: GamingProtectionStatus = GamingProtectionStatus(),
    sessionLaunchPrompt: SessionLaunchPrompt? = null,
    isPremium: Boolean = false,
    adManager: AdManager = GoogleAdMobManager,
    onToggleAutopilot: (Boolean) -> Unit = {},
    onSelectAutopilotProfile: (ProfileType) -> Unit = {},
    onSetSmartAimMode: (AimOptimizationMode) -> Unit = {},
    onLaunchGame: (GameProfile) -> Unit = {},
    onStartGamingSession: ((GameProfile?, String, ProfileType, Boolean, Boolean) -> Unit)? = null,
    onDismissSessionPrompt: () -> Unit = {},
    onOpenPermissionCenter: () -> Unit = {},
    onStartBenchmark: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onStopBenchmark: (Float?, Float?) -> Unit = { _, _ -> },
    onDismissRecommendation: (String) -> Unit = {},
    onResetDismissedRules: () -> Unit = {},
    onRunScanClick: () -> Unit,
    onOpenChecklistClick: () -> Unit,
    onNavigateToGamingSpace: () -> Unit = {},
    onNavigateToOverlay: () -> Unit = {},
    onNavigateToOverlaySettings: () -> Unit = {},
    onNavigateToNetwork: () -> Unit = {},
    onNavigateToBenchmark: () -> Unit = {},
    onNavigateToAim: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onNavigateToSmartGaming: () -> Unit = {},
    onNavigateToSessionHud: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adState by adManager.adState.collectAsState()
    val isOverlayRunning by GamingOverlayService.isServiceRunning.collectAsState()
    val lastOverlayError by GamingOverlayService.lastOverlayError.collectAsState()

    var hasOverlayPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    DisposableEffect(lifecycleOwner, sessionLaunchPrompt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                if (hasOverlayPerm && sessionLaunchPrompt is SessionLaunchPrompt.MissingOverlayPermission) {
                    onDismissSessionPrompt()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val defaultGame = remember(installedGames, autopilotState.targetGameTitle) {
        val installedMatch = installedGames.firstOrNull {
            it.name == autopilotState.targetGameTitle && it.isInstalled
        }?.name
        installedMatch
            ?: installedGames.firstOrNull { it.isInLibrary && it.isInstalled }?.name
            ?: autopilotState.targetGameTitle.takeIf { it.isNotBlank() && installedGames.isEmpty() }
            ?: "Global Gaming Mode"
    }
    var selectedGameName by remember(defaultGame) { mutableStateOf(defaultGame) }
    var isGameSelectorExpanded by remember { mutableStateOf(false) }
    val selectedProfile = remember(selectedGameName, installedGames) {
        if (selectedGameName == "Global Gaming Mode") null
        else installedGames.firstOrNull { it.name == selectedGameName }
    }

    val formattedSessionTime = remember(benchmarkSeconds) {
        val mins = benchmarkSeconds / 60
        val secs = benchmarkSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val homeBannerVisibility = AdVisibilityContext(
        location = AdScreenLocation.HOME_DASHBOARD,
        isGamingSessionActive = isBenchmarking || autopilotState.isSessionActive,
        isAutopilotSessionActive = autopilotState.isSessionActive,
        isBenchmarking = isBenchmarking,
        isScanning = isScanning,
        thermalSeverity = protectionStatus.overallSeverity
    )
    val shouldShowBannerAd = !isPremium && remember(adState, homeBannerVisibility) {
        adManager.canDisplayAds(homeBannerVisibility)
    }

    GameBoostAppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("dashboard_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. ZEROX BOOST BRAND HEADER
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.gameboost_logo),
                                contentDescription = "ZeroX Boost Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .border(
                                        1.5.dp,
                                        Brush.linearGradient(listOf(NeonPurpleBright, NeonPurple, NeonCyan)),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(4.dp)
                                    .testTag("dashboard_brand_logo")
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "ZeroX BOOST",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp
                                )
                                Text(
                                    text = stats.deviceModel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isPremium) NeonPurple.copy(alpha = 0.22f) else DarkSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPremium) NeonPurpleBright else DarkSurfaceBorderBright
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onNavigateToPremium() }
                                    .testTag("dashboard_pro_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Stars,
                                        contentDescription = "Pro",
                                        tint = NeonPurpleBright,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isPremium) "PRO" else "GO PRO",
                                        color = NeonPurpleBright,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StatusPillBadge(
                                text = "${stats.displayRefreshRate.toInt()} Hz",
                                color = NeonGreen
                            )
                        }
                    }
                }

                // 2. SYSTEM READINESS & THERMAL PROTECTION STRIP
                item {
                    DashboardProtectionBanner(
                        protectionStatus = protectionStatus,
                        onOpenSmartGaming = onNavigateToSmartGaming
                    )
                }

                // 3. LIVE HARDWARE TELEMETRY 2x2 BENTO GRID
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VisualGaugeCard(
                                modifier = Modifier.weight(1f),
                                title = "RAM Usage",
                                value = "${stats.memoryUsagePercent}%",
                                subtitle = "${stats.availableMemoryGb} GB Free",
                                icon = Icons.Default.Memory,
                                accentColor = if (stats.memoryUsagePercent > 85) NeonOrange else NeonCyan,
                                progress = stats.memoryUsagePercent / 100f
                            )
                            val hasBatteryTemp = stats.batteryTemperatureC > 0f
                            VisualGaugeCard(
                                modifier = Modifier.weight(1f),
                                title = "Battery & Temp",
                                value = if (hasBatteryTemp) "${stats.batteryTemperatureC}°C" else "N/A",
                                subtitle = "${stats.batteryLevelPercent}% • ${stats.powerSource}",
                                icon = Icons.Default.BatteryChargingFull,
                                accentColor = if (!hasBatteryTemp) TextSecondary else if (stats.batteryTemperatureC >= 39.5f) NeonRed else NeonGreen,
                                progress = if (hasBatteryTemp) (stats.batteryTemperatureC / 50f).coerceIn(0f, 1f) else 0.05f
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VisualGaugeCard(
                                modifier = Modifier.weight(1f),
                                title = "OS Thermal",
                                value = stats.powerManagerThermalStatus.take(10),
                                subtitle = if (stats.thermalHeadroom >= 0f) "Headroom ${(stats.thermalHeadroom * 100).toInt()}%" else "PowerManager",
                                icon = Icons.Default.Thermostat,
                                accentColor = if (stats.powerManagerThermalStatusCode >= 2) NeonRed else if (stats.powerManagerThermalStatusCode == 1) NeonOrange else NeonGreen,
                                progress = if (stats.powerManagerThermalStatusCode >= 2) 0.85f else 0.25f
                            )
                            VisualGaugeCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onNavigateToNetwork() },
                                title = "Network",
                                value = if (networkStats.pingMs < 0) "Not Tested" else "${networkStats.pingMs} ms",
                                subtitle = if (networkStats.isConnected) "${networkStats.connectionType} • ±${networkStats.jitterMs}ms" else "Offline",
                                icon = Icons.Default.Wifi,
                                accentColor = if (networkStats.jitterMs > 25) NeonOrange else NeonPurpleBright,
                                progress = if (networkStats.pingMs > 0) (networkStats.pingMs / 150f).coerceIn(0.1f, 1f) else 0.15f
                            )
                        }
                    }
                }

                // 4. ZEROX COMMAND DECK HERO (Game Target + Mode Segmented Control + Launch CTA)
                item {
                    GameBoostHeroCard(
                        accentStart = NeonPurple,
                        accentEnd = NeonCyan,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        // Top Row: Target selector + Autopilot Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkBackground.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isGameSelectorExpanded = !isGameSelectorExpanded }
                                    .testTag("dashboard_game_selector")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.SportsEsports,
                                            contentDescription = null,
                                            tint = NeonPurpleBright,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = selectedGameName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isGameSelectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Select Game",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (autopilotState.isAutopilotEnabled) NeonCyan.copy(alpha = 0.12f) else DarkBackground.copy(alpha = 0.72f),
                                border = BorderStroke(
                                    1.dp,
                                    if (autopilotState.isAutopilotEnabled) NeonCyan.copy(alpha = 0.45f) else DarkSurfaceBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AUTO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (autopilotState.isAutopilotEnabled) NeonCyan else TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(
                                        checked = autopilotState.isAutopilotEnabled,
                                        onCheckedChange = onToggleAutopilot,
                                        modifier = Modifier.testTag("dashboard_autopilot_switch"),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = NeonCyan,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = DarkSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isGameSelectorExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkBackground.copy(alpha = 0.85f))
                                    .border(1.dp, DarkSurfaceBorderBright, RoundedCornerShape(14.dp))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val gameOptions = (listOf("Global Gaming Mode") + installedGames.map { it.name }).distinct().take(6)
                                gameOptions.forEach { game ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedGameName = game
                                                isGameSelectorExpanded = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = game,
                                            color = if (selectedGameName == game) NeonPurpleBright else TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (selectedGameName == game) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (selectedGameName == game) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Clean Segmented Profile Switcher
                        ZeroXSegmentedControl(
                            options = listOf(
                                ProfileType.SMOOTHNESS_FOCUS to "Stability",
                                ProfileType.BALANCED to "Balanced",
                                ProfileType.BATTERY_FRIENDLY to "Battery"
                            ),
                            selected = autopilotState.activeProfile,
                            onSelect = onSelectAutopilotProfile,
                            accentColor = NeonPurpleBright
                        )

                        // Session Action CTA
                        if (!isBenchmarking) {
                            PrimaryGradientButton(
                                text = "START GAMING SESSION",
                                icon = Icons.Default.FlashOn,
                                startColor = GamingBlue,
                                endColor = NeonPurpleBright,
                                contentColor = Color.White,
                                onClick = {
                                    if (onStartGamingSession != null) {
                                        onStartGamingSession(
                                            selectedProfile,
                                            selectedGameName,
                                            autopilotState.activeProfile,
                                            false,
                                            false
                                        )
                                    } else {
                                        onStartBenchmark(
                                            "Session",
                                            selectedGameName,
                                            autopilotState.activeProfile.displayName,
                                            "PowerManager Thermal Telemetry",
                                            "Gaming session started from ZeroX Boost."
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("dashboard_start_gaming_button")
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = DarkBackground.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.65f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonGreen))
                                            Text("SESSION ACTIVE", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                                        }
                                        Text(formattedSessionTime, style = TelemetryMonoStyle.copy(fontSize = 20.sp), color = TextPrimary)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onNavigateToSessionHud,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                                        ) {
                                            Text("Open HUD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { onStopBenchmark(null, null) },
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .heightIn(min = 48.dp)
                                                .testTag("dashboard_stop_gaming_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f)),
                                            border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.7f))
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, tint = NeonRed, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("STOP GAMING SESSION", color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. QUICK GAMING UTILITIES DECK
                item {
                    SectionLabel("Quick Utilities", accentColor = NeonPurpleBright)
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardToolShortcutTile(
                            icon = Icons.Default.SportsEsports,
                            label = "Game Space",
                            tint = NeonCyan,
                            onClick = onNavigateToGamingSpace
                        )
                        DashboardToolShortcutTile(
                            icon = Icons.Default.Security,
                            label = "Diagnostics",
                            tint = NeonGreen,
                            onClick = onRunScanClick
                        )
                        DashboardToolShortcutTile(
                            icon = Icons.Default.Wifi,
                            label = "Network",
                            tint = NeonPurpleBright,
                            onClick = onNavigateToNetwork
                        )
                        DashboardToolShortcutTile(
                            icon = Icons.Default.Speed,
                            label = "Benchmarks",
                            tint = ElectricBlue,
                            onClick = onNavigateToBenchmark
                        )
                        DashboardToolShortcutTile(
                            icon = Icons.Default.GpsFixed,
                            label = "Aim & Control",
                            tint = NeonRed,
                            onClick = onNavigateToAim
                        )
                        DashboardToolShortcutTile(
                            icon = Icons.Default.AutoMode,
                            label = "Autopilot",
                            tint = NeonOrange,
                            onClick = onNavigateToSmartGaming
                        )
                    }
                }

                // 6. QUICK LAUNCH & RECENT ACTIVITY
                item {
                    DashboardQuickLaunchAndRecentCard(
                        installedGames = installedGames,
                        latestSession = benchmarkSessions.firstOrNull(),
                        onLaunchGame = onLaunchGame,
                        onNavigateToGamingSpace = onNavigateToGamingSpace,
                        onNavigateToReports = onNavigateToReports
                    )
                }

                // 7. FLOATING GAMING SIDEBAR QUICK CONTROL CARD
                item {
                    GameBoostSurfaceCard(
                        modifier = Modifier.testTag("dashboard_sidebar_quick_card"),
                        borderColor = if (isOverlayRunning) NeonCyan.copy(alpha = 0.50f) else DarkSurfaceBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isOverlayRunning) NeonCyan.copy(alpha = 0.16f) else DarkSurfaceElevated,
                                    border = BorderStroke(1.dp, if (isOverlayRunning) NeonCyan.copy(alpha = 0.35f) else DarkSurfaceBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = "Floating Sidebar",
                                            tint = if (isOverlayRunning) NeonCyan else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Floating Gaming Sidebar",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            isOverlayRunning -> "Active over games"
                                            hasOverlayPerm -> "Ready to launch"
                                            else -> "Overlay permission needed"
                                        },
                                        color = when {
                                            isOverlayRunning -> NeonGreen
                                            hasOverlayPerm -> TextSecondary
                                            else -> NeonOrange
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNavigateToOverlaySettings,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dashboard_sidebar_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Sidebar Settings",
                                    tint = NeonPurpleBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!hasOverlayPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        if (!PermissionManager.openPermissionSettings(context, AppPermissionType.OVERLAY)) {
                                            onNavigateToOverlaySettings()
                                        }
                                    } else if (isOverlayRunning) {
                                        GamingOverlayService.stopOverlay(context)
                                        GamingOverlayService.clearLastOverlayError()
                                    } else {
                                        GamingOverlayService.startOverlay(context)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_sidebar_toggle_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isOverlayRunning -> NeonRed.copy(alpha = 0.2f)
                                        hasOverlayPerm -> NeonCyan
                                        else -> NeonOrange
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = when {
                                        isOverlayRunning -> Icons.Default.Stop
                                        hasOverlayPerm -> Icons.Default.Layers
                                        else -> Icons.Default.LockOpen
                                    },
                                    contentDescription = null,
                                    tint = if (isOverlayRunning) NeonRed else Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isOverlayRunning -> "Stop Sidebar"
                                        hasOverlayPerm -> "Enable Sidebar"
                                        else -> "Grant Overlay"
                                    },
                                    color = if (isOverlayRunning) NeonRed else Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onNavigateToOverlay,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_hud_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!lastOverlayError.isNullOrBlank()) {
                            Text(
                                text = lastOverlayError.orEmpty(),
                                color = NeonOrange,
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("dashboard_overlay_error_text")
                            )
                        }
                    }
                }

                // 8. PERFORMANCE ADVISOR RECOMMENDATIONS
                if (activeRecommendations.isNotEmpty() || bottleneckAnalysis != null) {
                    item {
                        SectionLabel(
                            text = "Performance Advisor",
                            accentColor = NeonPurple,
                            trailingAction = {
                                TextButton(onClick = onOpenChecklistClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("Diagnostics", color = NeonCyan, fontSize = 11.sp)
                                }
                            }
                        )
                    }

                    item {
                        GameBoostSurfaceCard(containerColor = DarkSurfaceElevated) {
                            if (bottleneckAnalysis != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(bottleneckAnalysis.primaryBottleneck.badgeColorHex), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Limiter: ${bottleneckAnalysis.primaryBottleneck.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = bottleneckAnalysis.technicalCause,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            activeRecommendations.take(3).forEach { rec ->
                                HorizontalDivider(color = DarkSurfaceBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(rec.title, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(rec.measuredFact, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                                        Text("Action: ${rec.suggestedAction}", color = NeonGreen, fontSize = 10.5.sp, lineHeight = 14.sp)
                                    }
                                    IconButton(
                                        onClick = { onDismissRecommendation(rec.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss tip", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (activeRecommendations.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("No active bottlenecks flagged.", color = NeonGreen, fontSize = 12.sp)
                                    TextButton(onClick = onResetDismissedRules) {
                                        Text("Reset Tips", color = NeonCyan, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (shouldShowBannerAd) {
                AdMobAdaptiveBannerSlot(
                    visibilityContext = homeBannerVisibility,
                    adManager = adManager,
                    testTagName = "dashboard_ad_banner_slot",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    // Session Validation / Permission Guidance Dialogs
    when (sessionLaunchPrompt) {
        is SessionLaunchPrompt.MissingOverlayPermission -> {
            AlertDialog(
                onDismissRequest = onDismissSessionPrompt,
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("session_missing_overlay_dialog"),
                title = {
                    Text(
                        text = "Overlay Permission Needed",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ZeroX Boost needs 'Display over other apps' permission to automatically show the floating gaming handle above ${sessionLaunchPrompt.gameTitle}.",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                        TextButton(
                            onClick = {
                                onDismissSessionPrompt()
                                onOpenPermissionCenter()
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Open Permission Center", color = NeonCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            PermissionManager.openPermissionSettings(context, AppPermissionType.OVERLAY)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("session_prompt_grant_overlay")
                    ) {
                        Text("Open Overlay Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onStartGamingSession?.invoke(
                                sessionLaunchPrompt.gameProfile,
                                sessionLaunchPrompt.gameTitle,
                                sessionLaunchPrompt.profileType,
                                true,
                                false
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("session_prompt_continue_no_overlay")
                    ) {
                        Text("Start Without Overlay", color = TextSecondary)
                    }
                }
            )
        }
        is SessionLaunchPrompt.GameNotInstalled -> {
            AlertDialog(
                onDismissRequest = onDismissSessionPrompt,
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("session_game_not_installed_dialog"),
                title = {
                    Text(
                        text = "Game Not Installed",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = "${sessionLaunchPrompt.gameTitle} (${sessionLaunchPrompt.packageName}) is not installed on this device. You can select another game or start Global Gaming Mode with the floating overlay.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedGameName = "Global Gaming Mode"
                            onStartGamingSession?.invoke(
                                null,
                                "Global Gaming Mode",
                                sessionLaunchPrompt.profileType,
                                false,
                                true
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("session_prompt_start_global")
                    ) {
                        Text("Start Global Session", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissSessionPrompt,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
        is SessionLaunchPrompt.OverlayStartFailed -> {
            AlertDialog(
                onDismissRequest = onDismissSessionPrompt,
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("session_overlay_failed_dialog"),
                title = {
                    Text(
                        text = "Overlay Could Not Start",
                        color = NeonOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        text = "${sessionLaunchPrompt.message} Your gaming session and telemetry remain active.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismissSessionPrompt,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun DashboardToolShortcutTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .heightIn(min = 86.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.32f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
