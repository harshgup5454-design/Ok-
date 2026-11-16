package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun SmartAimControlCenterSection(
    controlState: SmartAimControlState,
    gyroState: GyroscopeTelemetry,
    deviceSpecs: AimDeviceSpecs?,
    savedProfiles: List<SensitivityProfile>,
    onMasterSwitchChanged: (Boolean) -> Unit,
    onModeSelected: (AimOptimizationMode) -> Unit,
    onGameSelected: (String) -> Unit,
    onApplyPreset: (String, AimOptimizationMode) -> Unit,
    onCalibrateGyro: () -> Unit,
    onSelectProfile: (SensitivityProfile) -> Unit,
    onExportProfiles: () -> Unit,
    onImportProfiles: () -> Unit
) {
    var isDeviceSpecsExpanded by remember { mutableStateOf(false) }
    val supportedGames = listOf("Free Fire MAX", "BGMI / PUBG Mobile", "Call of Duty: Mobile")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Master Control Hero Card
        GameBoostHeroCard(
            accentStart = if (controlState.isMasterEnabled) NeonCyan else GamingBlue,
            accentEnd = if (controlState.isMasterEnabled) NeonPurple else NeonCyan,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (controlState.isMasterEnabled)
                                    Brush.linearGradient(listOf(GamingBlue, NeonCyan))
                                else
                                    Brush.linearGradient(listOf(DarkSurfaceVariant, DarkSurfaceElevated))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = if (controlState.isMasterEnabled) Color.Black else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SMART AIM & ROTATION OPTIMIZER",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (controlState.isMasterEnabled) "Optimizer Active • Real-time Assistance" else "Optimizer Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (controlState.isMasterEnabled) NeonGreen else TextMuted
                        )
                    }
                }

                Switch(
                    checked = controlState.isMasterEnabled,
                    onCheckedChange = onMasterSwitchChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = DarkSurfaceBorder)

            SectionLabel(text = "Select Aiming Strategy", accentColor = NeonCyan)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AimOptimizationMode.values().forEach { mode ->
                    val isSelected = controlState.activeMode == mode
                    val accentColor = when (mode) {
                        AimOptimizationMode.ACCURACY -> NeonCyan
                        AimOptimizationMode.FAST_ROTATION -> NeonPurpleBright
                        AimOptimizationMode.BALANCED -> NeonGreen
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.16f) else DarkSurfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) accentColor else DarkSurfaceBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = controlState.isMasterEnabled) {
                                onModeSelected(mode)
                                onApplyPreset(controlState.selectedGame, mode)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    AimOptimizationMode.ACCURACY -> Icons.Default.FilterCenterFocus
                                    AimOptimizationMode.FAST_ROTATION -> Icons.Default.RotateRight
                                    AimOptimizationMode.BALANCED -> Icons.Default.Balance
                                },
                                contentDescription = null,
                                tint = if (isSelected) accentColor else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isSelected) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isSelected) accentColor else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Active Game & Target Preset Card
        GameBoostSurfaceCard(
            containerColor = DarkSurface,
            borderColor = DarkSurfaceBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET GAME INTEGRATION",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                StatusPillBadge(text = controlState.selectedGame, color = NeonCyan, showDot = false)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                supportedGames.forEach { game ->
                    val isSelected = controlState.selectedGame == game
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onGameSelected(game)
                            onApplyPreset(game, controlState.activeMode)
                        },
                        label = { Text(game.take(12), fontSize = 10.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "In-Game Configuration Reminder",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan
                        )
                    }
                    Text(
                        text = "To apply ${controlState.activeMode.displayName} inside ${controlState.selectedGame}, open the in-game Settings > Sensitivity and manually match the calculated values. GAMEBOOST never modifies third-party game code or memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Hardware Gyroscope & Touch Sensor Card
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
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    Text(
                        text = "GYROSCOPE & SENSOR STATUS",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }

                if (gyroState.isAvailable) {
                    Button(
                        onClick = onCalibrateGyro,
                        enabled = !gyroState.isCalibrating,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.18f)),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = if (gyroState.isCalibrating) "Calibrating..." else "Calibrate",
                            color = NeonGreen,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (gyroState.isAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorStatBox(
                        label = "X Axis",
                        value = String.format(Locale.US, "%.2f", gyroState.xRadPerSec),
                        unit = "rad/s",
                        modifier = Modifier.weight(1f)
                    )
                    SensorStatBox(
                        label = "Y Axis",
                        value = String.format(Locale.US, "%.2f", gyroState.yRadPerSec),
                        unit = "rad/s",
                        modifier = Modifier.weight(1f)
                    )
                    SensorStatBox(
                        label = "Z Axis",
                        value = String.format(Locale.US, "%.2f", gyroState.zRadPerSec),
                        unit = "rad/s",
                        modifier = Modifier.weight(1f)
                    )
                    SensorStatBox(
                        label = "Stability",
                        value = "${gyroState.stabilityIndex.toInt()}%",
                        unit = if (gyroState.calibrated) "Calibrated" else "Live",
                        modifier = Modifier.weight(1.2f)
                    )
                }
            } else {
                Text(
                    text = "Hardware gyroscope not detected on this hardware profile. Standard thumb drag sensitivity will be utilized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { isDeviceSpecsExpanded = !isDeviceSpecsExpanded }
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Screen Resolution, Touch & Refresh Rate",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                Icon(
                    imageVector = if (isDeviceSpecsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isDeviceSpecsExpanded) {
                deviceSpecs?.let { specs ->
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpecRow("Device", specs.deviceModel)
                        SpecRow("Resolution", specs.screenResolutionPx)
                        SpecRow("Display Refresh Rate", "${specs.refreshRateHz.toInt()} Hz (Panel Speed)")
                        SpecRow("Multi-touch Points", "${specs.touchMaxPointers} fingers")
                        SpecRow("Screen DPI Tier", "${specs.screenDpi} DPI")
                        SpecRow("Gyro Sensor", specs.gyroSensorName)
                        Text(
                            text = "Notice: Display panel refresh rate (Hz) measures screen scanning frequency, not in-game render FPS.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Profile Management & Quick Restore
        GameBoostSurfaceCard(
            containerColor = DarkSurface,
            borderColor = DarkSurfaceBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVED PROFILES & RESTORATION",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onExportProfiles,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Export", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    }
                    OutlinedButton(
                        onClick = onImportProfiles,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Import", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                    }
                }
            }

            if (savedProfiles.isEmpty()) {
                GameBoostEmptyState(
                    icon = Icons.Default.Tune,
                    title = "No sensitivity profiles saved",
                    description = "Calculate and save a custom profile in the Calculator tab to quickly restore it here.",
                    accentColor = NeonCyan
                )
            } else {
                savedProfiles.take(4).forEach { profile ->
                    val isCurrent = controlState.activeProfileId == profile.id || controlState.activeProfileName == profile.profileName
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) DarkSurfaceElevated else DarkSurfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, if (isCurrent) NeonCyan else DarkSurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(profile) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.profileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${profile.gameName} • General ${profile.general} • RedDot ${profile.redDot}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (isCurrent) {
                                StatusPillBadge(text = "ACTIVE", color = NeonCyan, showDot = false)
                            } else {
                                Text("Restore", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorStatBox(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextMuted)
            Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 12.5.sp), color = NeonGreen)
            Text(text = unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp), color = TextSecondary)
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 11.5.sp), color = TextPrimary)
    }
}
