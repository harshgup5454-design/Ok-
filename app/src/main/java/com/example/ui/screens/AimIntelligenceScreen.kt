package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.hypot
import kotlin.random.Random

data class TargetNode(
    val id: Int,
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val spawnTime: Long,
    val movementPattern: String
)

@Composable
fun AimIntelligenceScreen(
    savedProfiles: List<SensitivityProfile>,
    trainingHistory: List<AimTrainingSessionStats>,
    calibrationHistory: List<RotationCalibrationAttempt>,
    onSaveProfile: (SensitivityProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDuplicateProfile: (SensitivityProfile) -> Unit,
    onSaveCalibration: (RotationCalibrationAttempt) -> Unit,
    onSaveTrainingSession: (AimTrainingSessionStats) -> Unit,
    smartAimControlState: SmartAimControlState = SmartAimControlState(),
    gyroscopeState: GyroscopeTelemetry = GyroscopeTelemetry(),
    deviceSpecs: AimDeviceSpecs? = null,
    onMasterSwitchChanged: (Boolean) -> Unit = {},
    onModeSelected: (AimOptimizationMode) -> Unit = {},
    onGameSelected: (String) -> Unit = {},
    onApplyPreset: (String, AimOptimizationMode) -> Unit = { _, _ -> },
    onCalibrateGyro: () -> Unit = {},
    onSelectSavedProfile: (SensitivityProfile) -> Unit = {},
    onExportProfilesJson: () -> String = { "[]" },
    onImportProfilesJson: (String) -> Int = { 0 },
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    initialTab: Int = 0,
    onBackClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 5)) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }

    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("aim_intelligence_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "SMART AIM & CONTROL",
                    subtitle = "Sensitivity calculator, gyro calibration & precision trainer",
                    icon = Icons.Default.GpsFixed,
                    iconTint = NeonRed,
                    onBackClick = onBackClick,
                    backButtonTestTag = "aim_back_button"
                )
            }

            // Styled Sub-Tabs (2 compact rows inside DarkSurface card)
            item {
                GameBoostSurfaceCard(
                    containerColor = DarkSurface,
                    borderColor = DarkSurfaceBorder,
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AimTabPill("Optimizer", selectedTab == 0, NeonCyan, { selectedTab = 0 }, Modifier.weight(1f))
                        AimTabPill("Calculator", selectedTab == 1, NeonPurpleBright, { selectedTab = 1 }, Modifier.weight(1f))
                        AimTabPill("Rotation", selectedTab == 2, NeonGreen, { selectedTab = 2 }, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AimTabPill("Aim Lab", selectedTab == 3, NeonRed, { selectedTab = 3 }, Modifier.weight(1f))
                        AimTabPill("Analytics", selectedTab == 4, NeonPurpleBright, { selectedTab = 4 }, Modifier.weight(1f))
                        AimTabPill("HUD Guide", selectedTab == 5, NeonCyan, { selectedTab = 5 }, Modifier.weight(1f))
                    }
                }
            }

            item {
                when (selectedTab) {
                    0 -> SmartAimControlCenterSection(
                        controlState = smartAimControlState,
                        gyroState = gyroscopeState,
                        deviceSpecs = deviceSpecs,
                        savedProfiles = savedProfiles,
                        onMasterSwitchChanged = onMasterSwitchChanged,
                        onModeSelected = onModeSelected,
                        onGameSelected = onGameSelected,
                        onApplyPreset = onApplyPreset,
                        onCalibrateGyro = onCalibrateGyro,
                        onSelectProfile = onSelectSavedProfile,
                        onExportProfiles = {
                            exportedText = onExportProfilesJson()
                            showExportDialog = true
                        },
                        onImportProfiles = {
                            importInputText = ""
                            showImportDialog = true
                        }
                    )
                    1 -> SensitivityCalculatorSection(savedProfiles, onSaveProfile, onDeleteProfile, onDuplicateProfile)
                    2 -> RotationCalibrationSection(calibrationHistory, onSaveCalibration)
                    3 -> AimTrainerMiniGameSection(
                        isPremium = isPremium,
                        isAutopilotActive = isAutopilotActive,
                        thermalSeverity = thermalSeverity,
                        adManager = adManager,
                        onSaveSession = onSaveTrainingSession
                    )
                    4 -> AccuracyAnalyticsSection(history = trainingHistory)
                    5 -> HudAdvisorSection()
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = DarkSurfaceElevated,
            title = { Text("Export Sensitivity Profiles", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copy this JSON backup to share or restore your saved sensitivity profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = exportedText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = TelemetryMonoStyle.copy(color = TextPrimary, fontSize = 10.5.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(exportedText))
                        android.widget.Toast.makeText(context, "Profiles JSON copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Copy JSON", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = DarkSurfaceElevated,
            title = { Text("Import Sensitivity Profiles", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste exported ZeroX Boost profile JSON below to save & apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { Text("[{\"profileName\":\"My Profile\", ...}]", color = TextMuted, fontSize = 10.5.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        textStyle = TelemetryMonoStyle.copy(color = TextPrimary, fontSize = 10.5.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = onImportProfilesJson(importInputText)
                        if (count > 0) {
                            android.widget.Toast.makeText(context, "Imported $count profile(s)", android.widget.Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        } else {
                            android.widget.Toast.makeText(context, "Invalid JSON profile format", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Import", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AimTabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else DarkSurfaceBorder)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SensitivityCalculatorSection(
    savedProfiles: List<SensitivityProfile>,
    onSaveProfile: (SensitivityProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDuplicateProfile: (SensitivityProfile) -> Unit
) {
    var selectedGame by remember { mutableStateOf("BGMI / PUBG Mobile") }
    var selectedStyle by remember { mutableStateOf("Balanced") }
    var selectedDpi by remember { mutableStateOf(400) }
    var profileName by remember { mutableStateOf("Custom Pro Profile") }
    var showSavedProfiles by remember { mutableStateOf(false) }

    val games = listOf("BGMI / PUBG Mobile", "Free Fire MAX", "Call of Duty: Mobile")
    val styles = listOf("Low Sensitivity / Arm Swipes", "Balanced", "High Sensitivity / Thumb Swipes")
    val dpiTiers = listOf(320, 400, 480, 560)

    val currentProfile = remember(selectedGame, selectedStyle, selectedDpi) {
        AimIntelligenceRepository.calculateSensitivity(selectedGame, selectedStyle, selectedDpi)
    }

    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Advanced Sensitivity Calculator", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            TextButton(onClick = { showSavedProfiles = !showSavedProfiles }, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(
                    text = if (showSavedProfiles) "Calculator" else "Saved (${savedProfiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
            }
        }

        if (showSavedProfiles) {
            SectionLabel(text = "Saved Sensitivity Profiles", accentColor = NeonCyan)
            if (savedProfiles.isEmpty()) {
                GameBoostEmptyState(
                    icon = Icons.Default.Tune,
                    title = "No saved sensitivity profiles",
                    description = "Save your calculated sensitivity baseline to access it quickly.",
                    accentColor = NeonCyan
                )
            } else {
                savedProfiles.forEach { profile ->
                    GameBoostSurfaceCard(
                        containerColor = DarkSurfaceElevated,
                        borderColor = DarkSurfaceBorder,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = profile.profileName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onDuplicateProfile(profile) }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = NeonCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteProfile(profile.id) }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = NeonRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(text = "${profile.gameName} • ${profile.sensitivityStyle} • ${profile.dpiTier} DPI", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Gen: ${profile.general}", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonCyan)
                            Text(text = "RedDot: ${profile.redDot}", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonGreen)
                            Text(text = "2x: ${profile.scope2x}", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonPurpleBright)
                            Text(text = "4x: ${profile.scope4x}", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonOrange)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Select Game Title", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    games.forEach { game ->
                        FilterChip(
                            selected = selectedGame == game,
                            onClick = { selectedGame = game },
                            label = { Text(game.take(10), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f), selectedLabelColor = NeonCyan),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Camera Movement Style", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                styles.forEach { style ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedStyle == style) NeonCyan.copy(alpha = 0.1f) else DarkSurfaceElevated,
                        border = BorderStroke(1.dp, if (selectedStyle == style) NeonCyan else DarkSurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedStyle = style }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                            )
                            Text(text = style, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Device Screen DPI Tier", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dpiTiers.forEach { dpi ->
                        FilterChip(
                            selected = selectedDpi == dpi,
                            onClick = { selectedDpi = dpi },
                            label = { Text("$dpi DPI", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen.copy(alpha = 0.2f), selectedLabelColor = NeonGreen),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkSurfaceBorder)

            Text(text = "Recommended Scope Sensitivities", style = MaterialTheme.typography.titleSmall, color = NeonCyan)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensBox("General", "${currentProfile.general}", Modifier.weight(1f))
                SensBox("Red Dot", "${currentProfile.redDot}", Modifier.weight(1f))
                SensBox("2x Scope", "${currentProfile.scope2x}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensBox("4x Scope", "${currentProfile.scope4x}", Modifier.weight(1f))
                SensBox("Sniper 8x", "${currentProfile.sniper}", Modifier.weight(1f))
                SensBox("Free Look", "${currentProfile.freeLook}", Modifier.weight(1f))
            }

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Profile Name", color = TextMuted, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, unfocusedBorderColor = DarkSurfaceBorder),
                singleLine = true
            )

            PrimaryGradientButton(
                text = "Save Sensitivity Profile",
                icon = Icons.Default.Save,
                onClick = {
                    onSaveProfile(currentProfile.copy(profileName = profileName.ifBlank { "Custom Profile" }))
                }
            )

            HorizontalDivider(color = DarkSurfaceBorder)

            Text(text = "Transparent Algorithmic Rationale & Notice", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = "${currentProfile.mathematicalRationale}\n\nDisclaimer: ZeroX Boost touch recommendations are mathematical baselines. Always verify and test sensitivity in your in-game training room.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SensBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 16.sp), color = NeonCyan)
        }
    }
}

@Composable
fun RotationCalibrationSection(
    calibrationHistory: List<RotationCalibrationAttempt>,
    onSaveCalibration: (RotationCalibrationAttempt) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }
    var lastAttempt by remember { mutableStateOf<RotationCalibrationAttempt?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Touch Rotation Calibration Pad", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(
                    text = if (showHistory) "Calibrate" else "History (${calibrationHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen
                )
            }
        }

        if (showHistory) {
            SectionLabel(text = "Calibration Attempts Log", accentColor = NeonGreen)
            if (calibrationHistory.isEmpty()) {
                GameBoostEmptyState(
                    icon = Icons.Default.Swipe,
                    title = "No calibration attempts recorded",
                    description = "Swipe across the calibration pad to measure horizontal flick speed and path straightness.",
                    accentColor = NeonGreen
                )
            } else {
                calibrationHistory.forEach { att ->
                    GameBoostSurfaceCard(
                        containerColor = DarkSurfaceElevated,
                        borderColor = DarkSurfaceBorder,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = att.recommendedStyle, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(text = "${att.avgSpeedPxPerSec.toInt()} px/s", style = TelemetryMonoStyle.copy(fontSize = 12.5.sp), color = NeonCyan)
                        }
                        Text(
                            text = "Duration: ${att.durationMs}ms • Consistency: ${att.consistencyScore}% • Rec Gen: ${att.recommendedGeneralSensitivity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Swipe horizontally across the pad below at your natural aiming speed to measure velocity, swipe duration, and straightness.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            var trackedDistancePx by remember { mutableStateOf(0f) }
            var trackedNetDx by remember { mutableStateOf(0f) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                startTime = System.currentTimeMillis()
                                trackedDistancePx = 0f
                                trackedNetDx = 0f
                                isRecording = true
                            },
                            onDragEnd = {
                                if (isRecording) {
                                    val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                                    val distance = trackedDistancePx
                                    if (distance >= 20f) {
                                        val speed = (distance / duration) * 1000f
                                        val consistency = ((kotlin.math.abs(trackedNetDx) / distance) * 100f)
                                            .coerceIn(10f, 100f)

                                        val style = when {
                                            speed < 400f -> "Slow Precision Sweep"
                                            speed < 900f -> "Balanced Rotation"
                                            speed < 1500f -> "Fast Swipe"
                                            else -> "Very Fast Flick"
                                        }

                                        val genSens = when {
                                            speed < 400f -> 95
                                            speed < 900f -> 75
                                            speed < 1500f -> 55
                                            else -> 40
                                        }

                                        val attempt = RotationCalibrationAttempt(
                                            id = "cal_${System.currentTimeMillis()}",
                                            timestamp = System.currentTimeMillis(),
                                            swipeDistancePx = distance,
                                            durationMs = duration,
                                            avgSpeedPxPerSec = speed,
                                            consistencyScore = consistency,
                                            recommendedStyle = style,
                                            recommendedGeneralSensitivity = genSens,
                                            advice = "Measured ${distance.toInt()} px swipe (${speed.toInt()} px/s over ${duration}ms). Path straightness: ${consistency.toInt()}%. Recommended general sensitivity: $genSens."
                                        )
                                        lastAttempt = attempt
                                        onSaveCalibration(attempt)
                                    }
                                    isRecording = false
                                }
                            },
                            onDragCancel = { isRecording = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                trackedDistancePx += hypot(dragAmount.x, dragAmount.y)
                                trackedNetDx += dragAmount.x
                            }
                        )
                    },
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.5.dp, if (isRecording) NeonGreen else DarkSurfaceBorderBright)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Gesture else Icons.Default.Swipe,
                        contentDescription = null,
                        tint = if (isRecording) NeonGreen else NeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isRecording) "Recording Swipe Velocity..." else "SWIPE HORIZONTALLY HERE",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isRecording) NeonGreen else TextPrimary
                    )
                    Text(
                        text = "Test your thumb or arm flick speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            lastAttempt?.let { res ->
                HorizontalDivider(color = DarkSurfaceBorder)
                Text(text = "Latest Calibration Result", style = MaterialTheme.typography.titleSmall, color = NeonGreen)
                SpecRow("Style", res.recommendedStyle)
                SpecRow("Velocity & Duration", "${res.avgSpeedPxPerSec.toInt()} px/s (${res.durationMs}ms)")
                SpecRow("Consistency Score", "${res.consistencyScore}%")
                Text(text = res.advice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AimTrainerMiniGameSection(
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onSaveSession: (AimTrainingSessionStats) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var targetsHit by remember { mutableStateOf(0) }
    var targetsSpawned by remember { mutableStateOf(0) }
    var missCount by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var durationSec by remember { mutableStateOf(30) }
    var timeLeft by remember { mutableStateOf(30) }
    var difficulty by remember { mutableStateOf("Medium") }
    var targetSize by remember { mutableStateOf("Medium") }
    var targetMovement by remember { mutableStateOf("Moving (Drift)") }

    val reactionTimes = remember { mutableStateListOf<Long>() }
    var currentTarget by remember { mutableStateOf<TargetNode?>(null) }
    var sessionFinished by remember { mutableStateOf(false) }
    var lastAccuracy by remember { mutableStateOf(0f) }
    var lastAvgReaction by remember { mutableStateOf(0) }

    val adState by adManager.adState.collectAsState()
    val aimVisibility = AdVisibilityContext(
        location = AdScreenLocation.AIM_TRAINER,
        isGamingSessionActive = isAutopilotActive || isPlaying,
        isAutopilotSessionActive = isAutopilotActive,
        isAimTrainingActive = isPlaying,
        thermalSeverity = thermalSeverity
    )
    val shouldShowAimBanner = !isPlaying && !isPremium && remember(adState, aimVisibility) {
        adManager.canDisplayAds(aimVisibility)
    }

    LaunchedEffect(isPlaying, timeLeft) {
        if (isPlaying && timeLeft > 0) {
            delay(1000L)
            timeLeft--
            if (timeLeft == 0) {
                isPlaying = false
                sessionFinished = true
                val totalAttempts = (targetsHit + missCount).coerceAtLeast(1)
                lastAccuracy = (targetsHit.toFloat() / totalAttempts.toFloat()) * 100f
                lastAvgReaction = if (reactionTimes.isNotEmpty()) reactionTimes.average().toInt() else 320

                onSaveSession(
                    AimTrainingSessionStats(
                        id = "train_${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = durationSec,
                        difficulty = difficulty,
                        targetSize = targetSize,
                        targetMovement = targetMovement,
                        targetsSpawned = targetsSpawned,
                        targetsHit = targetsHit,
                        accuracyPercent = lastAccuracy,
                        avgReactionTimeMs = lastAvgReaction,
                        bestStreak = bestStreak,
                        missCount = missCount,
                        score = score
                    )
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shouldShowAimBanner) {
            AdMobAdaptiveBannerSlot(
                visibilityContext = aimVisibility,
                adManager = adManager,
                testTagName = "aim_trainer_ad_banner_slot"
            )
        }

        GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Polished Aim Trainer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                StatusPillBadge(
                    text = if (isPlaying) "TIME: ${timeLeft}s" else "READY",
                    color = NeonRed,
                    showDot = isPlaying
                )
            }

            if (!isPlaying && !sessionFinished) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Difficulty Level", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Easy", "Medium", "Hard", "Expert").forEach { diff ->
                            FilterChip(
                                selected = difficulty == diff,
                                onClick = { difficulty = diff },
                                label = { Text(diff, fontSize = 10.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f), selectedLabelColor = NeonCyan),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text(text = "Target Size & Movement", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Small", "Medium", "Large").forEach { sz ->
                            FilterChip(
                                selected = targetSize == sz,
                                onClick = { targetSize = sz },
                                label = { Text(sz, fontSize = 10.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen.copy(alpha = 0.2f), selectedLabelColor = NeonGreen),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Stationary", "Moving (Drift)", "Pulsing").forEach { mv ->
                            FilterChip(
                                selected = targetMovement == mv,
                                onClick = { targetMovement = mv },
                                label = { Text(mv, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonPurple.copy(alpha = 0.2f), selectedLabelColor = NeonPurpleBright),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text(text = "Practice Duration", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = durationSec == sec,
                                onClick = { durationSec = sec; timeLeft = sec },
                                label = { Text("${sec}s", style = TelemetryMonoStyle.copy(fontSize = 11.sp)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f), selectedLabelColor = NeonCyan),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            score = 0
                            targetsHit = 0
                            targetsSpawned = 1
                            missCount = 0
                            currentStreak = 0
                            bestStreak = 0
                            timeLeft = durationSec
                            reactionTimes.clear()
                            sessionFinished = false
                            isPlaying = true

                            val radius = when (targetSize) {
                                "Small" -> 24f
                                "Large" -> 55f
                                else -> 38f
                            }
                            currentTarget = TargetNode(1, Random.nextFloat() * 220f + 40f, Random.nextFloat() * 160f + 40f, radius, System.currentTimeMillis(), targetMovement)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Aim Training (${durationSec}s)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isPlaying) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Score: $score", style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = NeonCyan)
                    Text(text = "Streak: $currentStreak (Best $bestStreak)", style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = NeonGreen)
                    Text(text = "Misses: $missCount", style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = NeonOrange)
                }

                var arenaWidthPx by remember { mutableStateOf(0f) }
                var arenaHeightPx by remember { mutableStateOf(0f) }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.5.dp, NeonRed.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .onSizeChanged {
                            arenaWidthPx = it.width.toFloat()
                            arenaHeightPx = it.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val target = currentTarget
                                if (target != null) {
                                    val distance = hypot(offset.x - target.initialX, offset.y - target.initialY)
                                    if (distance <= target.radius * 1.5f) {
                                        targetsHit++
                                        currentStreak++
                                        if (currentStreak > bestStreak) bestStreak = currentStreak
                                        score += when (difficulty) {
                                            "Expert" -> 200
                                            "Hard" -> 150
                                            "Medium" -> 100
                                            else -> 50
                                        }
                                        val reaction = System.currentTimeMillis() - target.spawnTime
                                        reactionTimes.add(reaction)

                                        targetsSpawned++
                                        val radius = when (targetSize) {
                                            "Small" -> 24f
                                            "Large" -> 55f
                                            else -> 38f
                                        }
                                        val margin = radius * 1.6f
                                        val usableW = (arenaWidthPx - margin * 2f).coerceAtLeast(180f)
                                        val usableH = (arenaHeightPx - margin * 2f).coerceAtLeast(140f)
                                        currentTarget = TargetNode(
                                            targetsSpawned,
                                            margin + Random.nextFloat() * usableW,
                                            margin + Random.nextFloat() * usableH,
                                            radius,
                                            System.currentTimeMillis(),
                                            targetMovement
                                        )
                                    } else {
                                        missCount++
                                        currentStreak = 0
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        currentTarget?.let { t ->
                            drawCircle(color = NeonRed.copy(alpha = 0.25f), radius = t.radius * 1.5f, center = Offset(t.initialX, t.initialY))
                            drawCircle(color = NeonRed, radius = t.radius, center = Offset(t.initialX, t.initialY))
                            drawCircle(color = Color.White, radius = t.radius * 0.35f, center = Offset(t.initialX, t.initialY))
                        }
                    }
                }
            } else if (sessionFinished) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(44.dp))
                    Text(text = "Training Session Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                    SpecRow("Final Score", "$score")
                    SpecRow("Accuracy", "${String.format(Locale.US, "%.1f", lastAccuracy)}%")
                    SpecRow("Avg Reaction Time", "$lastAvgReaction ms")
                    SpecRow("Best Streak", "$bestStreak hits")
                    SpecRow("Targets Hit / Missed", "$targetsHit / $missCount")

                    PrimaryGradientButton(
                        text = "Train Again",
                        icon = Icons.Default.Refresh,
                        onClick = { sessionFinished = false }
                    )
                }
            }
        }
    }
}

@Composable
fun AccuracyAnalyticsSection(history: List<AimTrainingSessionStats>) {
    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Text(text = "Aim Training History & Analytics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        if (history.isEmpty()) {
            GameBoostEmptyState(
                icon = Icons.Default.GpsFixed,
                title = "No training sessions recorded yet",
                description = "Play a round in the Aim Lab tab to log accuracy, streak, and reaction time metrics.",
                accentColor = NeonRed
            )
        } else {
            history.forEach { session ->
                GameBoostSurfaceCard(
                    containerColor = DarkSurfaceElevated,
                    borderColor = DarkSurfaceBorder,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${session.difficulty} • ${session.targetSize} (${session.targetMovement})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(text = "Score: ${session.score}", style = TelemetryMonoStyle.copy(fontSize = 12.5.sp), color = NeonCyan)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Accuracy: ${String.format(Locale.US, "%.1f", session.accuracyPercent)}%", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonGreen)
                        Text(text = "Reaction: ${session.avgReactionTimeMs}ms", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonPurpleBright)
                        Text(text = "Streak: ${session.bestStreak}", style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = NeonGreen)
                    }
                }
            }
        }
    }
}

@Composable
fun HudAdvisorSection() {
    val adviceList = remember { AimIntelligenceRepository.getHudAdviceList() }

    GameBoostSurfaceCard(containerColor = DarkSurface, borderColor = DarkSurfaceBorder) {
        Text(text = "HUD & Control Layout Advisor", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            text = "Optimized ergonomic rules for multi-finger claw layouts and thumb placement.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        adviceList.forEach { advice ->
            GameBoostSurfaceCard(
                containerColor = DarkSurfaceElevated,
                borderColor = DarkSurfaceBorder,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(text = advice.title, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                Text(text = advice.recommendation, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text(text = advice.technicalRationale, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
