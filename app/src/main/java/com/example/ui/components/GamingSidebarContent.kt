package com.example.ui.components

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MembershipManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GamingSidebarContent(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    memoryUsage: Int,
    availMemory: Float,
    batteryTemp: Float,
    batteryLevel: Int,
    thermalStatus: String,
    thermalHeadroom: Float = -1f,
    pingMs: Int,
    jitterMs: Int = 0,
    refreshRateHz: Float = 60f,
    isCharging: Boolean = false,
    triggerPosition: String = "Center",
    triggerWidthDp: Int = 24,
    initialCompactMode: Boolean = false,
    initialAccentTheme: String = "Cyan",
    initialPanelOpacity: Float = 0.94f,
    autopilotStatusLabel: String = "Manual Control",
    targetGameTitle: String = "",
    activeProfileLabel: String = "Balanced",
    protectionAlertHeadline: String? = null,
    sessionSeconds: Long? = null,
    isTimerRunning: Boolean = true,
    onToggleTimer: (() -> Unit)? = null,
    onResetTimer: (() -> Unit)? = null,
    onSelectProfile: ((String) -> Unit)? = null,
    onRefreshPing: (() -> Unit)? = null,
    onOpenNetworkTools: (() -> Unit)? = null,
    onOpenSensitivityCalc: (() -> Unit)? = null,
    onRequestAimTrainer: (() -> Unit)? = null,
    isAimTrainerQueued: Boolean = false,
    aimTrainerStatusHint: String? = null,
    onCancelAimTrainerQueue: (() -> Unit)? = null,
    onPersistAppearance: ((accent: String, opacity: Float, compact: Boolean) -> Unit)? = null,
    onModeChange: ((isCompact: Boolean) -> Unit)? = null,
    onStopService: (() -> Unit)? = null,
    onDragHandleY: ((Float) -> Unit)? = null,
    isSystemOverlay: Boolean = onDragHandleY != null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var localSeconds by remember { mutableStateOf(0L) }
    var localTimerRunning by remember { mutableStateOf(true) }
    val effectiveSeconds = sessionSeconds ?: localSeconds
    val effectiveTimerRunning = if (sessionSeconds != null) isTimerRunning else localTimerRunning

    var isCompactMode by remember(initialCompactMode) { mutableStateOf(initialCompactMode) }
    var handleOffsetY by remember { mutableFloatStateOf(0f) }
    var selectedProfileMode by remember(activeProfileLabel) { mutableStateOf(activeProfileLabel) }
    var showAppearanceMenu by remember { mutableStateOf(false) }

    val entitlement by MembershipManager.entitlement.collectAsState()
    var selectedAccent by remember(initialAccentTheme) { mutableStateOf(initialAccentTheme) }
    var overlayAlpha by remember(initialPanelOpacity) { mutableFloatStateOf(initialPanelOpacity) }
    var proThemeHint by remember { mutableStateOf<String?>(null) }

    val effectiveAccent = if (selectedAccent != "Cyan" && !entitlement.allowOverlayTheming) {
        "Cyan"
    } else {
        selectedAccent
    }

    val accentColor = when (effectiveAccent) {
        "Purple" -> NeonPurple
        "Green" -> NeonGreen
        "OLED" -> Color.White
        else -> NeonCyan
    }

    LaunchedEffect(sessionSeconds == null, localTimerRunning) {
        if (sessionSeconds == null && localTimerRunning) {
            while (true) {
                delay(1000L)
                localSeconds++
            }
        }
    }

    val formattedTime = remember(effectiveSeconds) {
        val mins = effectiveSeconds / 60
        val secs = effectiveSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val triggerAlignment = when (triggerPosition) {
        "Top" -> Alignment.TopEnd
        "Bottom" -> Alignment.BottomEnd
        else -> Alignment.CenterEnd
    }

    BoxWithConstraints(
        modifier = if (isSystemOverlay && (!isOpen || isCompactMode)) {
            modifier.wrapContentSize()
        } else {
            modifier.fillMaxSize()
        }
    ) {
        val isDualWingLandscape = maxWidth >= 520.dp && maxWidth > maxHeight

        // Collapsed Floating Edge Handle
        if (!isOpen) {
            val handleWidth = triggerWidthDp.coerceAtLeast(24).dp
            val touchModifier = if (isSystemOverlay) {
                Modifier
            } else {
                Modifier
                    .align(triggerAlignment)
                    .offset(y = handleOffsetY.dp)
                    .padding(vertical = 36.dp)
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        val swipeOpenPx = with(density) { 24.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDx = 0f
                            var totalDy = 0f
                            var isDragging = false
                            var openedBySwipe = false
                            var cancelled = false
                            var lastPos = down.position

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed && !isDragging) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) break
                                val dx = change.position.x - lastPos.x
                                val dy = change.position.y - lastPos.y
                                lastPos = change.position
                                totalDx += dx
                                totalDy += dy

                                if (!isDragging && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                    if (abs(totalDx) > swipeOpenPx && abs(totalDx) > abs(totalDy) * 1.15f) {
                                        openedBySwipe = true
                                        change.consume()
                                        onOpen()
                                        break
                                    } else {
                                        isDragging = true
                                    }
                                }
                                if (isDragging) {
                                    change.consume()
                                    handleOffsetY = (handleOffsetY + (dy / density.density)).coerceIn(-260f, 260f)
                                }
                            }
                            if (!cancelled && !isDragging && !openedBySwipe) {
                                onOpen()
                            }
                        }
                    }
            }

            val edgeShape = remember {
                GenericShape { size, _ ->
                    val w = size.width
                    val h = size.height
                    moveTo(w, 0f)
                    lineTo(w * 0.35f, h * 0.14f)
                    lineTo(0f, h * 0.32f)
                    lineTo(0f, h * 0.68f)
                    lineTo(w * 0.35f, h * 0.86f)
                    lineTo(w, h)
                    close()
                }
            }

            Box(
                modifier = touchModifier
                    .widthIn(min = 44.dp)
                    .heightIn(min = 84.dp)
                    .padding(start = 12.dp)
                    .testTag("overlay_edge_handle"),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .width(handleWidth.coerceIn(18.dp, 30.dp))
                        .height(80.dp),
                    shape = edgeShape,
                    color = DarkBackground.copy(alpha = 0.92f),
                    border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.85f)),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (effectiveTimerRunning) NeonGreen else accentColor)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.verticalGradient(listOf(accentColor, GamingBlue)))
                        )
                    }
                }
            }
        }

        // Expanded View: Compact Pill OR Landscape Dual-Wing HUD OR Portrait Single-Wing HUD
        if (isCompactMode) {
            AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                CompactHudPill(
                    formattedTime = formattedTime,
                    batteryTemp = batteryTemp,
                    thermalStatus = thermalStatus,
                    pingMs = pingMs,
                    accentColor = accentColor,
                    onExpand = {
                        isCompactMode = false
                        onModeChange?.invoke(false)
                    },
                    onClose = onClose
                )
            }
        } else if (isDualWingLandscape) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("overlay_sidebar_panel"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LandscapeLeftWing(
                        accentColor = accentColor,
                        overlayAlpha = overlayAlpha,
                        targetGameTitle = targetGameTitle,
                        refreshRateHz = refreshRateHz,
                        memoryUsage = memoryUsage,
                        availMemory = availMemory,
                        batteryTemp = batteryTemp,
                        thermalStatus = thermalStatus,
                        protectionAlertHeadline = protectionAlertHeadline,
                        autopilotStatusLabel = autopilotStatusLabel,
                        selectedProfileMode = selectedProfileMode,
                        isAimTrainerQueued = isAimTrainerQueued,
                        aimTrainerStatusHint = aimTrainerStatusHint,
                        onSelectProfile = { mode ->
                            selectedProfileMode = mode
                            onSelectProfile?.invoke(mode)
                        },
                        onRefreshPing = onRefreshPing,
                        onOpenNetworkTools = onOpenNetworkTools,
                        onOpenSensitivityCalc = onOpenSensitivityCalc,
                        onRequestAimTrainer = onRequestAimTrainer,
                        onCancelAimTrainerQueue = onCancelAimTrainerQueue,
                        modifier = Modifier
                            .widthIn(min = 216.dp, max = 252.dp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .animateEnterExit(
                                enter = slideInHorizontally(initialOffsetX = { -it }),
                                exit = slideOutHorizontally(targetOffsetX = { -it })
                            )
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose
                            )
                            .testTag("overlay_center_passthrough")
                    )

                    LandscapeRightWing(
                        context = context,
                        accentColor = accentColor,
                        overlayAlpha = overlayAlpha,
                        formattedTime = formattedTime,
                        effectiveTimerRunning = effectiveTimerRunning,
                        pingMs = pingMs,
                        jitterMs = jitterMs,
                        batteryLevel = batteryLevel,
                        isCharging = isCharging,
                        showAppearanceMenu = showAppearanceMenu,
                        effectiveAccent = effectiveAccent,
                        entitlement = entitlement,
                        proThemeHint = proThemeHint,
                        onToggleCompact = {
                            isCompactMode = true
                            onModeChange?.invoke(true)
                        },
                        onClose = onClose,
                        onToggleTimer = {
                            if (onToggleTimer != null) onToggleTimer()
                            else localTimerRunning = !localTimerRunning
                        },
                        onResetTimer = {
                            if (onResetTimer != null) onResetTimer()
                            else localSeconds = 0L
                        },
                        onRefreshPing = onRefreshPing,
                        onToggleAppearanceMenu = { showAppearanceMenu = !showAppearanceMenu },
                        onSelectAccent = { accent ->
                            val isProTheme = accent != "Cyan"
                            if (!isProTheme || entitlement.allowOverlayTheming) {
                                proThemeHint = null
                                selectedAccent = accent
                                onPersistAppearance?.invoke(accent, overlayAlpha, isCompactMode)
                            } else {
                                proThemeHint = "$accent theme requires Pro."
                            }
                        },
                        onChangeAlpha = { alpha ->
                            overlayAlpha = alpha
                            onPersistAppearance?.invoke(selectedAccent, alpha, isCompactMode)
                        },
                        onStopService = onStopService,
                        modifier = Modifier
                            .widthIn(min = 198.dp, max = 232.dp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .animateEnterExit(
                                enter = slideInHorizontally(initialOffsetX = { it }),
                                exit = slideOutHorizontally(targetOffsetX = { it })
                            )
                    )
                }
            }
        } else {
            // Portrait / Narrow viewport: Chamfered Single-Wing Tactical Panel
            AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val wingShape = rememberChamferedWingShape(WingSide.RIGHT)
                Box(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 330.dp)
                        .fillMaxWidth()
                        .heightIn(max = 580.dp)
                        .padding(vertical = 8.dp)
                        .clip(wingShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    DeepNavyBg.copy(alpha = (overlayAlpha - 0.04f).coerceAtLeast(0.82f)),
                                    DarkBackground.copy(alpha = overlayAlpha.coerceAtLeast(0.90f))
                                )
                            )
                        )
                        .tacticalWingFrame(WingSide.RIGHT, accentColor)
                        .testTag("overlay_sidebar_panel")
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)
                            .testTag("overlay_sidebar_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        item {
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
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accentColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SportsEsports,
                                            contentDescription = "GAMEBOOST",
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "GAMEBOOST",
                                            fontFamily = SpaceGroteskFontFamily,
                                            color = accentColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.1.sp
                                        )
                                        Text(
                                            text = targetGameTitle.ifBlank { "Global Game Space" },
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            isCompactMode = true
                                            onModeChange?.invoke(true)
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("overlay_compact_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPicture,
                                            contentDescription = "Compact HUD",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onClose,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("overlay_close_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Minimize Sidebar",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Session Timer + Refresh Rate
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurface.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, DarkSurfaceBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (effectiveTimerRunning) NeonGreen else TextMuted)
                                        )
                                        Column {
                                            Text(text = "SESSION", color = TextMuted, fontSize = 9.sp, letterSpacing = 0.8.sp)
                                            Text(text = formattedTime, style = TelemetryMonoStyle.copy(fontSize = 15.sp), color = TextPrimary)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = NeonGreen.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "${refreshRateHz.toInt()} Hz",
                                                style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                                                color = NeonGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (onToggleTimer != null) onToggleTimer()
                                                else localTimerRunning = !localTimerRunning
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (effectiveTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Pause or Resume Timer",
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (onResetTimer != null) onResetTimer()
                                                else localSeconds = 0L
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = "Reset Timer",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!protectionAlertHeadline.isNullOrBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = NeonOrange.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = protectionAlertHeadline,
                                            color = NeonOrange,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Tactical Telemetry Gauges + Ping/Battery Strip
                        item {
                            val hasBatteryTemp = batteryTemp > 0f
                            val tempProgress = if (hasBatteryTemp) ((batteryTemp - 25f) / 22f).coerceIn(0.12f, 1f) else 0.05f
                            val tempTint = if (!hasBatteryTemp) TextSecondary else if (batteryTemp >= 39.5f || thermalStatus.contains("Severe") || thermalStatus.contains("Critical")) NeonRed else NeonGreen
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TacticalArcGauge(
                                        label = "RAM",
                                        valueText = "$memoryUsage%",
                                        subText = "${availMemory} GB Free",
                                        progress = memoryUsage / 100f,
                                        accentColor = if (memoryUsage > 85) NeonOrange else accentColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TacticalArcGauge(
                                        label = "TEMP",
                                        valueText = if (hasBatteryTemp) "${batteryTemp}°C" else "N/A",
                                        subText = "OS: ${thermalStatus.take(9)}",
                                        progress = tempProgress,
                                        accentColor = tempTint,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SidebarStatTile(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.Wifi,
                                        label = "Gateway Ping",
                                        value = if (pingMs < 0) "N/A" else "${pingMs}ms",
                                        sub = if (jitterMs > 0) "±${jitterMs}ms Jitter" else "Tap Net",
                                        tint = if (jitterMs > 25) NeonOrange else NeonPurpleBright
                                    )
                                    SidebarStatTile(
                                        modifier = Modifier.weight(1f),
                                        icon = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                        label = "Battery",
                                        value = "$batteryLevel%",
                                        sub = if (isCharging) "Charging" else "On Battery",
                                        tint = if (batteryLevel < 20) NeonOrange else NeonGreen
                                    )
                                }
                            }
                        }

                        // Profile Switcher
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurface.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, DarkSurfaceBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "PROFILE • ${autopilotStatusLabel.take(18)}",
                                        color = TextSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("Stability", "Balanced", "Battery").forEach { mode ->
                                            val selected = selectedProfileMode.equals(mode, ignoreCase = true)
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedProfileMode = mode
                                                        onSelectProfile?.invoke(mode)
                                                    },
                                                color = if (selected) accentColor.copy(alpha = 0.22f) else DarkSurfaceVariant,
                                                border = BorderStroke(1.dp, if (selected) accentColor else DarkSurfaceBorder),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = mode,
                                                        color = if (selected) accentColor else TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Gaming Tools + Quick Utilities (including Brightness / Display shortcut)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QuickToolButton(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 50.dp)
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
                                            .heightIn(min = 50.dp)
                                            .testTag("overlay_sensitivity_button"),
                                        icon = Icons.Default.Calculate,
                                        label = "Sensitivity",
                                        accentColor = NeonPurpleBright,
                                        onClick = { onOpenSensitivityCalc?.invoke() }
                                    )
                                    QuickToolButton(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 50.dp)
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = aimTrainerStatusHint ?: "Aim Trainer opens when you leave the game.",
                                                color = NeonOrange,
                                                fontSize = 9.5.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isAimTrainerQueued && onCancelAimTrainerQueue != null) {
                                                Text(
                                                    text = "Cancel",
                                                    color = accentColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable { onCancelAimTrainerQueue() }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    QuickToolButton(
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        icon = Icons.Default.VolumeUp,
                                        label = "Vol +",
                                        accentColor = accentColor,
                                        onClick = { adjustMusicVolume(context, AudioManager.ADJUST_RAISE) }
                                    )
                                    QuickToolButton(
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        icon = Icons.Default.VolumeDown,
                                        label = "Vol -",
                                        accentColor = accentColor,
                                        onClick = { adjustMusicVolume(context, AudioManager.ADJUST_LOWER) }
                                    )
                                    QuickToolButton(
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        icon = Icons.Default.Brightness6,
                                        label = "Bright",
                                        accentColor = accentColor,
                                        onClick = { openDisplayBrightnessSettings(context) }
                                    )
                                    QuickToolButton(
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
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
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                        icon = Icons.Default.Palette,
                                        label = "Style",
                                        accentColor = accentColor,
                                        onClick = { showAppearanceMenu = !showAppearanceMenu }
                                    )
                                    if (onStopService != null) {
                                        QuickToolButton(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                                .testTag("overlay_stop_service_button"),
                                            icon = Icons.Default.PowerSettingsNew,
                                            label = "Stop HUD",
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
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("Cyan", "Purple", "Green", "OLED").forEach { accent ->
                                                val isProTheme = accent != "Cyan"
                                                val isUnlocked = !isProTheme || entitlement.allowOverlayTheming
                                                FilterChip(
                                                    selected = effectiveAccent == accent,
                                                    onClick = {
                                                        if (isUnlocked) {
                                                            proThemeHint = null
                                                            selectedAccent = accent
                                                            onPersistAppearance?.invoke(accent, overlayAlpha, isCompactMode)
                                                        } else {
                                                            proThemeHint = "$accent theme requires Pro."
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            text = if (isProTheme && !entitlement.allowOverlayTheming) "$accent★" else accent,
                                                            fontSize = 9.sp
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        if (!proThemeHint.isNullOrBlank()) {
                                            Text(text = proThemeHint.orEmpty(), color = NeonPurpleBright, fontSize = 9.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Opacity ${(overlayAlpha * 100).toInt()}%", color = TextSecondary, fontSize = 10.sp)
                                            Slider(
                                                value = overlayAlpha,
                                                onValueChange = {
                                                    overlayAlpha = it
                                                    onPersistAppearance?.invoke(selectedAccent, it, isCompactMode)
                                                },
                                                valueRange = 0.55f..1.0f,
                                                modifier = Modifier.width(150.dp),
                                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
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
    }
}

@Composable
fun CompactHudPill(
    formattedTime: String,
    batteryTemp: Float,
    thermalStatus: String,
    pingMs: Int,
    accentColor: Color,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(172.dp)
            .padding(top = 12.dp, end = 4.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 6.dp, bottomEnd = 6.dp))
            .clickable(onClick = onExpand)
            .testTag("overlay_compact_pill"),
        color = DarkBackground.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 6.dp, bottomEnd = 6.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreen))
                    Text(text = formattedTime, style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = TextPrimary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onExpand, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.OpenInFull, contentDescription = "Expand", tint = accentColor, modifier = Modifier.size(13.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(13.dp))
                    }
                }
            }

            HorizontalDivider(color = DarkSurfaceBorder)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Batt Temp", color = TextSecondary, fontSize = 9.5.sp)
                val hasTemp = batteryTemp > 0f
                Text(
                    text = if (hasTemp) "${batteryTemp}°C" else "N/A",
                    style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                    color = if (!hasTemp) TextSecondary else if (batteryTemp >= 39.5f) NeonRed else NeonGreen
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "OS Thermal", color = TextSecondary, fontSize = 9.5.sp)
                Text(
                    text = thermalStatus.take(7),
                    style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                    color = if (thermalStatus.contains("Severe") || thermalStatus.contains("Critical")) NeonRed else NeonGreen
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "TCP Ping", color = TextSecondary, fontSize = 9.5.sp)
                Text(
                    text = if (pingMs < 0) "N/A" else "${pingMs}ms",
                    style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                    color = NeonPurpleBright
                )
            }
        }
    }
}

@Composable
private fun SidebarStatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Text(text = label, color = TextSecondary, fontSize = 9.5.sp, maxLines = 1)
            }
            Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 13.5.sp), color = TextPrimary, maxLines = 1)
            Text(text = sub, color = TextMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun QuickToolButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accentColor: Color = NeonCyan,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
