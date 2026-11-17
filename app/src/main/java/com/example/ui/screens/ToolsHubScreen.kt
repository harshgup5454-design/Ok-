package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
                    subtitle = "Diagnostics, network, aim lab & autopilot suite",
                    icon = Icons.Default.Build,
                    iconTint = NeonPurpleBright
                )
            }

            // Highlight Suite Banner
            item {
                GameBoostHeroCard(
                    accentStart = GamingBlue,
                    accentEnd = NeonPurpleBright,
                    contentPadding = PaddingValues(16.dp)
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
                            Text(
                                text = "ZeroX Tactical Suite",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hardware-honest telemetry, jitter & throughput tests, controlled stress labs, and reversible OS profiles.",
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

            // Bento Lab Grid (2x2)
            item {
                SectionLabel(text = "Diagnostics & Telemetry Labs", accentColor = NeonCyan)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ToolBentoTile(
                            title = "Diagnostics Lab",
                            subtitle = "Storage, touch, Hz & battery",
                            icon = Icons.Default.Security,
                            accentColor = NeonCyan,
                            badge = "5 Tests",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_card_scan"),
                            onClick = onNavigateToScan
                        )
                        ToolBentoTile(
                            title = "Network Lab",
                            subtitle = "Ping, RFC 3393 jitter & speed",
                            icon = Icons.Default.Wifi,
                            accentColor = NeonGreen,
                            badge = "Ping & Jitter",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_card_network"),
                            onClick = onNavigateToNetwork
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ToolBentoTile(
                            title = "Aim & Control",
                            subtitle = "Sens calc, gyro & aim lab",
                            icon = Icons.Default.GpsFixed,
                            accentColor = NeonRed,
                            badge = "Aim Trainer",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_card_aim"),
                            onClick = onNavigateToAim
                        )
                        ToolBentoTile(
                            title = "Benchmark Lab",
                            subtitle = "CPU, memory & Canvas load",
                            icon = Icons.Default.Speed,
                            accentColor = NeonPurpleBright,
                            badge = "Stress Lab",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_card_lab"),
                            onClick = onNavigateToLab
                        )
                    }
                }
            }

            // System Control & Membership
            item {
                SectionLabel(text = "System Control & Membership", accentColor = NeonPurple)
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
                    title = if (isPremium) "ZeroX Boost Pro Active" else "ZeroX Boost Pro",
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
private fun ToolBentoTile(
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
            .heightIn(min = 138.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.09f), Color.Transparent)
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
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
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.25f))
                        )
                    )
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.22f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
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
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
