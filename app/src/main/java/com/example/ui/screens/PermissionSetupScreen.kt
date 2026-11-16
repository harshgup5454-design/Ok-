package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

object OnboardingPreferences {
    private const val PREF_NAME = "gameboost_onboarding_prefs"
    private const val KEY_COMPLETED = "onboarding_completed"

    fun isCompleted(context: Context): Boolean {
        return try {
            context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)
        } catch (_: Exception) {
            false
        }
    }

    fun setCompleted(context: Context, completed: Boolean) {
        try {
            context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED, completed)
                .apply()
        } catch (_: Exception) {}
    }

    fun markCompleted(context: Context) = setCompleted(context, true)
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
fun PermissionSetupScreen(
    onFinishSetup: () -> Unit,
    isOnboardingMode: Boolean = true,
    previewSdkInt: Int = Build.VERSION.SDK_INT
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (!isOnboardingMode) {
        BackHandler { onFinishSetup() }
    }

    var audit by remember { mutableStateOf(PermissionManager.auditPermissions(context, activity, sdkInt = previewSdkInt)) }
    var activeRationaleItem by remember { mutableStateOf<PermissionDescriptor?>(null) }
    var settingsLaunchHint by remember { mutableStateOf<String?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        audit = PermissionManager.auditPermissions(context, activity, sdkInt = previewSdkInt)
    }

    DisposableEffect(lifecycleOwner, previewSdkInt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audit = PermissionManager.auditPermissions(context, activity, sdkInt = previewSdkInt)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun triggerPermissionAction(item: PermissionDescriptor) {
        settingsLaunchHint = null
        if (item.type == AppPermissionType.NOTIFICATIONS && item.isRuntimePermission && !item.isPermanentlyDenied) {
            PermissionManager.markNotificationPermissionRequested(context)
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val opened = PermissionManager.openPermissionSettings(context, item.type)
            if (!opened) {
                settingsLaunchHint = "Could not open system screen directly. Open Android Settings > Apps > Boost Game."
            }
        }
    }

    val readinessProgress = if (audit.totalApplicableCount > 0) {
        audit.grantedCount.toFloat() / audit.totalApplicableCount.toFloat()
    } else 1f

    GameBoostAppBackground {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag(if (isOnboardingMode) "permission_setup_screen" else "permission_center_screen"),
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(
                    color = DarkSurface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, DarkSurfaceBorder),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isOnboardingMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        OnboardingPreferences.setCompleted(context, true)
                                        onFinishSetup()
                                    },
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .heightIn(min = 50.dp)
                                        .testTag("onboarding_skip_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                ) {
                                    Text("Skip for Now", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }

                                PrimaryGradientButton(
                                    text = if (audit.hasOverlayPermission) "Enter GAMEBOOST" else "Continue",
                                    onClick = {
                                        OnboardingPreferences.setCompleted(context, true)
                                        onFinishSetup()
                                    },
                                    icon = Icons.Default.ArrowForward,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("onboarding_continue_button")
                                )
                            }
                        } else {
                            PrimaryGradientButton(
                                text = "Done & Return",
                                onClick = onFinishSetup,
                                icon = Icons.Default.Check,
                                modifier = Modifier.testTag("permission_center_done_button")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("permission_setup_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    if (isOnboardingMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    Image(
                                        painter = painterResource(id = R.drawable.gameboost_logo),
                                        contentDescription = "GAMEBOOST",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.5.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                    )
                                    Column {
                                        Text(
                                            text = "FIRST-TIME SETUP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Welcome to GAMEBOOST",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { audit = PermissionManager.auditPermissions(context, activity, sdkInt = previewSdkInt) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("permission_recheck_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Re-check Permissions", tint = NeonCyan)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OnboardingHighlightPill(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Speed,
                                    tint = NeonCyan,
                                    title = "Telemetry"
                                )
                                OnboardingHighlightPill(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Layers,
                                    tint = ElectricBlue,
                                    title = "Sidebar HUD"
                                )
                                OnboardingHighlightPill(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Restore,
                                    tint = NeonPurpleBright,
                                    title = "Autopilot"
                                )
                            }
                        }
                    } else {
                        GameBoostScreenHeader(
                            title = "Permission Center",
                            subtitle = "Live Android permission audit & transparent purpose",
                            onBackClick = onFinishSetup,
                            backButtonTestTag = "permission_center_back_button",
                            trailingContent = {
                                IconButton(
                                    onClick = { audit = PermissionManager.auditPermissions(context, activity, sdkInt = previewSdkInt) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("permission_recheck_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Re-check Permissions", tint = NeonCyan)
                                }
                            }
                        )
                    }
                }

                item {
                    GameBoostHeroCard(
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Permission Readiness",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (audit.hasOverlayPermission) {
                                        "Overlay ready — optional Autopilot permissions available anytime."
                                    } else {
                                        "Overlay is needed for the floating sidebar. Others are optional."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                            }
                            StatusPillBadge(
                                text = "${audit.grantedCount}/${audit.totalApplicableCount}",
                                color = if (audit.hasOverlayPermission) NeonGreen else NeonOrange
                            )
                        }

                        LinearProgressIndicator(
                            progress = { readinessProgress.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (audit.hasOverlayPermission) NeonGreen else NeonCyan,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                }

                if (!settingsLaunchHint.isNullOrBlank()) {
                    item {
                        GameBoostErrorBanner(
                            title = "Settings Shortcut",
                            message = settingsLaunchHint.orEmpty(),
                            isWarning = true
                        )
                    }
                }

                items(audit.items, key = { it.type.name }) { item ->
                    PermissionControlCard(
                        item = item,
                        onActionClick = { activeRationaleItem = item },
                        onOpenSettingsClick = { triggerPermissionAction(item) }
                    )
                }

                item {
                    GameBoostSurfaceCard(containerColor = DarkSurfaceElevated) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Privacy Promise: Diagnostics, Game Space launching, Benchmark Lab, Network Intelligence, and Aim Lab work with zero special permissions. No personal data is ever collected or uploaded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    activeRationaleItem?.let { item ->
        val settingsManaged = !item.isRuntimePermission || item.isPermanentlyDenied
        AlertDialog(
            onDismissRequest = { activeRationaleItem = null },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.testTag("permission_rationale_dialog"),
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeonCyan.copy(alpha = 0.14f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = item.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, DarkSurfaceBorder)
                    ) {
                        Text(
                            text = item.settingsStepHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = item
                        activeRationaleItem = null
                        triggerPermissionAction(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("permission_rationale_confirm")
                ) {
                    val confirmLabel = when {
                        item.isPermanentlyDenied -> "Open App Settings"
                        settingsManaged -> "Open Android Settings"
                        else -> "Allow Permission"
                    }
                    Text(
                        text = confirmLabel,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeRationaleItem = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("permission_rationale_cancel")
                ) {
                    Text("Not Now", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun OnboardingHighlightPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    title: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionControlCard(
    item: PermissionDescriptor,
    onActionClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val keySlug = item.type.name.lowercase(Locale.US)
    val badgeColor = when (item.status) {
        PermissionStatus.GRANTED -> NeonGreen
        PermissionStatus.NOT_GRANTED -> if (item.isRequiredForOverlay) NeonOrange else NeonCyan
        PermissionStatus.NOT_APPLICABLE -> TextMuted
    }

    GameBoostSurfaceCard(
        modifier = Modifier.testTag("permission_card_$keySlug"),
        borderColor = if (item.status == PermissionStatus.GRANTED) NeonGreen.copy(alpha = 0.35f) else DarkSurfaceBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = if (item.isRequiredForOverlay) "Essential for Overlay • ${item.featurePurpose}" else item.featurePurpose,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isRequiredForOverlay) NeonOrange else TextMuted
                )
            }

            StatusPillBadge(
                text = item.status.label,
                color = badgeColor,
                modifier = Modifier
                    .semantics(mergeDescendants = true) {}
                    .testTag("permission_status_$keySlug")
            )
        }

        Text(
            text = item.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        when (item.status) {
            PermissionStatus.NOT_GRANTED -> {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("permission_action_$keySlug"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    val actionLabel = if (item.isPermanentlyDenied) "Open App Settings" else "Enable ${item.title}"
                    Text(actionLabel, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
            PermissionStatus.GRANTED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Text("Active & Authorized", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                    }
                    TextButton(
                        onClick = onOpenSettingsClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("permission_action_$keySlug")
                    ) {
                        Text("System Settings", color = TextSecondary, fontSize = 11.5.sp)
                    }
                }
            }
            PermissionStatus.NOT_APPLICABLE -> {
                Text(
                    text = "Not needed on this Android version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
