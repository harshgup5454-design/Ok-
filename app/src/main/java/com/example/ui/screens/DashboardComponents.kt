package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.data.*
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.TelemetryArcGauge
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun DashboardProtectionBanner(
    protectionStatus: GamingProtectionStatus,
    onOpenSmartGaming: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = when (protectionStatus.overallSeverity) {
        ProtectionSeverity.CRITICAL -> NeonRed
        ProtectionSeverity.WARNING -> NeonOrange
        ProtectionSeverity.ADVISORY -> NeonCyan
        ProtectionSeverity.SAFE -> NeonGreen
    }
    val readinessLabel = when (protectionStatus.overallSeverity) {
        ProtectionSeverity.CRITICAL -> "THERMAL ALERT"
        ProtectionSeverity.WARNING -> "CAUTION"
        ProtectionSeverity.ADVISORY -> "ADVISORY"
        ProtectionSeverity.SAFE -> "READY"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_protection_card"),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (protectionStatus.alerts.isNotEmpty()) expanded = !expanded
                        else onOpenSmartGaming()
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (protectionStatus.overallSeverity == ProtectionSeverity.SAFE) {
                                    Icons.Default.VerifiedUser
                                } else {
                                    Icons.Default.HealthAndSafety
                                },
                                contentDescription = protectionStatus.headline,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = protectionStatus.headline,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Battery Health: ${protectionStatus.batteryHealthLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                StatusPillBadge(text = readinessLabel, color = accentColor, showDot = true)
            }

            AnimatedVisibility(visible = expanded && protectionStatus.alerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = DarkSurfaceBorder)
                    protectionStatus.alerts.forEach { alert ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(alert.title, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(alert.recommendation, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualGaugeCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    progress: Float
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.10f), Color.Transparent),
                        radius = 280f
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    TelemetryArcGauge(
                        progress = progress,
                        accentColor = accentColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Text(
                    text = value,
                    style = TelemetryMonoStyle.copy(fontSize = 18.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DashboardQuickLaunchAndRecentCard(
    installedGames: List<GameProfile>,
    latestSession: BenchmarkSessionReport?,
    onLaunchGame: (GameProfile) -> Unit,
    onNavigateToGamingSpace: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    val featuredGame = remember(installedGames) {
        installedGames.firstOrNull { it.isInLibrary && it.isInstalled && it.isFavorite }
            ?: installedGames.firstOrNull { it.isInLibrary && it.isInstalled }
            ?: installedGames.firstOrNull { it.isInLibrary }
    }

    if (featuredGame == null && latestSession == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (featuredGame != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonPurple.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.35f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(21.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = featuredGame.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${featuredGame.performanceMode.displayName} • ${featuredGame.fpsLock} FPS Target",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onNavigateToGamingSpace,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("All", color = TextSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onLaunchGame(featuredGame) },
                            enabled = featuredGame.isInstalled,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_quick_launch_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Launch ${featuredGame.name}", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (featuredGame.isInstalled) "Launch" else "Missing",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (latestSession != null) {
                HorizontalDivider(color = DarkSurfaceBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToReports() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mins = latestSession.durationSeconds / 60
                    val secs = latestSession.durationSeconds % 60
                    val tempLabel = if (latestSession.peakTempC > 0f) {
                        String.format(Locale.US, "%.1f°C", latestSession.peakTempC)
                    } else {
                        "Temp N/A"
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(16.dp))
                        Text(
                            text = String.format(Locale.US, "Recent: %s • %dm %02ds • %s", latestSession.gameTitle, mins, secs, tempLabel),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Reports", tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
