package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.ads.AdManager
import com.example.ads.AdMobConfig
import com.example.ads.GoogleAdMobManager
import com.example.data.*
import com.example.service.GamingOverlayService
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    overlayConfig: OverlayConfig = OverlayConfig(),
    adManager: AdManager = GoogleAdMobManager,
    onUpdateOverlayConfig: ((OverlayConfig) -> OverlayConfig) -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    onOpenPermissionCenter: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenHelpFaq: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val adState by adManager.adState.collectAsState()
    val entitlement by MembershipManager.entitlement.collectAsState()
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var privacyActionFeedback by remember { mutableStateOf<String?>(null) }
    var themeGateFeedback by remember { mutableStateOf<String?>(null) }
    var overlayActionFeedback by remember { mutableStateOf<String?>(null) }

    fun readOverlayPerm(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun readNotificationPerm(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

    var canDrawOverlay by remember { mutableStateOf(readOverlayPerm()) }
    var hasNotificationPerm by remember { mutableStateOf(readNotificationPerm()) }
    var permissionAudit by remember { mutableStateOf(PermissionManager.auditPermissions(context, activity)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted
        permissionAudit = PermissionManager.auditPermissions(context, activity)
    }

    val serviceRunningState by GamingOverlayService.isServiceRunning.collectAsState()
    val lastOverlayError by GamingOverlayService.lastOverlayError.collectAsState()
    val overlayLogs by GamingOverlayService.diagnosticLogs.collectAsState()
    var showLogsExpanded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlay = readOverlayPerm()
                hasNotificationPerm = readNotificationPerm()
                permissionAudit = PermissionManager.auditPermissions(context, activity)
                if (canDrawOverlay) {
                    overlayActionFeedback = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "Settings & Sidebar",
                    subtitle = "Appearance, overlay HUD, permissions & privacy",
                    icon = Icons.Default.Settings,
                    iconTint = NeonPurpleBright,
                    trailingContent = {
                        OutlinedButton(
                            onClick = onNavigateToPremium,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_pro_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright)
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = "ZeroX Boost Pro", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (entitlement.isPremium) "PRO Active" else "Upgrade Pro",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }

            // Permission Center Card
            item {
                GameBoostSurfaceCard(
                    modifier = Modifier.testTag("settings_permission_center_card"),
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
                            Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    text = "Permission Center",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${permissionAudit.grantedCount}/${permissionAudit.totalApplicableCount} applicable permissions granted",
                                    color = if (permissionAudit.hasOverlayPermission) NeonGreen else NeonOrange,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Button(
                            onClick = onOpenPermissionCenter,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_permission_center_button")
                        ) {
                            Text(
                                text = "Manage",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        permissionAudit.items.take(3).forEach { item ->
                            val chipColor = when (item.status) {
                                PermissionStatus.GRANTED -> NeonGreen
                                PermissionStatus.NOT_GRANTED -> NeonOrange
                                PermissionStatus.NOT_APPLICABLE -> TextMuted
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = chipColor.copy(alpha = 0.14f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${item.title.substringBefore(" ")}: ${item.status.label}",
                                    color = chipColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Global Gaming Sidebar Service Card
            item {
                GameBoostSurfaceCard(
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                            Column {
                                Text(text = "Display Over Other Apps", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    text = if (canDrawOverlay) "Granted" else "Required for floating game sidebar",
                                    color = if (canDrawOverlay) NeonGreen else NeonOrange,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (!canDrawOverlay) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val opened = PermissionManager.openPermissionSettings(
                                            context,
                                            AppPermissionType.OVERLAY
                                        )
                                        if (!opened) {
                                            overlayActionFeedback = "Open Android Settings > Apps > Display over other apps."
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_overlay_grant_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text(text = "Grant", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPerm) {
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foreground Notification", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("Required on Android 13+ for active sidebar status.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            TextButton(
                                onClick = {
                                    PermissionManager.markNotificationPermissionRequested(context)
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Allow", color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (canDrawOverlay) {
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Floating Gaming Sidebar", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    text = if (serviceRunningState) "Running over games • Tap or slide edge handle" else "Shows edge handle over Free Fire MAX & other games",
                                    color = if (serviceRunningState) NeonGreen else TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = serviceRunningState,
                                modifier = Modifier.testTag("settings_overlay_service_switch"),
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val res = GamingOverlayService.startOverlay(context)
                                        overlayActionFeedback = if (res.started) null else res.reason
                                        if (!res.started && !readOverlayPerm() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            canDrawOverlay = false
                                        }
                                    } else {
                                        GamingOverlayService.stopOverlay(context)
                                        GamingOverlayService.clearLastOverlayError()
                                        overlayActionFeedback = null
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NeonCyan)
                            )
                        }
                    }

                    val visibleError = overlayActionFeedback ?: lastOverlayError
                    if (!visibleError.isNullOrBlank()) {
                        Text(
                            text = visibleError,
                            color = NeonOrange,
                            fontSize = 11.sp,
                            modifier = Modifier.testTag("settings_overlay_error_text")
                        )
                    }
                }
            }

            item { SectionLabel("Appearance & Gaming Preferences", accentColor = NeonPurple) }

            // Overlay Appearance & Trigger Customization Card
            item {
                GameBoostSurfaceCard {
                    Text(text = "Overlay Trigger & HUD Customization", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Trigger Vertical Position", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Top", "Center", "Bottom").forEach { pos ->
                                FilterChip(
                                    selected = overlayConfig.triggerPosition == pos,
                                    onClick = { onUpdateOverlayConfig { it.copy(triggerPosition = pos) } },
                                    label = { Text(pos) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GamingBlue.copy(alpha = 0.25f),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Trigger Touch Width / Sensitivity", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(16 to "Slim (16dp)", 24 to "Standard (24dp)", 32 to "Wide (32dp)").forEach { (width, label) ->
                                FilterChip(
                                    selected = overlayConfig.triggerWidthDp == width,
                                    onClick = { onUpdateOverlayConfig { it.copy(triggerWidthDp = width) } },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GamingBlue.copy(alpha = 0.25f),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (entitlement.allowOverlayTheming) {
                                "Overlay Accent Theme (All Pro Themes Unlocked)"
                            } else {
                                "Overlay Accent Theme (Cyan Free • Purple/Green/OLED Pro)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Cyan", "Purple", "Green", "OLED").forEach { accent ->
                                val isProTheme = accent != "Cyan"
                                val isUnlocked = !isProTheme || entitlement.allowOverlayTheming
                                FilterChip(
                                    selected = overlayConfig.accentTheme == accent,
                                    onClick = {
                                        if (isUnlocked) {
                                            themeGateFeedback = null
                                            onUpdateOverlayConfig { it.copy(accentTheme = accent) }
                                        } else {
                                            themeGateFeedback = "$accent overlay theme requires ZeroX Boost Pro. Cyan remains free."
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (isProTheme && !entitlement.allowOverlayTheming) "$accent PRO" else accent,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GamingBlue.copy(alpha = 0.25f),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }
                        if (!themeGateFeedback.isNullOrBlank()) {
                            Text(
                                text = themeGateFeedback.orEmpty(),
                                color = NeonPurpleBright,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Default Panel Opacity", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                text = "${(overlayConfig.panelOpacity * 100).toInt()}%",
                                style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                color = NeonCyan
                            )
                        }
                        Slider(
                            value = overlayConfig.panelOpacity,
                            onValueChange = { alpha -> onUpdateOverlayConfig { it.copy(panelOpacity = alpha) } },
                            valueRange = 0.5f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open Overlay in Compact HUD Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Opens as a compact pill showing timer, temp, and latency.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = overlayConfig.defaultCompactMode,
                            onCheckedChange = { compact -> onUpdateOverlayConfig { it.copy(defaultCompactMode = compact) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NeonCyan)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Long Session Break Reminder", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = overlayConfig.longSessionReminderMinutes == mins,
                                    onClick = { onUpdateOverlayConfig { it.copy(longSessionReminderMinutes = mins) } },
                                    label = { Text("${mins} min") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonGreen
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Zero Usage Tracking Launch Mode Notice
            item {
                GameBoostSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy-First Session Launching", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                text = "Sessions start cleanly from Gaming Space or Command Center with zero cross-app usage tracking.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Overlay Diagnostic Logs & OEM Restrictions Card
            item {
                GameBoostSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Text(text = "Overlay Diagnostics & OEM Notes", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        TextButton(
                            onClick = { showLogsExpanded = !showLogsExpanded },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (showLogsExpanded) "Hide Logs" else "View Logs (${overlayLogs.size})", color = NeonCyan, fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "Android & OEM Notes: On Xiaomi/POCO/Redmi (MIUI/HyperOS), allow 'Display pop-up windows while running in the background' in App Info. Some full-screen games or OEM Game Turbo modes restrict third-party overlays without root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (showLogsExpanded) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (overlayLogs.isEmpty()) {
                                    Text("No overlay events recorded yet. Toggle the service above to trace WindowManager status.", color = TextMuted, fontSize = 10.5.sp)
                                } else {
                                    overlayLogs.take(12).forEach { entry ->
                                        Text(entry, style = TelemetryMonoStyle.copy(fontSize = 10.sp), color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Data & Privacy", accentColor = NeonGreen) }

            // Privacy, Consent & AdMob Transparency Card
            item {
                GameBoostSurfaceCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Privacy & Ad Consent",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (adManager.isAdFreeEntitled()) {
                            "Status: Ad-Free Pro entitlement active — ordinary ads are hidden."
                        } else {
                            "Consent: ${adState.consentStatusMessage}\nSDK: ${adState.sdkStatusMessage}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPrivacyPolicyDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_privacy_policy_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) {
                            Text("Privacy Policy", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val act = context.findActivity()
                                if (act != null && adState.isPrivacyOptionsRequired) {
                                    adManager.showPrivacyOptionsForm(act) { err ->
                                        privacyActionFeedback = err ?: "Consent preferences updated."
                                    }
                                } else {
                                    privacyActionFeedback = if (adState.isPrivacyOptionsRequired) {
                                        "Open from main activity to update UMP consent."
                                    } else {
                                        "UMP privacy options form is only required in regulated regions (e.g., EEA/UK)."
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_ad_privacy_options_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                        ) {
                            Text("Ad Privacy Options", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!privacyActionFeedback.isNullOrBlank()) {
                        Text(
                            text = privacyActionFeedback.orEmpty(),
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            // Developer AdMob Diagnostics & Live Status Section (Debug builds only)
            if (BuildConfig.DEBUG) {
                item {
                    GameBoostSurfaceCard(
                        modifier = Modifier.testTag("settings_admob_diagnostics_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "AdMob Diagnostics & Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (adState.isTestMode) NeonGreen.copy(alpha = 0.15f) else DarkSurfaceBorder
                            ) {
                                Text(
                                    text = if (adState.isTestMode) "TEST MODE" else "PRODUCTION",
                                    color = if (adState.isTestMode) NeonGreen else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mobile Ads SDK:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            val sdkLabel = if (adState.isSdkInitialized) {
                                if (adState.totalAdaptersCount > 0) {
                                    "Initialized (${adState.readyAdaptersCount}/${adState.totalAdaptersCount} ready)"
                                } else {
                                    "Initialized"
                                }
                            } else {
                                "Standby / Initializing"
                            }
                            Text(
                                text = sdkLabel,
                                color = if (adState.isSdkInitialized) NeonGreen else NeonOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UMP Can Request Ads:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = if (adState.canRequestAds) "Granted / Ready (true)" else "Pending / Restricted (false)",
                                color = if (adState.canRequestAds) NeonGreen else NeonOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ad Eligibility:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = when {
                                    adManager.isAdFreeEntitled() -> "Ad-Free (Pro User)"
                                    !adState.isNetworkAvailable -> "Suppressed (Offline)"
                                    !adState.canRequestAds -> "Suppressed (No Consent)"
                                    else -> "Eligible (Idle Browsing)"
                                },
                                color = if (!adManager.isAdFreeEntitled() && adState.canRequestAds && adState.isNetworkAvailable) NeonGreen else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Placements Live Status:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(
                                    text = "Home: ${adState.homeBannerStatus.name} | Native: ${adState.nativeSponsoredStatus.name}",
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                            val lastLoc = adState.lastBannerLocation
                            if (lastLoc != null) {
                                Text(
                                    text = "Latest Banner (${lastLoc.name}): ${adState.lastBannerStatus.name} | Reports Banner: ${adState.reportsBannerStatus.name}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ad Outcome Counters:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "Req: ${adState.totalRequestsCount} | Loaded: ${adState.totalLoadedCount} | NoFill: ${adState.noFillCount} | Err: ${adState.failedCount}",
                                style = TelemetryMonoStyle.copy(fontSize = 10.5.sp),
                                color = NeonCyan
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val errHeading = if (adState.lastErrorCode != null) {
                                "Last Load Outcome (Error Code ${adState.lastErrorCode}):"
                            } else {
                                "Last Load Outcome / Error:"
                            }
                            Text(errHeading, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBackground
                            ) {
                                Text(
                                    text = adState.lastAdError ?: "No errors recorded.",
                                    color = if (adState.lastAdError != null) NeonOrange else TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val act = context.findActivity()
                                if (act != null) {
                                    adManager.retryConsentAndSdkFlow(act)
                                    privacyActionFeedback = "Re-requesting UMP consent and initializing Mobile Ads..."
                                } else {
                                    privacyActionFeedback = "Activity unavailable for consent retry."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_retry_admob_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Retry Consent & SDK Initialization",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Active AdMob Configuration:",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "• App ID: ${AdMobConfig.currentAppId}",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "• Banner ID: ${AdMobConfig.currentBannerAdUnitId}",
                                color = NeonCyan,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "• Native Advanced ID: ${AdMobConfig.currentNativeAdUnitId}",
                                color = NeonGreen,
                                fontSize = 10.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val isTest = adManager.toggleAdMobTestMode()
                                privacyActionFeedback = if (isTest) "Switched to Official Google Sample Test Ad Units" else "Switched to Production AdMob IDs (pub-9880038173911569)"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_toggle_test_mode_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (adState.isTestMode) NeonGreen else NeonCyan)
                        ) {
                            Text(
                                text = if (adState.isTestMode) "Switch to Production IDs" else "Use Development Test IDs",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { SectionLabel("About & Support", accentColor = NeonCyan) }

            // About & Help FAQ Navigation + Engineering Transparency Card
            item {
                GameBoostSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenAbout,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_about_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("About ZeroX Boost", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenHelpFaq,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_help_faq_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Help & FAQ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    SettingRow(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                    )

                    SettingRow(
                        icon = Icons.Default.Security,
                        title = "Zero Root & Zero Game Tampering",
                        subtitle = "Runs strictly in standard Android user space. Never injects code or modifies game OBB/data files."
                    )

                    SettingRow(
                        icon = Icons.Default.Speed,
                        title = "Real Device Measurement Integrity",
                        subtitle = "Displays genuine PowerManager thermal status and TCP latency. We never show fake FPS numbers."
                    )
                }
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "ZeroX Boost Privacy Policy",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "• Local Telemetry Only: CPU, RAM, battery, thermal status, and saved game profiles remain stored locally in Room on your device.\n" +
                        "• Zero Usage Tracking: ZeroX Boost does not request Package Usage Access or track foreground activity across other apps.\n" +
                        "• Advertising & Consent: Non-intrusive banners on idle browsing screens use the Google Mobile Ads SDK and User Messaging Platform (UMP) where required.\n" +
                        "• Zero Gameplay Interference: Ads never appear during active gaming sessions, benchmarks, scans, tools, or inside the floating overlay.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = DarkSurfaceElevated
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
