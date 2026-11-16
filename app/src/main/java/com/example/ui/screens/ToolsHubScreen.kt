package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.ProtectionSeverity
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ToolsHubScreen(
    isPremium: Boolean = false,
    isGamingSessionActive: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onNavigateToScan: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToAim: () -> Unit,
    onNavigateToLab: () -> Unit,
    onNavigateToDeviceControl: () -> Unit,
    onNavigateToSmartGaming: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {}
) {
    val adState by adManager.adState.collectAsState()
    val toolsVisibility = AdVisibilityContext(
        location = AdScreenLocation.TOOLS_HUB,
        isGamingSessionActive = isGamingSessionActive || isAutopilotActive,
        isAutopilotSessionActive = isAutopilotActive,
        thermalSeverity = thermalSeverity
    )
    val shouldShowToolsBanner = !isPremium && remember(adState, toolsVisibility) {
        adManager.canDisplayAds(toolsVisibility)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tools_hub_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (shouldShowToolsBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = toolsVisibility,
                        adManager = adManager,
                        testTagName = "tools_hub_ad_banner_slot"
                    )
                }
            }

            item {
                GameBoostScreenHeader(
                    title = "GAMING TOOLS",
                    subtitle = "Diagnostics, network, aim lab & autopilot controls",
                    icon = Icons.Default.Build,
                    iconTint = NeonCyan
                )
            }

            // Highlight Suite Banner
            item {
                GameBoostHeroCard(
                    accentStart = GamingBlue,
                    accentEnd = NeonPurple,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Tactical Utility Suite",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hardware-honest telemetry, jitter & throughput tests, controlled stress labs, and safe OS gaming profiles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPillBadge(
                            text = if (isAutopilotActive) "AUTOPILOT ON" else "6 MODULES",
                            color = if (isAutopilotActive) NeonGreen else NeonCyan
                        )
                    }
                }
            }

            // Category 1: Hardware & Storage
            item {
                SectionLabel(text = "Hardware & Storage", accentColor = NeonCyan)
            }

            item {
                ToolLauncherCard(
                    title = "Device Diagnostics Lab",
                    subtitle = "Storage, multi-touch, display Hz & battery metrics",
                    icon = Icons.Default.Security,
                    accentColor = NeonCyan,
                    badge = "5 Tests",
                    modifier = Modifier.testTag("tool_card_scan"),
                    onClick = onNavigateToScan
                )
            }

            // Category 2: Network & Connectivity
            item {
                SectionLabel(text = "Network & Connectivity", accentColor = NeonGreen)
            }

            item {
                ToolLauncherCard(
                    title = "Network Intelligence",
                    subtitle = "Gateway ping, RFC 3393 jitter & live throughput",
                    icon = Icons.Default.Wifi,
                    accentColor = NeonGreen,
                    badge = "Ping & Jitter",
                    modifier = Modifier.testTag("tool_card_network"),
                    onClick = onNavigateToNetwork
                )
            }

            // Category 3: Controls & Aim Training
            item {
                SectionLabel(text = "Controls & Aim Training", accentColor = NeonRed)
            }

            item {
                ToolLauncherCard(
                    title = "Aim & Control Lab",
                    subtitle = "Sensitivity calculator, gyro, 360° pad & aim trainer",
                    icon = Icons.Default.GpsFixed,
                    accentColor = NeonRed,
                    badge = "Aim Trainer",
                    modifier = Modifier.testTag("tool_card_aim"),
                    onClick = onNavigateToAim
                )
            }

            // Category 4: Performance & Workload Testing
            item {
                SectionLabel(text = "Performance & Workload Testing", accentColor = NeonPurpleBright)
            }

            item {
                ToolLauncherCard(
                    title = "Benchmark Lab",
                    subtitle = "CPU math, memory & Canvas frame load with thermal guard",
                    icon = Icons.Default.Speed,
                    accentColor = NeonPurpleBright,
                    badge = "CPU / Mem / GPU",
                    modifier = Modifier.testTag("tool_card_lab"),
                    onClick = onNavigateToLab
                )
            }

            // Category 5: System Control & Membership
            item {
                SectionLabel(text = "System Control & Membership", accentColor = GamingBlue)
            }

            item {
                ToolLauncherCard(
                    title = "Gaming Autopilot",
                    subtitle = "Auto brightness, DND, timeout & background advisor",
                    icon = Icons.Default.AutoMode,
                    accentColor = NeonGreen,
                    badge = "Autopilot",
                    modifier = Modifier.testTag("tool_card_autopilot"),
                    onClick = onNavigateToSmartGaming
                )
            }

            item {
                ToolLauncherCard(
                    title = "Device Control Engine",
                    subtitle = "Safe optimization profiles, Dev Options & OEM Hub",
                    icon = Icons.Default.Tune,
                    accentColor = NeonCyan,
                    badge = "Authorized Engine",
                    modifier = Modifier.testTag("tool_card_device_control"),
                    onClick = onNavigateToDeviceControl
                )
            }

            item {
                ToolLauncherCard(
                    title = if (isPremium) "GAMEBOOST Pro Active" else "GAMEBOOST Pro",
                    subtitle = if (isPremium) "Ad-free mode, 50-session history & Pro HUD unlocked" else "Ad-free experience, Pro overlay themes & 50-run history",
                    icon = Icons.Default.Stars,
                    accentColor = NeonPurpleBright,
                    badge = if (isPremium) "PRO" else "Upgrade",
                    modifier = Modifier.testTag("tool_card_premium"),
                    onClick = onNavigateToPremium
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun ToolLauncherCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
