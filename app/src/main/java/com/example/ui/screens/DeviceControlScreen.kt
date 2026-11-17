package com.example.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DeviceControlScreen(
    capabilities: DeviceCapabilities,
    controlState: SystemControlState,
    savedSystemState: SavedSystemStateEntity?,
    profileApplyMessage: String?,
    onRefreshCapabilities: (Context) -> Unit,
    onApplyProfile: (Context, ProfileType, (String) -> Unit) -> Unit,
    onRestoreOriginals: (Context) -> Unit,
    onUpdateBrightness: (Context, Int, Int) -> Unit,
    onUpdateTimeout: (Context, Int) -> Unit,
    onUpdateAutoRotate: (Context, Int) -> Unit,
    onUpdateDndFilter: (Context, Int) -> Unit,
    onLaunchOemGamingSpace: (Context) -> Boolean,
    onClearMessage: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedProfileForDialog by remember { mutableStateOf<ProfileType?>(null) }
    var expandedProfileCard by remember { mutableStateOf<ProfileType?>(ProfileType.SMOOTHNESS_FOCUS) }

    BackHandler { onBackClick() }

    LaunchedEffect(Unit) {
        onRefreshCapabilities(context)
    }

    LaunchedEffect(profileApplyMessage) {
        if (!profileApplyMessage.isNullOrBlank()) {
            Toast.makeText(context, profileApplyMessage, Toast.LENGTH_LONG).show()
            onClearMessage()
        }
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("device_control_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "DEVICE CONTROL ENGINE",
                    subtitle = "Authorized Android System Settings & OEM Integration",
                    icon = Icons.Default.Tune,
                    iconTint = NeonCyan,
                    onBackClick = onBackClick,
                    backButtonTestTag = "device_control_back_button",
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onRefreshCapabilities(context) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Capabilities", tint = NeonCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )
            }

            // PHASE 1: Device Capability & OEM Detection
            item {
                DeviceCapabilitiesHeaderCard(
                    capabilities = capabilities,
                    onLaunchOem = {
                        val launched = onLaunchOemGamingSpace(context)
                        if (!launched) {
                            Toast.makeText(context, "Could not launch OEM Gaming Space", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // PHASE 6: Safe Optimization Profiles
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(
                        text = "Safe Optimization Profiles",
                        accentColor = NeonCyan,
                        trailingAction = if (savedSystemState != null) {
                            {
                                OutlinedButton(
                                    onClick = { onRestoreOriginals(context) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore Originals", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else null
                    )

                    Text(
                        text = "Profiles control ONLY settings ZeroX Boost is authorized to change. Baseline system values are saved prior to changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    ProfileType.values().forEach { profileType ->
                        val profile = DeviceControlEngine.getProfileDefinition(profileType)
                        val isApplied = savedSystemState?.appliedProfileName == profile.displayName

                        OptimizationProfileCard(
                            profile = profile,
                            isApplied = isApplied,
                            isExpanded = expandedProfileCard == profileType,
                            onToggleExpand = {
                                expandedProfileCard = if (expandedProfileCard == profileType) null else profileType
                            },
                            onApplyClick = {
                                selectedProfileForDialog = profileType
                            }
                        )
                    }
                }
            }

            // PHASE 2: Authorized Settings Control Panel
            item {
                AuthorizedControlsPanelCard(
                    controlState = controlState,
                    capabilities = capabilities,
                    onUpdateBrightness = { mode, level -> onUpdateBrightness(context, mode, level) },
                    onUpdateTimeout = { timeoutMs -> onUpdateTimeout(context, timeoutMs) },
                    onUpdateAutoRotate = { rotate -> onUpdateAutoRotate(context, rotate) },
                    onUpdateDndFilter = { filter -> onUpdateDndFilter(context, filter) },
                    onGrantWriteSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Open Settings > Apps > Special App Access > Modify System Settings", Toast.LENGTH_LONG).show()
                        }
                    },
                    onGrantDndAccess = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Open Settings > Apps > Special Access > Do Not Disturb Access", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // PHASE 4: Display and Power Advisor
            item {
                DisplayPowerAdvisorCard(
                    capabilities = capabilities,
                    onOpenBatterySaverSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // PHASE 3: Developer Options Assistant
            item {
                DeveloperOptionsAssistantCard(
                    onOpenDeveloperOptions = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                Toast.makeText(context, "Open System > Developer Options", Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // PHASE 7: Transparency & Restoration Record
            item {
                RestoreTransparencyCard(
                    savedState = savedSystemState,
                    currentState = controlState,
                    onRestoreOriginals = { onRestoreOriginals(context) }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    selectedProfileForDialog?.let { profileType ->
        val profile = DeviceControlEngine.getProfileDefinition(profileType)
        AlertDialog(
            onDismissRequest = { selectedProfileForDialog = null },
            containerColor = DarkSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan)
                    Text("Apply ${profile.displayName}?", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ZeroX Boost will apply authorized system adjustments. Your original system baseline will be saved so you can revert anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = DarkSurfaceBorder)

                    Text("Settings to be changed:", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                    profile.appliedAuthorizedSettings.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }

                    if (profile.manualRecommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Recommended Manual Actions:", style = MaterialTheme.typography.labelMedium, color = NeonOrange)
                        profile.manualRecommendations.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(14.dp))
                                Text(item, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = selectedProfileForDialog
                        selectedProfileForDialog = null
                        if (target != null) {
                            onApplyProfile(context, target) { missingPermission ->
                                Toast.makeText(context, "Permission Required: $missingPermission", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Confirm & Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProfileForDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun DeviceCapabilitiesHeaderCard(
    capabilities: DeviceCapabilities,
    onLaunchOem: () -> Unit
) {
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
                Text(
                    text = "${capabilities.manufacturer.uppercase()} ${capabilities.model}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Android ${capabilities.androidVersion} (API ${capabilities.sdkInt})",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
            }

            StatusPillBadge(text = "${capabilities.currentRefreshRateHz.toInt()} Hz Current", color = NeonGreen, showDot = false)
        }

        HorizontalDivider(color = DarkSurfaceBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapChip(
                modifier = Modifier.weight(1f),
                label = "Supported Rates",
                value = capabilities.supportedRefreshRates.joinToString("/") { "${it.toInt()}Hz" },
                tint = NeonCyan
            )
            CapChip(
                modifier = Modifier.weight(1f),
                label = "Battery Saver",
                value = if (capabilities.isBatterySaverOn) "ACTIVE" else "OFF",
                tint = if (capabilities.isBatterySaverOn) NeonOrange else NeonGreen
            )
            CapChip(
                modifier = Modifier.weight(1f),
                label = "OS Thermal",
                value = capabilities.thermalStatusDesc.take(10),
                tint = if (capabilities.thermalStatusCode >= 2) NeonRed else NeonGreen
            )
        }

        val oem = capabilities.oemGamingSpace
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (oem?.isAvailable == true) Icons.Default.Launch else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (oem?.isAvailable == true) NeonCyan else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (oem?.isAvailable == true) "OEM Gaming Hub: ${oem.name}" else "Native Android Gaming Engine Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = if (oem?.isAvailable == true) "Official manufacturer gaming interface detected" else "Standard Android system APIs in use",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (oem?.isAvailable == true) {
                    Button(
                        onClick = onLaunchOem,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text("Open OEM Hub", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CapChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextSecondary)
            Text(value, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = tint, maxLines = 1)
        }
    }
}

@Composable
fun OptimizationProfileCard(
    profile: OptimizationProfile,
    isApplied: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onApplyClick: () -> Unit
) {
    GameBoostSurfaceCard(
        containerColor = if (isApplied) DarkSurfaceElevated else DarkSurface,
        borderColor = if (isApplied) NeonCyan else DarkSurfaceBorder,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (profile.type) {
                        ProfileType.BALANCED -> Icons.Default.Balance
                        ProfileType.SMOOTHNESS_FOCUS -> Icons.Default.Speed
                        ProfileType.BATTERY_FRIENDLY -> Icons.Default.BatterySaver
                    },
                    contentDescription = null,
                    tint = when (profile.type) {
                        ProfileType.BALANCED -> NeonCyan
                        ProfileType.SMOOTHNESS_FOCUS -> NeonGreen
                        ProfileType.BATTERY_FRIENDLY -> NeonOrange
                    },
                    modifier = Modifier.size(22.dp)
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = profile.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        if (isApplied) {
                            StatusPillBadge(text = "ACTIVE", color = NeonCyan, showDot = false)
                        }
                    }
                    Text(text = profile.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onApplyClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isApplied) NeonCyan.copy(alpha = 0.2f) else NeonCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        text = if (isApplied) "Re-apply" else "Apply",
                        color = if (isApplied) NeonCyan else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Details",
                        tint = TextSecondary
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = DarkSurfaceBorder)

                Text("Applied Authorized Settings", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                profile.appliedAuthorizedSettings.forEach { setting ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                        Text(setting, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }

                Text("Recommended Manual User Actions", style = MaterialTheme.typography.labelMedium, color = NeonOrange)
                profile.manualRecommendations.forEach { rec ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(12.dp))
                        Text(rec, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorizedControlsPanelCard(
    controlState: SystemControlState,
    capabilities: DeviceCapabilities,
    onUpdateBrightness: (Int, Int) -> Unit,
    onUpdateTimeout: (Int) -> Unit,
    onUpdateAutoRotate: (Int) -> Unit,
    onUpdateDndFilter: (Int) -> Unit,
    onGrantWriteSettings: () -> Unit,
    onGrantDndAccess: () -> Unit
) {
    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Text("AUTHORIZED SYSTEM CONTROLS", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            StatusPillBadge(text = "Official APIs", color = NeonGreen, showDot = false)
        }

        Text(
            text = "Directly modify authorized device system preferences. No WRITE_SECURE_SETTINGS, ADB, Shizuku, or hidden APIs are used.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (!capabilities.canWriteSettings) {
            PermissionRequestBanner(
                title = "Modify System Settings Permission Required",
                description = "Required to control Screen Brightness Mode, Screen Timeout, and Auto-Rotate lock.",
                onClickGrant = onGrantWriteSettings
            )
        }

        if (!capabilities.canAccessDnd) {
            PermissionRequestBanner(
                title = "Do Not Disturb Access Required",
                description = "Required to moderate incoming notification popups while playing matches.",
                onClickGrant = onGrantDndAccess
            )
        }

        HorizontalDivider(color = DarkSurfaceBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Auto-Rotate Lock", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text("Prevent accidental orientation switching", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = controlState.autoRotate == 1,
                onCheckedChange = { isChecked -> onUpdateAutoRotate(if (isChecked) 1 else 0) },
                enabled = capabilities.canWriteSettings,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NeonCyan)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Screen Off Timeout", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60000 to "1m", 120000 to "2m", 300000 to "5m", 1800000 to "30m").forEach { (ms, label) ->
                    FilterChip(
                        selected = controlState.screenTimeoutMs == ms,
                        onClick = { onUpdateTimeout(ms) },
                        enabled = capabilities.canWriteSettings,
                        label = { Text(label, fontSize = 10.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Screen Brightness", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    text = if (controlState.brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "Auto-Brightness" else "${(controlState.brightnessLevel * 100) / 255}% Manual",
                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                    color = NeonCyan
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = controlState.brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                    onClick = { onUpdateBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, controlState.brightnessLevel) },
                    enabled = capabilities.canWriteSettings,
                    label = { Text("Auto", fontSize = 10.5.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = controlState.brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    onClick = { onUpdateBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, controlState.brightnessLevel) },
                    enabled = capabilities.canWriteSettings,
                    label = { Text("Manual", fontSize = 10.5.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (controlState.brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Slider(
                    value = controlState.brightnessLevel.toFloat(),
                    onValueChange = { newLevel -> onUpdateBrightness(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, newLevel.toInt()) },
                    valueRange = 10f..255f,
                    enabled = capabilities.canWriteSettings,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Do Not Disturb Notification Filter", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    NotificationManager.INTERRUPTION_FILTER_ALL to "All",
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY to "Priority",
                    NotificationManager.INTERRUPTION_FILTER_ALARMS to "Alarms"
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = controlState.dndFilter == filter,
                        onClick = { onUpdateDndFilter(filter) },
                        enabled = capabilities.canAccessDnd,
                        label = { Text(label, fontSize = 10.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestBanner(
    title: String,
    description: String,
    onClickGrant: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NeonOrange.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = NeonOrange)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClickGrant,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun DisplayPowerAdvisorCard(
    capabilities: DeviceCapabilities,
    onOpenBatterySaverSettings: () -> Unit
) {
    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text("DISPLAY & POWER ADVISOR", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Display Refresh Rate (Hz) vs In-Game Render FPS", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                Text(
                    text = "• Display Refresh Rate (Hz): Hardware screen scanning frequency (${capabilities.currentRefreshRateHz.toInt()} Hz).\n" +
                        "• Game Render FPS: Frame rate produced per second by the game engine.\n" +
                        "• A high-Hz display does NOT force a 30 FPS game to render at 120 FPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("System Battery Saver Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    text = if (capabilities.isBatterySaverOn) "Battery Saver ACTIVE (Limits CPU & caps display Hz)" else "Battery Saver OFF (Full performance available)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capabilities.isBatterySaverOn) NeonOrange else NeonGreen
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onOpenBatterySaverSettings,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Power Settings", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DeveloperOptionsAssistantCard(
    onOpenDeveloperOptions: () -> Unit
) {
    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(18.dp))
                Text("DEVELOPER OPTIONS ASSISTANT", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }

            Button(
                onClick = onOpenDeveloperOptions,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Open Dev Options", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Text(
            text = "Technical guidance for user-configurable Developer Options. ZeroX Boost never changes protected developer flags automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        HorizontalDivider(color = DarkSurfaceBorder)

        DevOptionGuideItem(
            title = "Window, Transition & Animator Scale (Set to 0.5x)",
            explanation = "Speeds up system window transitions and menu popups. Note: Does not increase in-game render FPS.",
            isRecommended = true
        )

        DevOptionGuideItem(
            title = "Default USB Configuration (Set to Charging Only)",
            explanation = "Prevents unexpected MTP file-transfer dialog popups when plugging in charging cables during matches.",
            isRecommended = true
        )

        DevOptionGuideItem(
            title = "Disable Hardware Overlays",
            explanation = "Forces Android WindowManager to compose windows using GPU. Helpful if screen recorder overlay flickers.",
            isRecommended = false
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NeonRed.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings NOT Recommended For General Gaming", style = MaterialTheme.typography.labelLarge, color = NeonRed)
                Text(
                    "• Force 4x MSAA: Causes severe GPU power draw & thermal throttling on mobile devices.\n• Background Process Limit: Can crash background services or chat apps.\n• Disabling Security / ADB Protection: Exposes device to malware risks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun DevOptionGuideItem(
    title: String,
    explanation: String,
    isRecommended: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isRecommended) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (isRecommended) NeonGreen else NeonOrange,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun RestoreTransparencyCard(
    savedState: SavedSystemStateEntity?,
    currentState: SystemControlState,
    onRestoreOriginals: () -> Unit
) {
    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text("RESTORE & SYSTEM TRANSPARENCY", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        }

        if (savedState != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Recorded System Baseline (Saved before optimization)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    SpecRow("Applied Profile", savedState.appliedProfileName)
                    SpecRow("Original Screen Timeout", "${savedState.originalScreenTimeoutMs / 1000}s")
                    SpecRow("Original Auto-Rotate", if (savedState.originalAutoRotate == 1) "ON" else "LOCKED")
                    SpecRow("Original Brightness", "${savedState.originalBrightnessLevel} / 255")
                }
            }

            Button(
                onClick = onRestoreOriginals,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore Original System Settings", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = "No active optimization profile applied. System baseline will be recorded automatically when a profile is activated.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
