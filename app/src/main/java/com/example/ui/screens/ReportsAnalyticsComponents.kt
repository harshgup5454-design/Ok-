package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BenchmarkSessionReport
import com.example.data.SessionAggregateSummary
import com.example.ui.components.GameBoostSurfaceCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun SessionAnalyticsOverviewCard(
    summary: SessionAggregateSummary
) {
    GameBoostSurfaceCard(
        modifier = Modifier.testTag("reports_analytics_overview_card")
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
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Session Analytics Overview",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            StatusPillBadge(text = "TELEMETRY", color = NeonCyan, showDot = false)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsMetricBox(
                modifier = Modifier.weight(1f),
                label = "Last 24h",
                value = "${summary.dailySessionCount} runs",
                subvalue = formatDurationShort(summary.dailyTotalDurationSec),
                accentColor = NeonCyan
            )
            AnalyticsMetricBox(
                modifier = Modifier.weight(1f),
                label = "Last 7 Days",
                value = "${summary.weeklySessionCount} runs",
                subvalue = formatDurationShort(summary.weeklyTotalDurationSec),
                accentColor = NeonPurpleBright
            )
            AnalyticsMetricBox(
                modifier = Modifier.weight(1f),
                label = "Avg Length",
                value = formatDurationShort(summary.averageDurationSec),
                subvalue = "Per session",
                accentColor = NeonGreen
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsMetricBox(
                modifier = Modifier.weight(1f),
                label = "Avg Est. Battery Drain",
                value = summary.averageEstimatedDrainPercent?.let {
                    String.format(Locale.US, "%.1f%%", it)
                } ?: "N/A (Charged)",
                subvalue = "Discharging runs",
                accentColor = NeonOrange
            )
            AnalyticsMetricBox(
                modifier = Modifier.weight(1f),
                label = "Avg Peak Temp",
                value = summary.averagePeakTempC?.let {
                    String.format(Locale.US, "%.1f°C", it)
                } ?: "N/A",
                subvalue = "Battery sensor",
                accentColor = if ((summary.averagePeakTempC ?: 0f) >= 40f) NeonRed else NeonGreen
            )
        }
    }
}

@Composable
private fun AnalyticsMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subvalue: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = TelemetryMonoStyle.copy(fontSize = 14.sp),
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subvalue,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SessionComparisonAndChartCard(
    sessions: List<BenchmarkSessionReport>,
    maxSessionsCount: Int = 10
) {
    if (sessions.isEmpty()) return

    var selectedPrimaryIndex by remember(sessions) { mutableIntStateOf(0) }
    var selectedCompareIndex by remember(sessions) {
        mutableIntStateOf(if (sessions.size > 1) 1 else 0)
    }

    val safePrimary = sessions.getOrElse(selectedPrimaryIndex) { sessions.first() }
    val safeCompare = sessions.getOrElse(selectedCompareIndex) { safePrimary }

    GameBoostSurfaceCard(
        modifier = Modifier.testTag("reports_comparison_chart_card")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ShowChart,
                contentDescription = null,
                tint = NeonPurpleBright,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Temperature Curve & Session Compare",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // Session A selector
        Text(
            text = "Primary Session (Chart)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.take(maxSessionsCount).forEachIndexed { idx, session ->
                FilterChip(
                    selected = idx == selectedPrimaryIndex,
                    onClick = { selectedPrimaryIndex = idx },
                    label = {
                        Text(
                            text = session.sessionLabel.take(20),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GamingBlue.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan
                    )
                )
            }
        }

        // Temperature Curve Canvas
        val snapshots = safePrimary.snapshots
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${safePrimary.gameTitle} • Temp Telemetry",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val hasPrimaryTemp = safePrimary.startTempC > 0f && safePrimary.peakTempC > 0f
                    Text(
                        text = if (hasPrimaryTemp) "${safePrimary.startTempC}°C → ${safePrimary.peakTempC}°C" else "N/A",
                        style = TelemetryMonoStyle.copy(fontSize = 11.5.sp),
                        color = if (!hasPrimaryTemp) TextSecondary else if (safePrimary.peakTempC >= 40f) NeonOrange else NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                val validTempSnapshots = remember(snapshots) {
                    snapshots.filter { it.batteryTempC > 0f }
                }
                if (validTempSnapshots.size >= 2) {
                    val minT = (validTempSnapshots.minOf { it.batteryTempC } - 1f).coerceAtLeast(20f)
                    val maxT = (validTempSnapshots.maxOf { it.batteryTempC } + 1f).coerceAtLeast(minT + 4f)
                    val gridColor = DarkSurfaceBorder
                    val lineColor = NeonCyan
                    val warnColor = NeonRed

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        for (i in 0..2) {
                            val y = h * (i / 2f)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val linePath = Path()
                        val fillPath = Path()
                        val points = validTempSnapshots.mapIndexed { index, snap ->
                            val x = (index.toFloat() / (validTempSnapshots.size - 1).coerceAtLeast(1)) * w
                            val norm = ((snap.batteryTempC - minT) / (maxT - minT)).coerceIn(0f, 1f)
                            val y = h - (norm * (h - 16f)) - 8f
                            Offset(x, y) to snap
                        }

                        points.forEachIndexed { idx, (pt, _) ->
                            if (idx == 0) {
                                linePath.moveTo(pt.x, pt.y)
                                fillPath.moveTo(pt.x, h)
                                fillPath.lineTo(pt.x, pt.y)
                            } else {
                                linePath.lineTo(pt.x, pt.y)
                                fillPath.lineTo(pt.x, pt.y)
                            }
                        }
                        if (points.isNotEmpty()) {
                            fillPath.lineTo(points.last().first.x, h)
                            fillPath.close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                                startY = 0f,
                                endY = h
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        points.forEach { (pt, snap) ->
                            val dotColor = if (snap.batteryTempC >= 41f) warnColor else lineColor
                            drawCircle(color = dotColor, radius = 3.5.dp.toPx(), center = pt)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0s (${validTempSnapshots.first().batteryTempC}°C)",
                            style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                            color = TextMuted
                        )
                        Text(
                            text = "${validTempSnapshots.size} samples",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${validTempSnapshots.last().elapsedSeconds}s (${validTempSnapshots.last().batteryTempC}°C)",
                            style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                            color = TextMuted
                        )
                    }
                } else {
                    Text(
                        text = "No time-series snapshots recorded for this baseline entry. Start a Live Benchmark Session to record per-interval sensor curves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2-Session Comparison Section
        if (sessions.size >= 2) {
            HorizontalDivider(color = DarkSurfaceBorder)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Compare With Second Session",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sessions.take(maxSessionsCount).forEachIndexed { idx, session ->
                    FilterChip(
                        selected = idx == selectedCompareIndex,
                        onClick = { selectedCompareIndex = idx },
                        label = {
                            Text(
                                text = session.sessionLabel.take(20),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple.copy(alpha = 0.25f),
                            selectedLabelColor = NeonCyan
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ComparisonHeaderRow(
                        leftTitle = safePrimary.sessionLabel.take(16),
                        rightTitle = safeCompare.sessionLabel.take(16)
                    )
                    HorizontalDivider(color = DarkSurfaceBorder)
                    ComparisonMetricRow(
                        metric = "Duration",
                        leftVal = formatDurationShort(safePrimary.durationSeconds),
                        rightVal = formatDurationShort(safeCompare.durationSeconds)
                    )
                    ComparisonMetricRow(
                        metric = "Peak Temp",
                        leftVal = if (safePrimary.peakTempC > 0f) "${safePrimary.peakTempC}°C" else "N/A",
                        rightVal = if (safeCompare.peakTempC > 0f) "${safeCompare.peakTempC}°C" else "N/A"
                    )
                    ComparisonMetricRow(
                        metric = "Temp Rise",
                        leftVal = if (safePrimary.startTempC > 0f && safePrimary.peakTempC > 0f) formatTempDelta(safePrimary.peakTempC - safePrimary.startTempC) else "N/A",
                        rightVal = if (safeCompare.startTempC > 0f && safeCompare.peakTempC > 0f) formatTempDelta(safeCompare.peakTempC - safeCompare.startTempC) else "N/A"
                    )
                    ComparisonMetricRow(
                        metric = "Bat. Delta",
                        leftVal = "${safePrimary.endBatteryLevel - safePrimary.startBatteryLevel}%",
                        rightVal = "${safeCompare.endBatteryLevel - safeCompare.startBatteryLevel}%"
                    )
                    ComparisonMetricRow(
                        metric = "Min Free RAM",
                        leftVal = "${safePrimary.minAvailRamGb} GB",
                        rightVal = "${safeCompare.minAvailRamGb} GB"
                    )
                    ComparisonMetricRow(
                        metric = "Avg Ping",
                        leftVal = if (safePrimary.avgPingMs > 0) "${safePrimary.avgPingMs} ms" else "N/A",
                        rightVal = if (safeCompare.avgPingMs > 0) "${safeCompare.avgPingMs} ms" else "N/A"
                    )
                    ComparisonMetricRow(
                        metric = "Throttled",
                        leftVal = if (safePrimary.throttlingObserved) "Yes" else "No",
                        rightVal = if (safeCompare.throttlingObserved) "Yes" else "No"
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeaderRow(leftTitle: String, rightTitle: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Metric", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(leftTitle, style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(rightTitle, style = MaterialTheme.typography.labelSmall, color = NeonPurpleBright, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComparisonMetricRow(metric: String, leftVal: String, rightVal: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(metric, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(leftVal, style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = TextPrimary, modifier = Modifier.weight(1f))
        Text(rightVal, style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

private fun formatDurationShort(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0m"
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins >= 60) {
        val hrs = mins / 60
        val remMins = mins % 60
        "${hrs}h ${remMins}m"
    } else if (mins > 0) {
        "${mins}m ${secs}s"
    } else {
        "${secs}s"
    }
}

private fun formatTempDelta(delta: Float): String {
    val sign = if (delta >= 0f) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", delta)}°C"
}
