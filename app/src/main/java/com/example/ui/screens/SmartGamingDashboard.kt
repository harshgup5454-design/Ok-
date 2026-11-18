package com.example.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.smartgaming.AutopilotState
import com.example.smartgaming.BackgroundAppItem
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun SmartGamingDashboard(
    deviceStats: DeviceStats,
    autopilotState: AutopilotState = AutopilotState(),
    protectionStatus: GamingProtectionStatus = GamingProtectionStatus(),
    backgroundApps: List<BackgroundAppItem> = emptyList(),
    gameProfiles: List<GameProfile> = emptyList(),
    permissionActionBanner: String? = null,
    onDismissPermissionBanner: () -> Unit = {},
    onToggleAutopilotMaster: (Boolean) -> Unit = {},
    onSelectProfile: (ProfileType) -> Unit = {},
    onSelectTargetGame: (GameProfile) -> Unit = {},
    onStartSession: (ProfileType) -> Unit = {},
    onStopSession: () -> Unit = {},
    onRefreshBackgroundApps: (Context) -> Unit = {},
    onRefreshUsageStatus: (Context) -> Unit = {},
    onOpenAppSettings: (Context, String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isGameSelectorExpanded by remember { mutableStateOf(false) }
    var isBackgroundSectionExpanded by remember { mutableStateOf(false) }
    var isPermissionGuideExpanded by remember { mutableStateOf(false) }

    BackHandler { onBackClick() }

    LaunchedEffect(Unit) {
        onRefreshUsageStatus(context)
        onRefreshBackgroundApps(context)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("smart_gaming_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackClick)
                            .testTag("autopilot_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ZEROX GAMING AUTOPILOT",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonPurpleBright,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = "Universal Game Detection & Safe System Profiles",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onRefreshUsageStatus(context)
                                onRefreshBackgroundApps(context)
                            }
                            .testTag("autopilot_refresh_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Autopilot Master Switch Hero Card
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
                                        if (autopilotState.isAutopilotEnabled)
                                            Brush.linearGradient(listOf(NeonPurple, NeonCyan))
                                        else
                                            Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceVariant))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoMode,
                                    contentDescription = null,
                                    tint = if (autopilotState.isAutopilotEnabled) Color.White else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AUTOPILOT MASTER",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (autopilotState.isAutopilotEnabled) NeonPurpleBright else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        autopilotState.isSessionActive && autopilotState.wasAutoDetected -> "Auto-detected active game session"
                                        autopilotState.isSessionActive -> "Manual game session active"
                                        autopilotState.isAutopilotEnabled -> "Auto-optimization armed"
                                        else -> "Manual profile mode"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = autopilotState.isAutopilotEnabled,
                            onCheckedChange = { onToggleAutopilotMaster(it) },
                            modifier = Modifier.testTag("autopilot_master_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    // Active Target Game Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target Game", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                            modifier = Modifier
                                .clickable(enabled = gameProfiles.isNotEmpty()) {
                                    isGameSelectorExpanded = !isGameSelectorExpanded
                                }
                                .testTag("autopilot_target_game_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = autopilotState.targetGameTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                if (gameProfiles.isNotEmpty()) {
                                    Icon(
                                        imageVector = if (isGameSelectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Choose Target Game",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isGameSelectorExpanded && gameProfiles.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Select game (loads & remembers its profile):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                gameProfiles.take(10).forEach { game ->
                                    val remembered = DeviceControlEngine.mapGamePerformanceModeToProfile(game.performanceMode)
                                    val isCurrent = game.packageName == autopilotState.targetGamePackage
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onSelectTargetGame(game)
                                                isGameSelectorExpanded = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = game.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) NeonCyan else TextPrimary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = remembered.autopilotTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, DarkSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (autopilotState.usageAccessGranted) NeonGreen else NeonOrange)
                            )
                            Text(
                                text = autopilotState.detectionStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = autopilotState.lastSessionSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (!permissionActionBanner.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonOrange.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("autopilot_permission_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = permissionActionBanner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        isPermissionGuideExpanded = true
                                        onDismissPermissionBanner()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Review", color = NeonOrange, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (!autopilotState.isSessionActive) {
                        PrimaryGradientButton(
                            text = "ACTIVATE ${autopilotState.activeProfile.autopilotTitle.uppercase()}",
                            icon = Icons.Default.PlayArrow,
                            onClick = { onStartSession(autopilotState.activeProfile) },
                            modifier = Modifier.testTag("autopilot_session_button")
                        )
                    } else {
                        Button(
                            onClick = onStopSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("autopilot_session_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.22f)),
                            border = BorderStroke(1.dp, NeonRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = NeonRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STOP SESSION & RESTORE SETTINGS",
                                color = NeonRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Live Telemetry & Smart Thermal Protection Card
            item {
                val sevColor = when (protectionStatus.overallSeverity) {
                    ProtectionSeverity.CRITICAL -> NeonRed
                    ProtectionSeverity.WARNING -> NeonOrange
                    ProtectionSeverity.ADVISORY -> NeonCyan
                    ProtectionSeverity.SAFE -> NeonGreen
                }
                GameBoostSurfaceCard(
                    containerColor = DarkSurface,
                    borderColor = sevColor.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = sevColor, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Smart Protection: ${protectionStatus.headline}",
                                style = MaterialTheme.typography.titleSmall,
                                color = sevColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val tempLabel = if (deviceStats.batteryTemperatureC > 0f) "${deviceStats.batteryTemperatureC}°C" else "N/A"
                        Text(
                            text = "$tempLabel • ${deviceStats.batteryLevelPercent}%",
                            style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val ramText = if (deviceStats.availableMemoryGb > 0f) {
                            "${String.format(Locale.US, "%.1f", deviceStats.availableMemoryGb)} GB Free"
                        } else {
                            "Unavailable"
                        }
                        val netText = if (autopilotState.networkLatencyMs < 0) {
                            "N/A (Not Tested)"
                        } else {
                            "${autopilotState.networkLatencyMs} ms (${autopilotState.networkType})"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Available RAM", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(ramText, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = TextPrimary)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Network Ping", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(netText, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = TextPrimary)
                            }
                        }
                    }

                    if (protectionStatus.alerts.isEmpty()) {
                        Text(
                            text = "Battery health (${protectionStatus.batteryHealthLabel}), OS thermal status (${deviceStats.powerManagerThermalStatus}), and power headroom are normal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        protectionStatus.alerts.forEach { alert ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceElevated,
                                border = BorderStroke(1.dp, DarkSurfaceBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(alert.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(alert.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("Recommendation: ${alert.recommendation}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Optimization Profiles Selector
            item {
                SectionLabel(text = "Optimization Profiles", accentColor = NeonCyan)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ProfileType.SMOOTHNESS_FOCUS to Icons.Default.Security,
                        ProfileType.BALANCED to Icons.Default.Balance,
                        ProfileType.BATTERY_FRIENDLY to Icons.Default.BatterySaver
                    ).forEach { (profile, icon) ->
                        val isSelected = autopilotState.activeProfile == profile
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectProfile(profile) }
                                .testTag("autopilot_profile_${profile.name.lowercase()}")
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) NeonCyan else DarkSurfaceBorder,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) GamingBlue.copy(alpha = 0.2f) else DarkSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = profile.autopilotTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) NeonCyan else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Profile Details & Honest Settings
            item {
                val profileDef = DeviceControlEngine.getProfileDefinition(autopilotState.activeProfile)
                GameBoostSurfaceCard(containerColor = DarkSurfaceElevated, borderColor = DarkSurfaceBorder) {
                    Text(
                        text = "Active Profile: ${autopilotState.activeProfile.autopilotTitle}",
                        style = MaterialTheme.typography.titleSmall,
                        color = NeonCyan
                    )
                    Text(
                        text = profileDef.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Supported System Adjustments:\n" + profileDef.appliedAuthorizedSettings.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Note: In-game sensitivity, graphics presets, and FPS limits are not modified automatically due to Android sandboxing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    if (autopilotState.isSessionActive && (autopilotState.appliedSettings.isNotEmpty() || autopilotState.skippedSettings.isNotEmpty())) {
                        HorizontalDivider(color = DarkSurfaceBorder)
                        if (autopilotState.appliedSettings.isNotEmpty()) {
                            Text(
                                text = "Applied Now:\n" + autopilotState.appliedSettings.joinToString("\n") { "✓ $it" },
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonGreen
                            )
                        }
                        if (autopilotState.skippedSettings.isNotEmpty()) {
                            Text(
                                text = "Skipped / Unchanged:\n" + autopilotState.skippedSettings.joinToString("\n") { "• $it" },
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonOrange
                            )
                        }
                    }
                }
            }

            // Permissions Status Card
            item {
                GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clickable { isPermissionGuideExpanded = !isPermissionGuideExpanded }
                            .testTag("autopilot_permissions_header"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                            Text("Official Android Permissions Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        Icon(
                            imageVector = if (isPermissionGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isPermissionGuideExpanded) {
                        val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context) else true
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val canDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm?.isNotificationPolicyAccessGranted == true else true
                        val hasUsage = autopilotState.usageAccessGranted

                        PermissionStatusRow(
                            label = "Usage Access (Auto Game Detection)",
                            isGranted = hasUsage,
                            description = "Optional: allows Autopilot to detect when supported games open or close.",
                            onFix = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                        )

                        PermissionStatusRow(
                            label = "Modify System Settings",
                            isGranted = canWrite,
                            description = "Required to adjust screen brightness and display timeout automatically.",
                            onFix = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            }
                        )

                        PermissionStatusRow(
                            label = "Do Not Disturb (Notification Policy)",
                            isGranted = canDnd,
                            description = "Required to suppress notification rings and alerts during matches.",
                            onFix = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }

            // Background Activity Manager
            item {
                GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clickable { isBackgroundSectionExpanded = !isBackgroundSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(18.dp))
                            Text("Background Activity Manager", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        Icon(
                            imageVector = if (isBackgroundSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, DarkSurfaceBorder)
                    ) {
                        Text(
                            text = "NOTICE: Standard Android applications cannot force-stop other apps due to sandbox security. Tap any app below to open its System Settings screen and set Battery Usage to 'Restricted' manually.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (isBackgroundSectionExpanded) {
                        if (backgroundApps.isEmpty()) {
                            Text("No background processes detected or apps isolated by Android.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        } else {
                            backgroundApps.take(6).forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenAppSettings(context, app.packageName) }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text(app.advice, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Manage", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                }
                                HorizontalDivider(color = DarkSurfaceBorder.copy(alpha = 0.5f))
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
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    description: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) NeonGreen else NeonOrange)
                )
                Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (!isGranted) {
            TextButton(
                onClick = onFix,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Authorize", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
        }
    }
}
