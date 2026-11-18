package com.example.ui.screens

import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.smartgaming.AutopilotState
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun GamingSessionScreen(
    stats: DeviceStats,
    networkStats: NetworkStats,
    autopilotState: AutopilotState,
    protectionStatus: GamingProtectionStatus,
    isBenchmarking: Boolean,
    benchmarkSeconds: Int,
    onSelectProfile: (ProfileType) -> Unit,
    onStartSession: () -> Unit,
    onStopAndSaveSession: (Float?, Float?) -> Unit,
    onRefreshPing: () -> Unit,
    onOpenOverlayPreview: () -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isOverlayRunning by GamingOverlayService.isServiceRunning.collectAsState()
    var hasOverlayPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var inputAvgFps by remember { mutableStateOf("") }
    var inputMinFps by remember { mutableStateOf("") }

    val formattedTime = remember(benchmarkSeconds) {
        String.format(Locale.US, "%02d:%02d", benchmarkSeconds / 60, benchmarkSeconds % 60)
    }

    val sevColor = when (protectionStatus.overallSeverity) {
        ProtectionSeverity.CRITICAL -> NeonRed
        ProtectionSeverity.WARNING -> NeonOrange
        ProtectionSeverity.ADVISORY -> NeonCyan
        ProtectionSeverity.SAFE -> NeonGreen
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gaming_session_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "Gaming Session HUD",
                    subtitle = autopilotState.targetGameTitle.ifBlank { "Global Gaming Mode" },
                    onBackClick = onBackClick,
                    backButtonTestTag = "gaming_session_back_button",
                    trailingContent = {
                        StatusPillBadge(
                            text = if (isBenchmarking) "LIVE" else "STANDBY",
                            color = if (isBenchmarking) NeonGreen else TextSecondary
                        )
                    }
                )
            }

            // Hero Timer & Session Status
            item {
                GameBoostHeroCard(
                    accentStart = if (isBenchmarking) NeonGreen else GamingBlue,
                    accentEnd = NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ELAPSED SESSION TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = formattedTime,
                                style = TelemetryMonoStyle.copy(fontSize = 36.sp),
                                color = if (isBenchmarking) NeonGreen else TextPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPillBadge(
                                text = autopilotState.activeProfile.autopilotTitle,
                                color = NeonCyan
                            )
                            Text(
                                text = "${stats.displayRefreshRate.toInt()} Hz Panel",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreen
                            )
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    // Profile Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ProfileType.SMOOTHNESS_FOCUS to "Stability",
                            ProfileType.BALANCED to "Balanced",
                            ProfileType.BATTERY_FRIENDLY to "Battery"
                        ).forEach { (profile, label) ->
                            val selected = autopilotState.activeProfile == profile
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectProfile(profile) },
                                label = { Text(label, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isBenchmarking) {
                        Button(
                            onClick = { showFinishDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("session_hud_stop_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, NeonRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = NeonRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP & SAVE TELEMETRY REPORT", color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        PrimaryGradientButton(
                            text = "BEGIN LIVE SESSION",
                            icon = Icons.Default.FlashOn,
                            startColor = Color(0xFF7C3AED),
                            endColor = Color(0xFFD946EF),
                            contentColor = Color.White,
                            onClick = onStartSession,
                            modifier = Modifier.testTag("session_hud_start_button")
                        )
                    }
                }
            }

            // Live Hardware Telemetry Grid
            item {
                SectionLabel("Live Telemetry Readouts", accentColor = NeonCyan)
            }

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
                        val hasTemp = stats.batteryTemperatureC > 0f
                        VisualGaugeCard(
                            modifier = Modifier.weight(1f),
                            title = "Battery & Temp",
                            value = if (hasTemp) "${stats.batteryTemperatureC}°C" else "N/A",
                            subtitle = "${stats.batteryLevelPercent}% • ${stats.powerSource}",
                            icon = Icons.Default.BatteryChargingFull,
                            accentColor = if (!hasTemp) TextSecondary else if (stats.batteryTemperatureC >= 39.5f) NeonRed else NeonGreen,
                            progress = if (hasTemp) (stats.batteryTemperatureC / 50f).coerceIn(0f, 1f) else 0.05f
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VisualGaugeCard(
                            modifier = Modifier.weight(1f),
                            title = "OS Thermal",
                            value = stats.powerManagerThermalStatus.take(10),
                            subtitle = if (stats.thermalHeadroom >= 0f) "Headroom ${(stats.thermalHeadroom * 100).toInt()}%" else "PowerManager",
                            icon = Icons.Default.Thermostat,
                            accentColor = sevColor,
                            progress = if (stats.powerManagerThermalStatusCode >= 2) 0.85f else 0.28f
                        )
                        VisualGaugeCard(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onRefreshPing() },
                            title = "Network (Tap Test)",
                            value = if (networkStats.pingMs < 0) "Not Tested" else "${networkStats.pingMs} ms",
                            subtitle = if (networkStats.isConnected) "${networkStats.connectionType} • ±${networkStats.jitterMs}ms" else "Offline",
                            icon = Icons.Default.Wifi,
                            accentColor = NeonPurple,
                            progress = if (networkStats.pingMs > 0) (networkStats.pingMs / 150f).coerceIn(0.1f, 1f) else 0.15f
                        )
                    }
                }
            }

            // Thermal Safety Card
            item {
                GameBoostSurfaceCard(borderColor = sevColor.copy(alpha = 0.45f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = sevColor, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(protectionStatus.headline, style = MaterialTheme.typography.titleSmall, color = sevColor)
                            Text("Battery Health: ${protectionStatus.batteryHealthLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    protectionStatus.alerts.forEach { alert ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(alert.title, color = sevColor, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                Text(alert.recommendation, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Quick Session Utilities
            item {
                SectionLabel("Quick Overlay & Device Controls", accentColor = NeonPurple)
            }

            item {
                GameBoostSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!hasOverlayPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    PermissionManager.openPermissionSettings(context, AppPermissionType.OVERLAY)
                                } else if (isOverlayRunning) {
                                    GamingOverlayService.stopOverlay(context)
                                } else {
                                    GamingOverlayService.startOverlay(context)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isOverlayRunning) "Stop Overlay" else "Start Overlay", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenOverlayPreview,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MenuOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Sidebar", fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VolumeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vol -", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vol +", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.BrightnessMedium, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Display", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Finish Gaming Session", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Optionally enter observed FPS from your OEM game overlay, or save hardware thermal telemetry directly.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = inputAvgFps,
                        onValueChange = { inputAvgFps = it },
                        label = { Text("Observed Avg FPS (Optional)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputMinFps,
                        onValueChange = { inputMinFps = it },
                        label = { Text("Observed 1% Low FPS (Optional)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        onStopAndSaveSession(inputAvgFps.toFloatOrNull(), inputMinFps.toFloatOrNull())
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save Session", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFinishDialog = false
                        onStopAndSaveSession(null, null)
                        onBackClick()
                    }
                ) {
                    Text("Save Telemetry Only", color = TextSecondary)
                }
            }
        )
    }
}
