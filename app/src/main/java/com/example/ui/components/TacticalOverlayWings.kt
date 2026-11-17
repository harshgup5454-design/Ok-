package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EntitlementState
import com.example.ui.theme.*
import kotlin.math.min

enum class WingSide {
    LEFT,
    RIGHT
}

@Composable
fun rememberChamferedWingShape(
    side: WingSide,
    bevelDp: Dp = 18.dp,
    notchDp: Dp = 8.dp
): Shape {
    val density = LocalDensity.current
    val bevelPx = with(density) { bevelDp.toPx() }
    val notchPx = with(density) { notchDp.toPx() }
    return GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val bevel = min(bevelPx, w * 0.20f)
        val notch = min(notchPx, w * 0.08f)
        if (side == WingSide.LEFT) {
            moveTo(0f, 0f)
            lineTo(w - bevel, 0f)
            lineTo(w, bevel * 1.15f)
            lineTo(w, h * 0.36f)
            lineTo(w - notch, h * 0.44f)
            lineTo(w - notch, h * 0.56f)
            lineTo(w, h * 0.64f)
            lineTo(w, h - bevel * 1.15f)
            lineTo(w - bevel, h)
            lineTo(0f, h)
            close()
        } else {
            moveTo(w, 0f)
            lineTo(bevel, 0f)
            lineTo(0f, bevel * 1.15f)
            lineTo(0f, h * 0.36f)
            lineTo(notch, h * 0.44f)
            lineTo(notch, h * 0.56f)
            lineTo(0f, h * 0.64f)
            lineTo(0f, h - bevel * 1.15f)
            lineTo(bevel, h)
            lineTo(w, h)
            close()
        }
    }
}

internal fun Modifier.tacticalWingFrame(
    side: WingSide,
    accentColor: Color,
    bevelDp: Dp = 18.dp,
    notchDp: Dp = 8.dp
): Modifier = drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    val bevel = min(bevelDp.toPx(), w * 0.20f)
    val notch = min(notchDp.toPx(), w * 0.08f)
    val strokeW = 1.6.dp.toPx()

    val innerEdge = Path().apply {
        if (side == WingSide.LEFT) {
            moveTo(w - bevel, 1.dp.toPx())
            lineTo(w - 1.dp.toPx(), bevel * 1.15f)
            lineTo(w - 1.dp.toPx(), h * 0.36f)
            lineTo(w - notch - 1.dp.toPx(), h * 0.44f)
            lineTo(w - notch - 1.dp.toPx(), h * 0.56f)
            lineTo(w - 1.dp.toPx(), h * 0.64f)
            lineTo(w - 1.dp.toPx(), h - bevel * 1.15f)
            lineTo(w - bevel, h - 1.dp.toPx())
        } else {
            moveTo(bevel, 1.dp.toPx())
            lineTo(1.dp.toPx(), bevel * 1.15f)
            lineTo(1.dp.toPx(), h * 0.36f)
            lineTo(notch + 1.dp.toPx(), h * 0.44f)
            lineTo(notch + 1.dp.toPx(), h * 0.56f)
            lineTo(1.dp.toPx(), h * 0.64f)
            lineTo(1.dp.toPx(), h - bevel * 1.15f)
            lineTo(bevel, h - 1.dp.toPx())
        }
    }

    drawPath(
        path = innerEdge,
        brush = Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = 0.9f),
                GamingBlue.copy(alpha = 0.55f),
                accentColor.copy(alpha = 0.9f)
            )
        ),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    val ventCount = 4
    for (i in 0 until ventCount) {
        val yOffset = h - (14 + i * 6).dp.toPx()
        val ventLen = (14 + i * 3).dp.toPx()
        if (side == WingSide.LEFT) {
            val xEnd = w - 12.dp.toPx()
            drawLine(
                color = accentColor.copy(alpha = 0.25f + i * 0.08f),
                start = Offset(xEnd - ventLen, yOffset),
                end = Offset(xEnd, yOffset - 3.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            val xStart = 12.dp.toPx()
            drawLine(
                color = accentColor.copy(alpha = 0.25f + i * 0.08f),
                start = Offset(xStart, yOffset - 3.dp.toPx()),
                end = Offset(xStart + ventLen, yOffset),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun TacticalArcGauge(
    label: String,
    valueText: String,
    subText: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.5.dp.toPx()
                    val pad = stroke / 2f + 2.dp.toPx()
                    val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                    drawArc(
                        color = DarkSurfaceBorder,
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(accentColor.copy(alpha = 0.65f), accentColor, GamingBlue)
                        ),
                        startAngle = 140f,
                        sweepAngle = (260f * progress.coerceIn(0.05f, 1f)),
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = valueText,
                        style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LandscapeLeftWing(
    accentColor: Color,
    overlayAlpha: Float,
    targetGameTitle: String,
    refreshRateHz: Float,
    memoryUsage: Int,
    availMemory: Float,
    batteryTemp: Float,
    thermalStatus: String,
    protectionAlertHeadline: String?,
    autopilotStatusLabel: String,
    selectedProfileMode: String,
    isAimTrainerQueued: Boolean,
    aimTrainerStatusHint: String?,
    onSelectProfile: (String) -> Unit,
    onRefreshPing: (() -> Unit)?,
    onOpenNetworkTools: (() -> Unit)?,
    onOpenSensitivityCalc: (() -> Unit)?,
    onRequestAimTrainer: (() -> Unit)?,
    onCancelAimTrainerQueue: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val wingShape = rememberChamferedWingShape(WingSide.LEFT)
    Box(
        modifier = modifier
            .clip(wingShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        DarkBackground.copy(alpha = overlayAlpha.coerceAtLeast(0.86f)),
                        DeepNavyBg.copy(alpha = (overlayAlpha - 0.04f).coerceAtLeast(0.80f))
                    )
                )
            )
            .tacticalWingFrame(WingSide.LEFT, accentColor)
            .testTag("overlay_left_wing")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                .testTag("overlay_sidebar_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "ZeroX BOOST",
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ZeroX BOOST",
                                fontFamily = SpaceGroteskFontFamily,
                                color = accentColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = targetGameTitle.ifBlank { "Game HUD" },
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NeonGreen.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "${refreshRateHz.toInt()} Hz",
                            style = TelemetryMonoStyle.copy(fontSize = 9.5.sp),
                            color = NeonGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            item {
                val hasBatteryTemp = batteryTemp > 0f
                val tempProgress = if (hasBatteryTemp) ((batteryTemp - 25f) / 22f).coerceIn(0.12f, 1f) else 0.05f
                val tempTint = if (!hasBatteryTemp) TextSecondary else if (batteryTemp >= 39.5f || thermalStatus.contains("Severe") || thermalStatus.contains("Critical")) NeonRed else NeonGreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TacticalArcGauge(
                        label = "RAM",
                        valueText = "$memoryUsage%",
                        subText = "${availMemory}G free",
                        progress = memoryUsage / 100f,
                        accentColor = if (memoryUsage > 85) NeonOrange else accentColor,
                        modifier = Modifier.weight(1f)
                    )
                    TacticalArcGauge(
                        label = "TEMP",
                        valueText = if (hasBatteryTemp) "${batteryTemp}°C" else "N/A",
                        subText = thermalStatus.take(8),
                        progress = tempProgress,
                        accentColor = tempTint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!protectionAlertHeadline.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonOrange.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(13.dp))
                            Text(
                                text = protectionAlertHeadline,
                                color = NeonOrange,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = autopilotStatusLabel.take(22),
                        color = TextMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Stability", "Balanced", "Battery").forEach { mode ->
                            val selected = selectedProfileMode.equals(mode, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable { onSelectProfile(mode) },
                                color = if (selected) accentColor.copy(alpha = 0.22f) else DarkSurfaceVariant,
                                border = BorderStroke(1.dp, if (selected) accentColor else DarkSurfaceBorder),
                                shape = RoundedCornerShape(7.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode,
                                        color = if (selected) accentColor else TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("overlay_network_button"),
                            icon = Icons.Default.NetworkCheck,
                            label = "Ping / Net",
                            accentColor = accentColor,
                            onClick = {
                                onRefreshPing?.invoke()
                                onOpenNetworkTools?.invoke()
                            }
                        )
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("overlay_sensitivity_button"),
                            icon = Icons.Default.Calculate,
                            label = "Sensitivity",
                            accentColor = NeonPurpleBright,
                            onClick = { onOpenSensitivityCalc?.invoke() }
                        )
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("overlay_aim_trainer_button"),
                            icon = Icons.Default.GpsFixed,
                            label = if (isAimTrainerQueued) "Queued" else "Aim Trainer",
                            accentColor = if (isAimTrainerQueued) NeonOrange else NeonRed,
                            onClick = { onRequestAimTrainer?.invoke() }
                        )
                    }

                    if (isAimTrainerQueued || !aimTrainerStatusHint.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonOrange.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = aimTrainerStatusHint ?: "Aim Trainer opens when you leave the game.",
                                    color = NeonOrange,
                                    fontSize = 9.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAimTrainerQueued && onCancelAimTrainerQueue != null) {
                                    Text(
                                        text = "Cancel",
                                        color = accentColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onCancelAimTrainerQueue() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LandscapeRightWing(
    context: Context,
    accentColor: Color,
    overlayAlpha: Float,
    formattedTime: String,
    effectiveTimerRunning: Boolean,
    pingMs: Int,
    jitterMs: Int,
    batteryLevel: Int,
    isCharging: Boolean,
    showAppearanceMenu: Boolean,
    effectiveAccent: String,
    entitlement: EntitlementState,
    proThemeHint: String?,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onRefreshPing: (() -> Unit)?,
    onToggleAppearanceMenu: () -> Unit,
    onSelectAccent: (String) -> Unit,
    onChangeAlpha: (Float) -> Unit,
    onStopService: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val wingShape = rememberChamferedWingShape(WingSide.RIGHT)
    Box(
        modifier = modifier
            .clip(wingShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        DeepNavyBg.copy(alpha = (overlayAlpha - 0.04f).coerceAtLeast(0.80f)),
                        DarkBackground.copy(alpha = overlayAlpha.coerceAtLeast(0.86f))
                    )
                )
            )
            .tacticalWingFrame(WingSide.RIGHT, accentColor)
            .testTag("overlay_right_wing")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 14.dp)
                .testTag("overlay_right_wing_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (effectiveTimerRunning) NeonGreen else TextMuted)
                        )
                        Column {
                            Text(text = "SESSION", color = TextMuted, fontSize = 8.sp, letterSpacing = 0.6.sp)
                            Text(text = formattedTime, style = TelemetryMonoStyle.copy(fontSize = 14.sp), color = TextPrimary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleCompact,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("overlay_compact_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Compact HUD",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("overlay_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize Sidebar",
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = DarkSurface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onRefreshPing?.invoke() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (pingMs < 0) "N/A" else "${pingMs}ms",
                                style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                color = if (jitterMs > 25) NeonOrange else NeonPurpleBright
                            )
                            Text(
                                text = if (jitterMs > 0) "±${jitterMs}ms" else "Ping",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$batteryLevel%",
                                style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                color = if (batteryLevel < 20) NeonOrange else NeonGreen
                            )
                            Text(
                                text = if (isCharging) "CHG" else "BATT",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onToggleTimer, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (effectiveTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Pause or Resume Timer",
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(onClick = onResetTimer, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Timer",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            icon = Icons.Default.VolumeUp,
                            label = "Vol +",
                            accentColor = accentColor,
                            onClick = { adjustMusicVolume(context, AudioManager.ADJUST_RAISE) }
                        )
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            icon = Icons.Default.VolumeDown,
                            label = "Vol -",
                            accentColor = accentColor,
                            onClick = { adjustMusicVolume(context, AudioManager.ADJUST_LOWER) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            icon = Icons.Default.Brightness6,
                            label = "Display",
                            accentColor = accentColor,
                            onClick = { openDisplayBrightnessSettings(context) }
                        )
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            icon = Icons.Default.DoNotDisturb,
                            label = "DND",
                            accentColor = accentColor,
                            onClick = { openDndSettings(context) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuickToolButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            icon = Icons.Default.Palette,
                            label = "Style",
                            accentColor = accentColor,
                            onClick = onToggleAppearanceMenu
                        )
                        if (onStopService != null) {
                            QuickToolButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("overlay_stop_service_button"),
                                icon = Icons.Default.PowerSettingsNew,
                                label = "Stop",
                                accentColor = NeonRed,
                                onClick = onStopService
                            )
                        }
                    }
                }
            }

            if (showAppearanceMenu) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkSurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf("Cyan", "Purple", "Green", "OLED").forEach { accent ->
                                    val isProTheme = accent != "Cyan"
                                    val isUnlocked = !isProTheme || entitlement.allowOverlayTheming
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onSelectAccent(accent) },
                                        color = if (effectiveAccent == accent) accentColor.copy(alpha = 0.22f) else DarkSurfaceVariant,
                                        border = BorderStroke(1.dp, if (effectiveAccent == accent) accentColor else DarkSurfaceBorder)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (!isUnlocked) "${accent.take(3)}★" else accent.take(4),
                                                color = if (effectiveAccent == accent) accentColor else TextSecondary,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (!proThemeHint.isNullOrBlank()) {
                                Text(text = proThemeHint, color = NeonPurpleBright, fontSize = 8.5.sp)
                            }
                            Text(
                                text = "Opacity ${(overlayAlpha * 100).toInt()}%",
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                            Slider(
                                value = overlayAlpha,
                                onValueChange = onChangeAlpha,
                                valueRange = 0.55f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun adjustMusicVolume(context: Context, direction: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}

internal fun openDndSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}

internal fun openDisplayBrightnessSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}
