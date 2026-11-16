package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DiagnosticItem
import com.example.viewmodel.DiagnosticStatus
import kotlin.math.hypot

@Composable
fun ScanScreen(
    isScanning: Boolean,
    results: List<DiagnosticItem>,
    bottleneckAnalysis: BottleneckAnalysis?,
    checklist: List<PreGameChecklistItem>,
    storageData: StorageDiagnosticData?,
    displayData: DisplayDiagnosticData?,
    batteryData: BatteryDiagnosticData?,
    systemData: SystemCompatibilityData?,
    diagnosticReport: DeviceDiagnosticReport?,
    edgeResponseState: EdgeResponseState,
    lastSwipeResult: SwipeMetricResult?,
    onToggleChecklistItem: (String) -> Unit,
    onStartScan: () -> Unit,
    onRecordSwipe: (SwipeMetricResult) -> Unit,
    onRecordEdgeHit: (String) -> Unit,
    onResetEdgeTests: () -> Unit,
    onClearAppCache: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    LaunchedEffect(Unit) {
        onRefreshDiagnostics()
    }

    val adState by adManager.adState.collectAsState()
    val scanVisibility = AdVisibilityContext(
        location = AdScreenLocation.DEVICE_DIAGNOSTICS,
        isGamingSessionActive = isAutopilotActive,
        isAutopilotSessionActive = isAutopilotActive,
        isScanning = isScanning,
        thermalSeverity = thermalSeverity
    )
    val shouldShowScanBanner = !isPremium && remember(adState, scanVisibility) {
        adManager.canDisplayAds(scanVisibility)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("scan_screen_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (shouldShowScanBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = scanVisibility,
                        adManager = adManager,
                        testTagName = "device_diagnostics_ad_banner_slot"
                    )
                }
            }

            item {
                GameBoostScreenHeader(
                    title = "DIAGNOSTICS LAB",
                    subtitle = "Hardware telemetry, storage analyzer, touchscreen & system reports",
                    icon = Icons.Default.Security,
                    iconTint = NeonCyan,
                    onBackClick = onBackClick,
                    backButtonTestTag = "scan_back_button",
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRefreshDiagnostics)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )
            }

            // Styled Sub-Navigation Tabs
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = NeonCyan,
                        edgePadding = 6.dp,
                        divider = {}
                    ) {
                        listOf("Scanner", "Storage", "Touchscreen", "Display & Battery", "System & Report").forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selectedTab == idx) NeonCyan else TextSecondary,
                                        fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: HARDWARE SCANNER
                    item {
                        GameBoostHeroCard(
                            accentStart = GamingBlue,
                            accentEnd = NeonCyan,
                            contentPadding = PaddingValues(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    color = NeonCyan.copy(alpha = 0.12f),
                                    border = BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.4f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isScanning) {
                                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (isScanning) "Inspecting System Telemetry..." else if (results.isNotEmpty()) "Diagnostics Complete" else "Ready to Analyze Hardware",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Tests PowerManager thermal headroom, RAM pressure, and TCP socket jitter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                PrimaryGradientButton(
                                    text = if (isScanning) "Analyzing Hardware..." else "Start Performance Scan",
                                    icon = Icons.Default.Refresh,
                                    enabled = !isScanning,
                                    onClick = onStartScan,
                                    modifier = Modifier.testTag("scan_start_button")
                                )
                            }
                        }
                    }

                    if (bottleneckAnalysis != null) {
                        item {
                            val badgeColor = Color(bottleneckAnalysis.primaryBottleneck.badgeColorHex)
                            GameBoostSurfaceCard(
                                containerColor = DarkSurface,
                                borderColor = badgeColor.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Limiter Analysis: ${bottleneckAnalysis.primaryBottleneck.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = bottleneckAnalysis.technicalCause,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                HorizontalDivider(color = DarkSurfaceBorder)

                                Text(
                                    text = "Conditional Strategy",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonGreen
                                )
                                Text(
                                    text = bottleneckAnalysis.conditionalStrategy,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // Pre-Game Preparation Checklist Card
                    item {
                        GameBoostSurfaceCard(
                            containerColor = DarkSurface,
                            borderColor = DarkSurfaceBorder
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Checklist, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                                    Text(text = "Pre-Game Preparation Checklist", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                }

                                StatusPillBadge(
                                    text = "${checklist.count { it.isCompleted }}/${checklist.size}",
                                    color = NeonGreen,
                                    showDot = false
                                )
                            }

                            Text(
                                text = "Real device preparations that reduce thermal throttling and background frame drops:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )

                            checklist.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkSurfaceElevated,
                                    border = BorderStroke(1.dp, if (item.isCompleted) NeonGreen.copy(alpha = 0.35f) else DarkSurfaceBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onToggleChecklistItem(item.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = item.isCompleted,
                                            onCheckedChange = { onToggleChecklistItem(item.id) },
                                            colors = CheckboxDefaults.colors(checkedColor = NeonCyan, checkmarkColor = Color.Black)
                                        )
                                        Column(
                                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(text = item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            Text(text = item.whyItMayHelp, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = DarkSurfaceBorder)
                            Text(text = "Android System Shortcuts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "DND Setup" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                                    "Display (Hz)" to Settings.ACTION_DISPLAY_SETTINGS,
                                    "Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS
                                ).forEach { (label, action) ->
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(action))
                                            } catch (_: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = NeonCyan, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    if (results.isNotEmpty()) {
                        item {
                            SectionLabel(text = "Hardware Telemetry Findings", accentColor = NeonCyan)
                        }
                        items(results) { item ->
                            DiagnosticResultCard(item = item)
                        }
                    }
                }

                1 -> {
                    // TAB 1: STORAGE ANALYZER
                    item {
                        val storage = storageData
                        if (storage != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = DarkSurfaceBorder
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                            Text("Internal Storage Overview", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        }
                                        StatusPillBadge(
                                            text = "${storage.usagePercent}% Used",
                                            color = if (storage.usagePercent > 88) NeonOrange else NeonCyan,
                                            showDot = false
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { storage.usagePercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                        color = if (storage.usagePercent > 88) NeonOrange else NeonCyan,
                                        trackColor = DarkSurfaceBorder
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${storage.usedInternalGb} GB Used", style = TelemetryMonoStyle.copy(fontSize = 11.5.sp), color = TextSecondary)
                                        Text("${storage.availableInternalGb} GB Free", style = TelemetryMonoStyle.copy(fontSize = 11.5.sp), color = NeonGreen)
                                        Text("${storage.totalInternalGb} GB Total", style = TelemetryMonoStyle.copy(fontSize = 11.5.sp), color = TextSecondary)
                                    }
                                }

                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = DarkSurfaceBorder
                                ) {
                                    Text("GAMEBOOST Accessible Footprint", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

                                    SpecRow("Internal Cache", DeviceDiagnosticsEngine.formatBytes(storage.appInternalCacheBytes))
                                    SpecRow("External Cache", DeviceDiagnosticsEngine.formatBytes(storage.appExternalCacheBytes))
                                    SpecRow("App Database & Files", DeviceDiagnosticsEngine.formatBytes(storage.appFilesBytes))

                                    HorizontalDivider(color = DarkSurfaceBorder)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total App Footprint", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                                        Text(storage.totalAppFootprintFormatted, style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = NeonCyan)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onClearAppCache()
                                                Toast.makeText(context, "App cache cleared safely", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.18f)),
                                            border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Clear Cache", color = NeonRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                                                } catch (_: Exception) {
                                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Text("System Storage", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                if (storage.largestAccessibleFiles.isNotEmpty()) {
                                    SectionLabel(text = "Largest Accessible Files in Sandbox", accentColor = NeonCyan)
                                    storage.largestAccessibleFiles.forEach { file ->
                                        GameBoostSurfaceCard(
                                            containerColor = DarkSurfaceElevated,
                                            borderColor = DarkSurfaceBorder,
                                            contentPadding = PaddingValues(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(file.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(file.directoryCategory, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                                }
                                                Text(file.sizeFormatted, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = NeonCyan)
                                            }
                                        }
                                    }
                                }

                                GameBoostSurfaceCard(
                                    containerColor = DarkSurfaceElevated,
                                    borderColor = NeonGreen.copy(alpha = 0.25f),
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                        Text(text = storage.cleanupGuidance, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                        } else {
                            GameBoostLoadingSkeleton(itemCount = 3)
                        }
                    }
                }

                2 -> {
                    // TAB 2: TOUCHSCREEN TEST LAB
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionLabel(text = "Interactive Touchscreen Test Canvas", accentColor = NeonCyan)
                            Text(
                                "Test touch coordinates, screen-edge registration, and swipe velocity within GAMEBOOST.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            TouchTestCanvas(
                                edgeState = edgeResponseState,
                                onRecordSwipe = onRecordSwipe,
                                onRecordEdgeHit = onRecordEdgeHit
                            )

                            GameBoostSurfaceCard(
                                containerColor = DarkSurface,
                                borderColor = DarkSurfaceBorder
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Screen-Edge Response Verification", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    TextButton(onClick = onResetEdgeTests, modifier = Modifier.heightIn(min = 40.dp)) {
                                        Text("Reset", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EdgeStatusBadge(modifier = Modifier.weight(1f), label = "Left", passed = edgeResponseState.leftEdgeHit)
                                    EdgeStatusBadge(modifier = Modifier.weight(1f), label = "Right", passed = edgeResponseState.rightEdgeHit)
                                    EdgeStatusBadge(modifier = Modifier.weight(1f), label = "Top", passed = edgeResponseState.topEdgeHit)
                                    EdgeStatusBadge(modifier = Modifier.weight(1f), label = "Bottom", passed = edgeResponseState.bottomEdgeHit)
                                }

                                Text(
                                    text = if (edgeResponseState.allEdgesTested)
                                        "All 4 display edges respond to touch inputs. Edge gestures & sidebar triggers will operate reliably."
                                    else
                                        "Touch each glowing edge boundary inside the canvas above to confirm touch sensor registration up to the bezel.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (edgeResponseState.allEdgesTested) NeonGreen else TextSecondary
                                )
                            }

                            if (lastSwipeResult != null) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = NeonCyan.copy(alpha = 0.35f)
                                ) {
                                    Text("Last Measured Swipe Telemetry", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                                    SpecRow("Distance", "${lastSwipeResult.distancePx.toInt()} px")
                                    SpecRow("Duration", "${lastSwipeResult.durationMs} ms")
                                    SpecRow("Velocity", "${lastSwipeResult.velocityPxPerSec.toInt()} px/sec")
                                    SpecRow("Touch Event", if (lastSwipeResult.wasInterrupted) "Interrupted" else "Clean Event")
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: DISPLAY & BATTERY ANALYZER
                    item {
                        val display = displayData
                        val battery = batteryData

                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (display != null) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = DarkSurfaceBorder
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                            Text("Display Panel Telemetry", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        }
                                        StatusPillBadge(text = "${display.currentRefreshRateHz.toInt()} Hz Active", color = NeonGreen, showDot = false)
                                    }

                                    SpecRow("Resolution (Physical)", display.resolutionPx)
                                    SpecRow("Resolution (Layout)", display.resolutionDp)
                                    SpecRow("Density Scale / DPI", "${display.densityDpi} DPI (${display.densityCategory})")
                                    SpecRow("Color Gamut & HDR", display.hdrTypes)

                                    HorizontalDivider(color = DarkSurfaceBorder)

                                    Text("Supported Hardware Refresh Modes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        display.supportedModes.take(4).forEach { mode ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = DarkSurfaceElevated,
                                                border = BorderStroke(1.dp, DarkSurfaceBorder),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("${mode.refreshRateHz.toInt()} Hz", style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = NeonCyan)
                                                    Text("${mode.widthPx}p", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = DarkSurfaceElevated,
                                        border = BorderStroke(1.dp, DarkSurfaceBorder)
                                    ) {
                                        Text(
                                            text = display.educationalNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            } else {
                                GameBoostLoadingSkeleton(itemCount = 2)
                            }

                            if (battery != null) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = DarkSurfaceBorder
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                                            Text("Battery Telemetry", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        }
                                        StatusPillBadge(text = "${battery.levelPercent}%", color = NeonGreen, showDot = false)
                                    }

                                    SpecRow("Power State", "${battery.status} (${battery.powerSource})")
                                    val hasTemp = battery.temperatureC > 0f
                                    SpecRow("Sensor Temperature", if (hasTemp) "${battery.temperatureC}°C" else "N/A")
                                    SpecRow("Measured Voltage", if (battery.voltageVolts != null) "${battery.voltageVolts} V" else "Unavailable")
                                    SpecRow("Instantaneous Current", if (battery.currentMilliAmperes != null) "${battery.currentMilliAmperes} mA" else "Unsupported by OEM")
                                    SpecRow("Health Category", battery.healthCategory)

                                    HorizontalDivider(color = DarkSurfaceBorder)

                                    Text(text = battery.healthDisclaimer, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // TAB 4: SYSTEM COMPATIBILITY & EXPORTABLE REPORT
                    item {
                        val system = systemData
                        val report = diagnosticReport

                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (system != null) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = DarkSurfaceBorder
                                ) {
                                    Text("Android Platform Compatibility", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

                                    SpecRow("Android Version & SDK", "Android ${system.androidVersion} (API ${system.sdkInt})")
                                    SpecRow("Security Patch", system.securityPatch)
                                    SpecRow("PowerManager Thermal API", if (system.thermalStatusApiAvailable) "Supported" else "Unsupported")
                                    SpecRow("Thermal Headroom API", if (system.thermalHeadroomApiAvailable) "Supported (API 30+)" else "Unsupported")
                                    SpecRow("Overlay Permission", if (system.overlayPermissionGranted) "Granted" else "Requires Permission")

                                    if (!system.overlayPermissionGranted) {
                                        PrimaryGradientButton(
                                            text = "Grant Overlay Permission",
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                                } catch (_: Exception) {
                                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                                }
                                            }
                                        )
                                    }

                                    HorizontalDivider(color = DarkSurfaceBorder)
                                    Text(system.sandboxExplanations, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            } else {
                                GameBoostLoadingSkeleton(itemCount = 2)
                            }

                            if (report != null) {
                                GameBoostSurfaceCard(
                                    containerColor = DarkSurface,
                                    borderColor = NeonCyan.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Complete Diagnostic Report", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("GAMEBOOST Report", report.exportableText))
                                                    Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan)
                                            }
                                            IconButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, report.exportableText)
                                                        type = "text/plain"
                                                    }
                                                    try {
                                                        context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "No compatible share app found", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonCyan)
                                            }
                                        }
                                    }

                                    Text(report.storageSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(report.displaySummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(report.batterySummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(report.thermalSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                                    HorizontalDivider(color = DarkSurfaceBorder)

                                    Text("Recommended Actions", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                                    report.recommendedActions.forEach { act ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("•", color = NeonCyan)
                                            Text(act, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun EdgeStatusBadge(modifier: Modifier = Modifier, label: String, passed: Boolean) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (passed) NeonGreen.copy(alpha = 0.14f) else DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (passed) NeonGreen.copy(alpha = 0.45f) else DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (passed) NeonGreen else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (passed) NeonGreen else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TouchTestCanvas(
    edgeState: EdgeResponseState,
    onRecordSwipe: (SwipeMetricResult) -> Unit,
    onRecordEdgeHit: (String) -> Unit
) {
    var touchPositions by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var swipeStartTime by remember { mutableLongStateOf(0L) }
    var totalSwipeDist by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface)
            .border(
                1.5.dp,
                if (edgeState.allEdgesTested) NeonGreen else DarkSurfaceBorderBright,
                RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        swipeStartTime = System.currentTimeMillis()
                        totalSwipeDist = 0f
                        touchPositions = listOf(offset)

                        val edgeThreshold = 36f
                        if (offset.x <= edgeThreshold) onRecordEdgeHit("left")
                        if (offset.x >= size.width - edgeThreshold) onRecordEdgeHit("right")
                        if (offset.y <= edgeThreshold) onRecordEdgeHit("top")
                        if (offset.y >= size.height - edgeThreshold) onRecordEdgeHit("bottom")
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        touchPositions = listOf(change.position)
                        totalSwipeDist += hypot(dragAmount.x, dragAmount.y)

                        val edgeThreshold = 36f
                        if (change.position.x <= edgeThreshold) onRecordEdgeHit("left")
                        if (change.position.x >= size.width - edgeThreshold) onRecordEdgeHit("right")
                        if (change.position.y <= edgeThreshold) onRecordEdgeHit("top")
                        if (change.position.y >= size.height - edgeThreshold) onRecordEdgeHit("bottom")
                    },
                    onDragEnd = {
                        val duration = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                        val velocity = (totalSwipeDist / (duration.toFloat() / 1000f))
                        onRecordSwipe(
                            SwipeMetricResult(
                                distancePx = totalSwipeDist,
                                durationMs = duration,
                                velocityPxPerSec = velocity,
                                wasInterrupted = false
                            )
                        )
                        touchPositions = emptyList()
                    },
                    onDragCancel = {
                        val duration = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                        onRecordSwipe(
                            SwipeMetricResult(
                                distancePx = totalSwipeDist,
                                durationMs = duration,
                                velocityPxPerSec = 0f,
                                wasInterrupted = true
                            )
                        )
                        touchPositions = emptyList()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val edgeThickness = 24.dp.toPx()

            drawRect(
                color = if (edgeState.leftEdgeHit) NeonGreen.copy(alpha = 0.35f) else NeonCyan.copy(alpha = 0.14f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(edgeThickness, h)
            )
            drawRect(
                color = if (edgeState.rightEdgeHit) NeonGreen.copy(alpha = 0.35f) else NeonCyan.copy(alpha = 0.14f),
                topLeft = Offset(w - edgeThickness, 0f),
                size = androidx.compose.ui.geometry.Size(edgeThickness, h)
            )
            drawRect(
                color = if (edgeState.topEdgeHit) NeonGreen.copy(alpha = 0.35f) else NeonCyan.copy(alpha = 0.14f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w, edgeThickness)
            )
            drawRect(
                color = if (edgeState.bottomEdgeHit) NeonGreen.copy(alpha = 0.35f) else NeonCyan.copy(alpha = 0.14f),
                topLeft = Offset(0f, h - edgeThickness),
                size = androidx.compose.ui.geometry.Size(w, edgeThickness)
            )

            val gridStep = 40.dp.toPx()
            var x = gridStep
            while (x < w) {
                drawLine(color = DarkSurfaceBorder.copy(alpha = 0.45f), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 1f)
                x += gridStep
            }
            var y = gridStep
            while (y < h) {
                drawLine(color = DarkSurfaceBorder.copy(alpha = 0.45f), start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
                y += gridStep
            }

            touchPositions.forEach { pos ->
                drawCircle(color = NeonCyan.copy(alpha = 0.35f), radius = 48.dp.toPx(), center = pos)
                drawCircle(color = NeonCyan, radius = 24.dp.toPx(), center = pos, style = Stroke(width = 3.dp.toPx()))
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = pos)
                drawLine(color = NeonCyan, start = Offset(pos.x - 32.dp.toPx(), pos.y), end = Offset(pos.x + 32.dp.toPx(), pos.y), strokeWidth = 1.5f)
                drawLine(color = NeonCyan, start = Offset(pos.x, pos.y - 32.dp.toPx()), end = Offset(pos.x, pos.y + 32.dp.toPx()), strokeWidth = 1.5f)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkBackground.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = if (touchPositions.isNotEmpty())
                            "X: ${touchPositions[0].x.toInt()} px | Y: ${touchPositions[0].y.toInt()} px"
                        else
                            "Touch or drag across canvas to test",
                        style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                        color = NeonCyan
                    )
                    Text(
                        text = "Edges Registered: ${listOf(edgeState.leftEdgeHit, edgeState.rightEdgeHit, edgeState.topEdgeHit, edgeState.bottomEdgeHit).count { it }}/4",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (edgeState.allEdgesTested) NeonGreen else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticResultCard(item: DiagnosticItem) {
    val (color, icon) = when (item.status) {
        DiagnosticStatus.GOOD -> NeonGreen to Icons.Default.CheckCircle
        DiagnosticStatus.WARNING -> NeonOrange to Icons.Default.Warning
        DiagnosticStatus.CRITICAL -> NeonRed to Icons.Default.Error
    }

    GameBoostSurfaceCard(
        containerColor = DarkSurface,
        borderColor = color.copy(alpha = 0.35f),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
