package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.AdManager
import com.example.ads.AdScreenLocation
import com.example.ads.AdVisibilityContext
import com.example.ads.GoogleAdMobManager
import com.example.data.BenchmarkSessionReport
import com.example.data.DeviceStats
import com.example.data.GamingProtectionStatus
import com.example.data.MembershipManager
import com.example.data.SessionAnalyticsAggregator
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun ReportsScreen(
    stats: DeviceStats,
    isBenchmarking: Boolean,
    benchmarkSeconds: Int,
    benchmarkSessions: List<BenchmarkSessionReport>,
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    isScanning: Boolean = false,
    protectionStatus: GamingProtectionStatus = GamingProtectionStatus(),
    adManager: AdManager = GoogleAdMobManager,
    onStartBenchmark: (String, String, String, String, String) -> Unit,
    onStopBenchmark: (Float?, Float?) -> Unit,
    onNavigateToPremium: () -> Unit = {}
) {
    var sessionLabel by remember { mutableStateOf("") }
    val selectedGame by remember { mutableStateOf("BGMI / PUBG Mobile") }
    var graphicsSettings by remember { mutableStateOf("Smooth + Ultra (Shadows OFF)") }
    var selectedFpsSource by remember { mutableStateOf("None (FPS Unavailable)") }
    val userNotes by remember { mutableStateOf("") }

    var showStopDialog by remember { mutableStateOf(false) }
    var inputAvgFpsText by remember { mutableStateOf("") }
    var inputMinFpsText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val entitlement by MembershipManager.entitlement.collectAsState()
    val effectiveIsPremium = isPremium || entitlement.isPremium
    val maxHistoryCount = if (effectiveIsPremium) 50 else 10
    val visibleSessions = remember(benchmarkSessions, maxHistoryCount) {
        benchmarkSessions.take(maxHistoryCount)
    }
    var exportedFeedback by remember { mutableStateOf<String?>(null) }

    val sessionSummary = remember(benchmarkSessions) {
        SessionAnalyticsAggregator.summarize(benchmarkSessions)
    }

    val nativeAdVisibility = AdVisibilityContext(
        location = AdScreenLocation.REPORTS_NATIVE_SPONSORED,
        isGamingSessionActive = isBenchmarking || isAutopilotActive,
        isAutopilotSessionActive = isAutopilotActive,
        isBenchmarking = isBenchmarking,
        isScanning = isScanning,
        hasWarningOrErrorDialog = showStopDialog,
        thermalSeverity = protectionStatus.overallSeverity
    )
    val reportsBannerVisibility = nativeAdVisibility.copy(location = AdScreenLocation.REPORTS_BANNER)
    val shouldShowSponsoredCard = !effectiveIsPremium && adManager.canDisplayAds(nativeAdVisibility)
    val shouldShowReportsBanner = !effectiveIsPremium && adManager.canDisplayAds(reportsBannerVisibility)

    val formattedBenchmarkTime = remember(benchmarkSeconds) {
        val mins = benchmarkSeconds / 60
        val secs = benchmarkSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reports_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "FPS Validation & Telemetry Reports",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transparent performance benchmarking comparing thermal rise, battery drain, and observed FPS under controlled test conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Live Benchmark Session Recorder Card
            item {
                GameBoostHeroCard(
                    accentStart = if (isBenchmarking) NeonRed else GamingBlue,
                    accentEnd = NeonCyan
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
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Telemetry Session Recorder",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isBenchmarking) {
                            StatusPillBadge(
                                text = "RECORDING $formattedBenchmarkTime",
                                color = NeonRed
                            )
                        }
                    }

                    if (!isBenchmarking) {
                        OutlinedTextField(
                            value = sessionLabel,
                            onValueChange = { sessionLabel = it },
                            label = { Text("Session Description", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Test Run A: Playing while charging", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            )
                        )

                        OutlinedTextField(
                            value = graphicsSettings,
                            onValueChange = { graphicsSettings = it },
                            label = { Text("In-Game Graphics Settings", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Smooth + High Cap + Shadows OFF", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedContainerColor = DarkSurfaceElevated,
                                unfocusedContainerColor = DarkSurfaceElevated
                            )
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "FPS Measurement Source",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "None (FPS Unavailable)" to "Standard / No OEM tool",
                                    "OEM Game Turbo / Space" to "OEM Tool Active",
                                    "ADB gfxinfo / Perfetto" to "ADB Profiler"
                                ).forEach { (sourceKey, label) ->
                                    FilterChip(
                                        selected = selectedFpsSource == sourceKey,
                                        onClick = { selectedFpsSource = sourceKey },
                                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GamingBlue.copy(alpha = 0.25f),
                                            selectedLabelColor = NeonCyan
                                        )
                                    )
                                }
                            }
                        }

                        PrimaryGradientButton(
                            text = "Start Live Benchmark Session",
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                onStartBenchmark(
                                    sessionLabel.ifBlank { "Test Session" },
                                    selectedGame,
                                    graphicsSettings,
                                    selectedFpsSource,
                                    userNotes
                                )
                            },
                            modifier = Modifier.testTag("reports_start_benchmark_button")
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Sampling hardware telemetry (battery temp, PowerManager status, RAM, TCP latency)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Button(
                                onClick = { showStopDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, NeonRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("reports_stop_benchmark_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = NeonRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Finish & Save Benchmark Report", color = NeonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Daily/Weekly Session Analytics Summary
            item {
                SessionAnalyticsOverviewCard(summary = sessionSummary)
            }

            // Temperature Curve & 2-Session Comparison (up to 10 Free / 50 Pro)
            if (visibleSessions.isNotEmpty()) {
                item {
                    SessionComparisonAndChartCard(
                        sessions = visibleSessions,
                        maxSessionsCount = maxHistoryCount
                    )
                }
            }

            // Pro Advanced Session Insights & Structured Export Card
            item {
                GameBoostSurfaceCard(
                    modifier = Modifier.testTag("reports_pro_insights_card"),
                    borderColor = if (effectiveIsPremium) NeonPurple.copy(alpha = 0.55f) else DarkSurfaceBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Extended History & Pro Report Export",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusPillBadge(
                            text = if (effectiveIsPremium) "PRO (UP TO 50)" else "FREE (LAST 10)",
                            color = if (effectiveIsPremium) NeonPurpleBright else TextSecondary,
                            showDot = false
                        )
                    }

                    if (effectiveIsPremium) {
                        val stableCount = visibleSessions.count { !it.throttlingObserved }
                        val stabilityPct = if (visibleSessions.isEmpty()) 100 else ((stableCount * 100f) / visibleSessions.size).toInt()
                        val avgRamFree = if (visibleSessions.isEmpty()) stats.availableMemoryGb else visibleSessions.map { it.avgAvailRamGb }.average().toFloat()
                        Text(
                            text = "Showing ${visibleSessions.size} of ${benchmarkSessions.size} saved runs • Thermal Stability Rate: $stabilityPct% • Avg Free RAM: ${String.format(Locale.US, "%.2f", avgRamFree)} GB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                val reportText = buildProTelemetryReportSummary(stats, visibleSessions, stabilityPct)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("GAMEBOOST Telemetry Report", reportText))
                                exportedFeedback = "Copied ${visibleSessions.size}-session telemetry summary to clipboard."
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("reports_pro_export_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Pro Session Report Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (!exportedFeedback.isNullOrBlank()) {
                            Text(text = exportedFeedback.orEmpty(), color = NeonGreen, fontSize = 11.sp)
                        }
                    } else {
                        Text(
                            text = "Free shows last 10 runs & temp curves. Pro unlocks 50-run history, stability scoring & clipboard export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = onNavigateToPremium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("reports_upgrade_pro_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright)
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explore GAMEBOOST Pro", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section Title: Validation History & Comparisons
            item {
                SectionLabel("Controlled Validation Reports (${visibleSessions.size})", accentColor = NeonCyan)
            }

            // Clearly-labelled AdMob Native Sponsored Content
            if (shouldShowSponsoredCard) {
                item {
                    AdMobNativeSponsoredCard(
                        visibilityContext = nativeAdVisibility,
                        adManager = adManager,
                        testTagName = "reports_native_sponsored_card"
                    )
                }
            }

            if (visibleSessions.isEmpty()) {
                item {
                    GameBoostEmptyState(
                        icon = Icons.Default.Timeline,
                        title = "No Telemetry Sessions Yet",
                        description = "Start a Live Benchmark Session above to record battery temperature, RAM availability, and PowerManager thermal state over time."
                    )
                }
            } else {
                items(visibleSessions) { report ->
                    BenchmarkReportCard(report = report)
                }
            }

            // Hardware Profile Summary
            item {
                GameBoostSurfaceCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(text = "Device Hardware Baseline", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    ReportRow(label = "Device Model", value = stats.deviceModel)
                    ReportRow(label = "Hardware Chipset", value = stats.cpuModel)
                    ReportRow(label = "SoC Board", value = stats.cpuBoard)
                    ReportRow(label = "Display Refresh Rate", value = "${stats.displayRefreshRate.toInt()} Hz")
                    ReportRow(label = "Thermal Headroom API", value = if (stats.isThermalHeadroomSupported) "${(stats.thermalHeadroom * 100).toInt()}%" else "Unsupported on this device")
                    ReportRow(label = "PowerManager Thermal Status", value = if (stats.isThermalStatusSupported) "Supported (API 29+)" else "Unsupported (< Android 10)")
                    ReportRow(label = "Total System RAM", value = "${stats.totalMemoryGb} GB")
                    ReportRow(label = "Android Version", value = "Android ${stats.androidVersion} (API ${stats.sdkInt})")
                }
            }

            // Transparent FPS Testing Guide Card
            item {
                GameBoostSurfaceCard(containerColor = DarkSurfaceElevated) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(text = "Scientific FPS Validation Guide", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "1. Control Test Variables: Use the same phone, game map, graphics settings, room temperature, and test duration.\n" +
                            "2. Unrooted Android Sandbox: Android isolates other apps' frame buffers. No standard third-party app can read another game's live FPS without root, Shizuku, or ADB.\n" +
                            "3. Validating Frame Rate: If your phone includes a manufacturer overlay (e.g., Xiaomi Game Turbo, ASUS Armoury Crate, Samsung Game Plugins) or you connect via ADB dumpsys gfxinfo, log those observed numbers above.\n" +
                            "4. Hardware Correlation: PowerManager thermal throttling status, battery temp rise, and RAM availability show when clock speeds are constrained.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Bottom Reports Adaptive Banner Slot
            if (shouldShowReportsBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = reportsBannerVisibility,
                        adManager = adManager,
                        testTagName = "reports_ad_banner_slot"
                    )
                }
            }
        }
    }

    // Dialog when completing benchmark session
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Log Final Session Metrics", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "If an OEM game overlay or developer FPS tool was active, enter your observed frame rate below. If not, leave blank to record as 'FPS Unavailable'.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = inputAvgFpsText,
                        onValueChange = { inputAvgFpsText = it },
                        label = { Text("Observed Average FPS (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 30.5", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        )
                    )
                    OutlinedTextField(
                        value = inputMinFpsText,
                        onValueChange = { inputMinFpsText = it },
                        label = { Text("Observed 1% Low / Min FPS (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 24.0", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val avg = inputAvgFpsText.toFloatOrNull()
                        val min = inputMinFpsText.toFloatOrNull()
                        showStopDialog = false
                        onStopBenchmark(avg, min)
                        inputAvgFpsText = ""
                        inputMinFpsText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Save Report", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopBenchmark(null, null)
                        inputAvgFpsText = ""
                        inputMinFpsText = ""
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Save as 'FPS Unavailable'", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun BenchmarkReportCard(report: BenchmarkSessionReport) {
    GameBoostSurfaceCard(
        borderColor = if (report.throttlingObserved) NeonRed.copy(alpha = 0.4f) else DarkSurfaceBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = report.sessionLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    text = "${report.gameTitle} • Settings: ${report.graphicsSettings}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            StatusPillBadge(
                text = if (report.throttlingObserved) "THROTTLING REPORTED" else "STABLE TELEMETRY",
                color = if (report.throttlingObserved) NeonRed else NeonGreen
            )
        }

        HorizontalDivider(color = DarkSurfaceBorder)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Average Measured FPS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (report.measuredAvgFps != null) "${report.measuredAvgFps} FPS" else "Unavailable (Sandbox Protected)",
                    style = TelemetryMonoStyle.copy(fontSize = 13.sp),
                    color = if (report.measuredAvgFps != null) NeonCyan else TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "1% Low / Min FPS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (report.measuredMinFps != null) "${report.measuredMinFps} FPS" else "Unavailable",
                    style = TelemetryMonoStyle.copy(fontSize = 13.sp),
                    color = if (report.measuredMinFps != null) TextPrimary else TextMuted
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Calculated Frame-Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (report.measuredFrameTimeMs != null) "~${report.measuredFrameTimeMs} ms" else "Unavailable",
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Display Refresh Rate", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = "${report.displayRefreshRateHz.toInt()} Hz Display",
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = TextSecondary
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Battery Temp Change", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                val hasTemp = report.startTempC > 0f && report.peakTempC > 0f
                val delta = report.peakTempC - report.startTempC
                val deltaSign = if (delta >= 0) "+" else ""
                Text(
                    text = if (hasTemp) {
                        "${report.startTempC}°C → ${report.peakTempC}°C ($deltaSign${String.format(Locale.US, "%.1f", delta)}°C)"
                    } else {
                        "N/A"
                    },
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = if (hasTemp && report.peakTempC >= 41f) NeonRed else TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Battery Drain", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = "${report.startBatteryLevel}% → ${report.endBatteryLevel}% (${report.endBatteryLevel - report.startBatteryLevel}%)",
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
            }
        }

        Column {
            Text(text = "OS Thermal Status (Start → Peak)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "${report.startThermalStatus} → ${report.peakThermalStatus}",
                color = if (report.throttlingObserved) NeonRed else TextPrimary,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "RAM (Min / Avg Free)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = "${report.minAvailRamGb} GB / ${report.avgAvailRamGb} GB Free",
                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "TCP Gateway Latency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (report.avgPingMs > 0) "Avg ${report.avgPingMs}ms • Jitter ${report.avgJitterMs}ms" else "Not Tested (N/A)",
                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                    color = if (report.avgPingMs > 0) NeonCyan else TextSecondary
                )
            }
        }

        val comparisonDetails = remember(report) {
            com.example.data.PerformanceAdvisorEngine.generateSessionComparisonReport(report)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${report.fpsMeasurementSource}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                StatusPillBadge(
                    text = comparisonDetails.confidenceTag,
                    color = NeonCyan,
                    showDot = false
                )
            }
            Text(
                text = comparisonDetails.evaluationVerdict,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = TextPrimary)
    }
}

internal fun buildProTelemetryReportSummary(
    stats: DeviceStats,
    sessions: List<BenchmarkSessionReport>,
    stabilityPct: Int
): String {
    val sb = StringBuilder()
    sb.appendLine("GAMEBOOST PRO — Telemetry & Stability Report")
    sb.appendLine("Device: ${stats.deviceModel} | SoC: ${stats.cpuModel} | Android ${stats.androidVersion} (API ${stats.sdkInt})")
    sb.appendLine("Sessions Analyzed: ${sessions.size} | Thermal Stability Rate: $stabilityPct%")
    sb.appendLine("---")
    sessions.take(10).forEachIndexed { idx, s ->
        val avgFpsStr = s.measuredAvgFps?.let { "${it} FPS" } ?: "FPS N/A"
        val tempStr = if (s.startTempC > 0f && s.peakTempC > 0f) "Temp ${s.startTempC}°C->${s.peakTempC}°C" else "Temp N/A"
        val pingStr = if (s.avgPingMs > 0) "Ping ${s.avgPingMs}ms" else "Ping N/A"
        sb.appendLine(
            "${idx + 1}. ${s.sessionLabel} (${s.gameTitle}) | ${s.durationSeconds}s | " +
                "$tempStr | Bat ${s.startBatteryLevel}%->${s.endBatteryLevel}% | " +
                "$avgFpsStr | $pingStr | Throttled=${s.throttlingObserved}"
        )
    }
    return sb.toString().trim()
}
