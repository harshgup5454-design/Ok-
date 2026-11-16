package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.NetworkHistoryRecord
import com.example.data.NetworkStats
import com.example.data.ProtectionSeverity
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun NetworkScreen(
    stats: NetworkStats,
    isSpeedTesting: Boolean,
    speedTestProgress: Float,
    networkHistory: List<NetworkHistoryRecord>,
    onTestPing: () -> Unit,
    onStartSpeedTest: () -> Unit,
    onCancelSpeedTest: () -> Unit,
    onClearHistory: () -> Unit = {},
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onBackClick: (() -> Unit)? = null
) {
    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    val adState by adManager.adState.collectAsState()
    val networkVisibility = AdVisibilityContext(
        location = AdScreenLocation.NETWORK_TOOLS,
        isGamingSessionActive = isAutopilotActive,
        isAutopilotSessionActive = isAutopilotActive,
        isSpeedTesting = isSpeedTesting,
        thermalSeverity = thermalSeverity
    )
    val shouldShowNetworkBanner = !isPremium && remember(adState, networkVisibility) {
        adManager.canDisplayAds(networkVisibility)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("network_screen_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (shouldShowNetworkBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = networkVisibility,
                        adManager = adManager,
                        testTagName = "network_tools_ad_banner_slot"
                    )
                }
            }

            item {
                GameBoostScreenHeader(
                    title = "NETWORK INTELLIGENCE",
                    subtitle = "Gateway ping, RFC 3393 jitter variance & speed diagnostics",
                    icon = Icons.Default.Wifi,
                    iconTint = NeonGreen,
                    onBackClick = onBackClick,
                    backButtonTestTag = "network_back_button"
                )
            }

            // Connection Status & Diagnostics Hero Card
            item {
                GameBoostHeroCard(
                    accentStart = if (stats.isConnected) NeonCyan else NeonRed,
                    accentEnd = if (stats.isConnected) NeonGreen else NeonOrange,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = (if (stats.isConnected) NeonCyan else NeonRed).copy(alpha = 0.14f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (stats.connectionType) {
                                            "Wi-Fi" -> Icons.Default.Wifi
                                            "Ethernet" -> Icons.Default.SettingsEthernet
                                            "Offline" -> Icons.Default.CloudOff
                                            else -> Icons.Default.SignalCellularAlt
                                        },
                                        contentDescription = null,
                                        tint = if (stats.isConnected) NeonCyan else NeonRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Connection Dashboard",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (stats.isConnected) "Active transport detected" else "No active internet link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        StatusPillBadge(
                            text = if (stats.isConnected) stats.connectionType.uppercase() else "OFFLINE",
                            color = if (stats.isConnected) NeonGreen else NeonRed
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Gateway Latency",
                            value = if (stats.pingMs < 0) "Not Tested" else "${stats.pingMs} ms",
                            sub = buildString {
                                if (stats.minPingMs > 0) append("Min ${stats.minPingMs} • Max ${stats.maxPingMs}") else append("8.8.8.8:53")
                                if (stats.secondaryPingMs > 0) append(" • Alt ${stats.secondaryPingMs}ms")
                            },
                            tint = when {
                                stats.pingMs < 0 -> TextMuted
                                stats.pingMs < 50 -> NeonGreen
                                stats.pingMs < 100 -> NeonOrange
                                else -> NeonRed
                            }
                        )
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Latency Jitter",
                            value = if (stats.pingMs < 0) "—" else "${stats.jitterMs} ms",
                            sub = "RFC 3393 Delay Var",
                            tint = if (stats.jitterMs <= 15) NeonGreen else NeonOrange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "DNS Resolution",
                            value = if (stats.dnsResolutionMs < 0) "—" else "${stats.dnsResolutionMs} ms",
                            sub = "dns.google lookup",
                            tint = NeonCyan
                        )
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Packet Loss / Timeouts",
                            value = "${stats.packetLossPercent}%",
                            sub = "${stats.timeoutCount} timeouts recorded",
                            tint = if (stats.packetLossPercent == 0) NeonGreen else NeonRed
                        )
                    }

                    PrimaryGradientButton(
                        text = "Run Advanced Gateway Diagnostics",
                        icon = Icons.Default.Speed,
                        onClick = onTestPing,
                        modifier = Modifier.testTag("network_run_ping_button")
                    )
                }
            }

            // Speed Test Section
            item {
                GameBoostSurfaceCard(
                    containerColor = DarkSurface,
                    borderColor = DarkSurfaceBorder,
                    contentPadding = PaddingValues(18.dp)
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
                            Icon(Icons.Default.Download, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Internet Speed Benchmark",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        StatusPillBadge(text = "USES DATA", color = NeonOrange, showDot = false)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "HTTP Download Probe",
                            value = "${stats.downloadSpeedMbps} Mbps",
                            sub = "Measured transfer rate",
                            tint = NeonGreen
                        )
                        NetworkMetricBox(
                            modifier = Modifier.weight(1f),
                            title = "Link Upstream Est.",
                            value = "${stats.uploadSpeedMbps} Mbps",
                            sub = "Android NetworkCap",
                            tint = NeonCyan
                        )
                    }

                    Text(
                        text = stats.throughputNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    if (isSpeedTesting) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { speedTestProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = NeonGreen,
                                trackColor = DarkSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Testing bandwidth (${(speedTestProgress * 100).toInt()}%)...",
                                    style = TelemetryMonoStyle.copy(fontSize = 11.5.sp),
                                    color = TextSecondary
                                )
                                TextButton(onClick = onCancelSpeedTest, modifier = Modifier.heightIn(min = 44.dp)) {
                                    Text(text = "Cancel", color = NeonRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartSpeedTest,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("network_start_speedtest_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Start Speed Test Benchmark", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Network Advisor & Multiplayer Explanation
            item {
                GameBoostSurfaceCard(
                    containerColor = DarkSurfaceElevated,
                    borderColor = NeonCyan.copy(alpha = 0.25f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Network Advisor & Multiplayer Guidance",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "• Connection Type: ${stats.connectionType}. Crowded public Wi-Fi routers can spike frame-time jitter.\n" +
                            "• Manual Switching: If cellular provides lower ping than unstable Wi-Fi, switch manually via system settings.\n" +
                            "• Multiplayer Desync: Packet loss (>5%) causes rubberbanding and hit-reg delay in real-time combat games.\n" +
                            "• Gateway Measurement: Measures your device's round-trip to standard backbone endpoints, not external game servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // History Section
            item {
                SectionLabel(
                    text = "Network Test History (${networkHistory.size})",
                    accentColor = NeonPurpleBright,
                    trailingAction = if (networkHistory.isNotEmpty()) {
                        {
                            TextButton(onClick = onClearHistory, modifier = Modifier.heightIn(min = 40.dp)) {
                                Text("Clear", color = NeonRed, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else null
                )
            }

            if (networkHistory.isEmpty()) {
                item {
                    GameBoostEmptyState(
                        icon = Icons.Default.NetworkCheck,
                        title = "No network tests logged yet",
                        description = "Run gateway diagnostics above to measure latency, RFC 3393 jitter, and DNS resolution.",
                        accentColor = NeonCyan
                    )
                }
            } else {
                items(networkHistory) { record ->
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
                            Text(text = record.connectionType, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                text = "${record.avgPingMs} ms • Jitter ${record.jitterMs}ms",
                                style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                color = NeonCyan
                            )
                        }
                        Text(text = record.rating, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun NetworkMetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 16.sp), color = tint)
            Text(text = sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextMuted)
        }
    }
}
