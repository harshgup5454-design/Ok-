package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BenchmarkLabScreen(
    labResults: List<BenchmarkLabRunResult>,
    isLabRunning: Boolean,
    labProgress: Float,
    activeWorkload: String,
    telemetryTemp: Float,
    telemetryThermal: String,
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onRunWorkload: (Context, WorkloadType) -> Unit,
    onCancelLab: () -> Unit,
    onClearHistory: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    val adState by adManager.adState.collectAsState()
    val labVisibility = AdVisibilityContext(
        location = AdScreenLocation.BENCHMARK_LAB,
        isGamingSessionActive = isAutopilotActive || isLabRunning,
        isAutopilotSessionActive = isAutopilotActive,
        isBenchmarking = isLabRunning,
        isLabRunning = isLabRunning,
        thermalSeverity = thermalSeverity
    )
    val shouldShowLabBanner = !isPremium && remember(adState, labVisibility) {
        adManager.canDisplayAds(labVisibility)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("benchmark_lab_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (shouldShowLabBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = labVisibility,
                        adManager = adManager,
                        testTagName = "benchmark_lab_ad_banner_slot"
                    )
                }
            }

            item {
                GameBoostScreenHeader(
                    title = "BENCHMARK LAB",
                    subtitle = "Controlled CPU, Memory & Canvas Workloads with Thermal Guard",
                    icon = Icons.Default.Speed,
                    iconTint = NeonPurpleBright,
                    onBackClick = onBackClick,
                    backButtonTestTag = "benchmark_lab_back_button"
                )
            }

            // Scientific Integrity Card
            item {
                GameBoostSurfaceCard(
                    containerColor = DarkSurfaceElevated,
                    borderColor = NeonCyan.copy(alpha = 0.28f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Scientific Benchmark Integrity",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Workloads measure ZeroX Boost's internal compute, memory copy, and UI canvas rendering throughput under live OS thermal monitoring. They do not simulate or fabricate external game FPS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Active Lab Run Progress Card
            if (isLabRunning) {
                item {
                    GameBoostHeroCard(
                        accentStart = NeonPurple,
                        accentEnd = NeonCyan,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                StatusPillBadge(text = "WORKLOAD ACTIVE", color = NeonCyan)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = activeWorkload,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            Button(
                                onClick = onCancelLab,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.45f)),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Cancel", color = NeonRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        LinearProgressIndicator(
                            progress = { labProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeonCyan,
                            trackColor = DarkSurfaceBorder
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Battery Temp: ${if (telemetryTemp > 0f) "${telemetryTemp}°C" else "N/A"}",
                                style = TelemetryMonoStyle.copy(fontSize = 11.5.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "Thermal: $telemetryThermal",
                                style = TelemetryMonoStyle.copy(fontSize = 11.5.sp),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Workload Selectors
            item {
                SectionLabel(text = "Select Benchmark Workload", accentColor = NeonPurpleBright)
            }

            items(WorkloadType.values()) { workload ->
                val accent = when {
                    workload.displayName.contains("CPU", ignoreCase = true) -> NeonCyan
                    workload.displayName.contains("Memory", ignoreCase = true) -> NeonGreen
                    else -> NeonPurpleBright
                }
                GameBoostSurfaceCard(
                    containerColor = DarkSurface,
                    borderColor = DarkSurfaceBorder,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = workload.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(text = workload.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onRunWorkload(context, workload) },
                            enabled = !isLabRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                disabledContainerColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (!isLabRunning) DarkBackground else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Run",
                                color = if (!isLabRunning) DarkBackground else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // History Section Header
            item {
                SectionLabel(
                    text = "Benchmark History (${labResults.size})",
                    accentColor = NeonCyan,
                    trailingAction = if (labResults.isNotEmpty()) {
                        {
                            TextButton(onClick = onClearHistory, modifier = Modifier.heightIn(min = 40.dp)) {
                                Text("Clear History", color = NeonRed, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else null
                )
            }

            if (labResults.isEmpty()) {
                item {
                    GameBoostEmptyState(
                        icon = Icons.Default.Speed,
                        title = "No benchmark results recorded yet",
                        description = "Run a CPU, Memory, or Canvas render workload above to record baseline throughput and thermal delta.",
                        accentColor = NeonPurpleBright
                    )
                }
            } else {
                items(labResults, key = { it.id }) { result ->
                    GameBoostSurfaceCard(
                        containerColor = DarkSurface,
                        borderColor = DarkSurfaceBorder,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = result.workloadType, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                            Text(
                                text = dateFormat.format(Date(result.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (result.workloadType.contains("CPU")) "Throughput: ${String.format(Locale.US, "%.1f", result.scoreOrOpsPerSec / 1000.0)}k ops/s"
                                else if (result.workloadType.contains("Memory")) "Bandwidth: ${String.format(Locale.US, "%.1f", result.scoreOrOpsPerSec)} MB/s"
                                else "App Render: ~${String.format(Locale.US, "%.1f", result.scoreOrOpsPerSec)} FPS",
                                style = TelemetryMonoStyle.copy(fontSize = 13.sp),
                                color = TextPrimary
                            )
                            StatusPillBadge(text = "${result.durationMs} ms", color = NeonPurpleBright, showDot = false)
                        }

                        HorizontalDivider(color = DarkSurfaceBorder)

                        Text(text = result.detailsSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val tempLabel = if (result.startBatteryTempC > 0f && result.peakBatteryTempC > 0f) {
                                "${result.startBatteryTempC}°C → ${result.peakBatteryTempC}°C"
                            } else {
                                "N/A"
                            }
                            Text(text = "Battery Temp: $tempLabel", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(text = result.deviceModel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
