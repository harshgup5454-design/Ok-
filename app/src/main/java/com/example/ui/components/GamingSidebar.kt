package com.example.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.DeviceStats
import com.example.data.NetworkStats
import com.example.data.OverlayConfig

@Composable
fun GamingSidebarContainer(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    deviceStats: DeviceStats,
    networkStats: NetworkStats,
    overlayConfig: OverlayConfig = OverlayConfig(),
    autopilotStatusLabel: String = "Manual Control",
    targetGameTitle: String = "",
    activeProfileLabel: String = "Balanced",
    protectionAlertHeadline: String? = null,
    onSelectProfile: ((String) -> Unit)? = null,
    onRefreshPing: (() -> Unit)? = null,
    onOpenNetworkTools: (() -> Unit)? = null,
    onOpenSensitivityCalc: (() -> Unit)? = null,
    onRequestAimTrainer: (() -> Unit)? = null,
    onPersistAppearance: ((accent: String, opacity: Float, compact: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isOpen) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isOpen && change.position.x > size.width - 80f && dragAmount < -20f) {
                            onOpen()
                        } else if (isOpen && dragAmount > 30f) {
                            onClose()
                        }
                    }
                )
            }
    ) {
        content()

        GamingSidebarContent(
            isOpen = isOpen,
            onClose = onClose,
            onOpen = onOpen,
            memoryUsage = deviceStats.memoryUsagePercent,
            availMemory = deviceStats.availableMemoryGb,
            batteryTemp = deviceStats.batteryTemperatureC,
            batteryLevel = deviceStats.batteryLevelPercent,
            thermalStatus = deviceStats.powerManagerThermalStatus,
            thermalHeadroom = deviceStats.thermalHeadroom,
            pingMs = networkStats.pingMs,
            jitterMs = networkStats.jitterMs,
            refreshRateHz = deviceStats.displayRefreshRate,
            isCharging = deviceStats.isCharging,
            triggerPosition = overlayConfig.triggerPosition,
            triggerWidthDp = overlayConfig.triggerWidthDp,
            initialCompactMode = overlayConfig.defaultCompactMode,
            initialAccentTheme = overlayConfig.accentTheme,
            initialPanelOpacity = overlayConfig.panelOpacity,
            autopilotStatusLabel = autopilotStatusLabel,
            targetGameTitle = targetGameTitle,
            activeProfileLabel = activeProfileLabel,
            protectionAlertHeadline = protectionAlertHeadline,
            onSelectProfile = onSelectProfile,
            onRefreshPing = onRefreshPing,
            onOpenNetworkTools = onOpenNetworkTools,
            onOpenSensitivityCalc = onOpenSensitivityCalc,
            onRequestAimTrainer = onRequestAimTrainer,
            onPersistAppearance = onPersistAppearance,
            isSystemOverlay = false,
            modifier = Modifier.matchParentSize()
        )
    }
}
