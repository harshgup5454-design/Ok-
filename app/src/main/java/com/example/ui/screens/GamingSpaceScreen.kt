package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.*
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun GamingSpaceScreen(
    profiles: List<GameProfile>,
    deviceStats: DeviceStats,
    isBenchmarking: Boolean,
    benchmarkSeconds: Int,
    benchmarkSessions: List<BenchmarkSessionReport>,
    installedApps: List<InstalledAppItem>,
    isLoadingApps: Boolean,
    isPremium: Boolean = false,
    isAutopilotActive: Boolean = false,
    thermalSeverity: ProtectionSeverity = ProtectionSeverity.SAFE,
    adManager: AdManager = GoogleAdMobManager,
    onRefreshApps: () -> Unit,
    onAddGameToVault: (String, String) -> Unit,
    onRemoveGameFromVault: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onUpdateFullProfile: (GameProfile) -> Unit,
    onRestorePreviousProfile: (String) -> Unit = {},
    onDeleteProfilePermanently: (String) -> Unit = {},
    onLaunchGame: (GameProfile, Boolean) -> Boolean,
    onStopSession: () -> Unit,
    onOpenSidebarPreview: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = My Vault, 1 = Tuning Guides, 2 = Sessions
    var vaultSearchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var tuningProfile by remember { mutableStateOf<GameProfile?>(null) }
    var autoTrackOnLaunch by remember { mutableStateOf(true) }
    var notInstalledMessage by remember { mutableStateOf<String?>(null) }

    val vaultGames = remember(profiles) { profiles.filter { it.isInLibrary } }
    val filteredVaultGames = remember(vaultGames, vaultSearchQuery, favoritesOnly) {
        vaultGames.filter { game ->
            val matchesQuery = vaultSearchQuery.isBlank() ||
                game.name.contains(vaultSearchQuery, ignoreCase = true) ||
                game.packageName.contains(vaultSearchQuery, ignoreCase = true)
            val matchesFav = !favoritesOnly || game.isFavorite
            matchesQuery && matchesFav
        }
    }

    val adState by adManager.adState.collectAsState()
    val spaceVisibility = AdVisibilityContext(
        location = AdScreenLocation.GAME_SPACE,
        isGamingSessionActive = isBenchmarking || isAutopilotActive,
        isAutopilotSessionActive = isAutopilotActive,
        isBenchmarking = isBenchmarking,
        isPermissionOrLoadingActive = isLoadingApps,
        thermalSeverity = thermalSeverity
    )
    val shouldShowSpaceBanner = !isPremium && remember(adState, spaceVisibility) {
        adManager.canDisplayAds(spaceVisibility)
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_space_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (shouldShowSpaceBanner) {
                item {
                    AdMobAdaptiveBannerSlot(
                        visibilityContext = spaceVisibility,
                        adManager = adManager,
                        testTagName = "game_space_ad_banner_slot"
                    )
                }
            }

            // Header
            item {
                GameBoostScreenHeader(
                    title = "GAMING SPACE",
                    subtitle = "Game Vault, Profiles & Telemetry Launch Hub",
                    icon = Icons.Default.SportsEsports,
                    iconTint = NeonCyan,
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenSidebarPreview)
                                .testTag("game_space_sidebar_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuOpen,
                                    contentDescription = "Sidebar Preview",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Real-Time Hardware Status Hero Strip
            item {
                GameBoostHeroCard(
                    accentStart = GamingBlue,
                    accentEnd = NeonCyan,
                    contentPadding = PaddingValues(14.dp)
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
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Real-Time Hardware Status",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusPillBadge(
                            text = "${deviceStats.displayRefreshRate.toInt()} Hz Display",
                            color = NeonGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HardwareChip(
                            modifier = Modifier.weight(1f),
                            label = "Free RAM",
                            value = "${deviceStats.availableMemoryGb} GB",
                            sub = "${deviceStats.memoryUsagePercent}% used",
                            tint = if (deviceStats.memoryUsagePercent > 85) NeonOrange else NeonCyan
                        )
                        val hasTemp = deviceStats.batteryTemperatureC > 0f
                        HardwareChip(
                            modifier = Modifier.weight(1f),
                            label = "Battery Temp",
                            value = if (hasTemp) "${deviceStats.batteryTemperatureC}°C" else "N/A",
                            sub = if (deviceStats.isCharging) "Charging" else "On Battery",
                            tint = if (!hasTemp) TextSecondary else if (deviceStats.batteryTemperatureC >= 39.5f) NeonRed else NeonGreen
                        )
                        HardwareChip(
                            modifier = Modifier.weight(1f),
                            label = "OS Thermal",
                            value = deviceStats.powerManagerThermalStatus.take(8),
                            sub = if (deviceStats.powerManagerThermalStatusCode >= 2) "Throttling" else "Normal",
                            tint = if (deviceStats.powerManagerThermalStatusCode >= 2) NeonRed else NeonGreen
                        )
                    }

                    if (isBenchmarking) {
                        HorizontalDivider(color = DarkSurfaceBorder)
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
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(NeonRed)
                                )
                                Text(
                                    text = "Session Active • ${
                                        String.format(
                                            Locale.US,
                                            "%02d:%02d",
                                            benchmarkSeconds / 60,
                                            benchmarkSeconds % 60
                                        )
                                    }",
                                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                    color = NeonRed
                                )
                            }
                            Button(
                                onClick = onStopSession,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.18f)),
                                border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text(
                                    "Stop & Save",
                                    color = NeonRed,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Segmented Tabs
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpaceTabPill(
                            label = "My Vault (${vaultGames.size})",
                            selected = selectedTab == 0,
                            accent = NeonCyan,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        SpaceTabPill(
                            label = "Guides",
                            selected = selectedTab == 1,
                            accent = NeonGreen,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(0.85f)
                        )
                        SpaceTabPill(
                            label = "Sessions (${benchmarkSessions.size})",
                            selected = selectedTab == 2,
                            accent = NeonPurple,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(text = "Installed Game Vault", accentColor = NeonCyan)
                            Button(
                                onClick = {
                                    onRefreshApps()
                                    showAddGameDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("game_space_add_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Game", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Search & Favorites Filter (when vault has games)
                    if (vaultGames.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = vaultSearchQuery,
                                    onValueChange = { vaultSearchQuery = it },
                                    placeholder = {
                                        Text("Search vault games or packages...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (vaultSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { vaultSearchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = DarkSurfaceBorder,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("game_space_search_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !favoritesOnly,
                                        onClick = { favoritesOnly = false },
                                        label = { Text("All (${vaultGames.size})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.16f),
                                            selectedLabelColor = NeonCyan
                                        )
                                    )
                                    FilterChip(
                                        selected = favoritesOnly,
                                        onClick = { favoritesOnly = true },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = if (favoritesOnly) NeonRed else TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = { Text("Favorites (${vaultGames.count { it.isFavorite }})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonRed.copy(alpha = 0.16f),
                                            selectedLabelColor = NeonRed
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Auto-track telemetry card
                    item {
                        GameBoostSurfaceCard(
                            containerColor = DarkSurface,
                            borderColor = DarkSurfaceBorder,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Auto-track session telemetry on launch",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Logs battery temp, OS thermal status, and RAM usage while playing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = autoTrackOnLaunch,
                                    onCheckedChange = { autoTrackOnLaunch = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = NeonCyan,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    if (vaultGames.isEmpty()) {
                        item {
                            GameBoostEmptyState(
                                icon = Icons.Default.SportsEsports,
                                title = "No games in your vault yet",
                                description = "Add games installed on your device to launch them with a dedicated profile, FPS lock target, and GAMEBOOST overlay HUD.",
                                accentColor = NeonCyan,
                                actionLabel = "Browse Installed Apps",
                                onActionClick = {
                                    onRefreshApps()
                                    showAddGameDialog = true
                                }
                            )
                        }
                    } else if (filteredVaultGames.isEmpty()) {
                        item {
                            GameBoostEmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "No matching games found",
                                description = "No games in your vault match \"$vaultSearchQuery\" with the current filter.",
                                accentColor = NeonPurple,
                                actionLabel = "Reset Filters",
                                onActionClick = {
                                    vaultSearchQuery = ""
                                    favoritesOnly = false
                                }
                            )
                        }
                    } else {
                        items(filteredVaultGames, key = { it.id }) { game ->
                            GameVaultCard(
                                game = game,
                                context = context,
                                onLaunch = {
                                    val launched = onLaunchGame(game, autoTrackOnLaunch)
                                    if (!launched) {
                                        notInstalledMessage = "${game.name} is not installed or has no launchable activity on this device."
                                    }
                                },
                                onTune = { tuningProfile = game },
                                onRestorePrevious = {
                                    onRestorePreviousProfile(game.id)
                                    Toast.makeText(context, "Restored previous profile for ${game.name}", Toast.LENGTH_SHORT).show()
                                },
                                onToggleFavorite = { onToggleFavorite(game.id) },
                                onRemove = { onRemoveGameFromVault(game.id) }
                            )
                        }
                    }
                }

                1 -> {
                    item {
                        SectionLabel(text = "Verified In-Game Configuration Guides", accentColor = NeonGreen)
                    }

                    items(profiles.take(5)) { profile ->
                        GameTuningGuideCard(profile = profile)
                    }

                    item {
                        GameBoostSurfaceCard(
                            containerColor = DarkSurfaceElevated,
                            borderColor = NeonGreen.copy(alpha = 0.35f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Engineering Transparency & Anti-Cheat",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "GAMEBOOST does not inject into game memory, modify APK/OBB assets, or claim impossible GPU boosting. These settings represent realistic graphics configurations to apply inside game menus to sustain target frame rates without thermal throttling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(text = "Recorded Gaming Sessions", accentColor = NeonPurple)
                            if (isBenchmarking) {
                                StatusPillBadge(text = "RECORDING NOW", color = NeonRed)
                            }
                        }
                    }

                    if (benchmarkSessions.isEmpty()) {
                        item {
                            GameBoostEmptyState(
                                icon = Icons.Default.Assessment,
                                title = "No recorded sessions yet",
                                description = "Launch any game from your vault with auto-tracking enabled to log battery drain, temperature delta, and thermal throttling.",
                                accentColor = NeonPurple
                            )
                        }
                    } else {
                        items(benchmarkSessions) { session ->
                            SessionSummaryCard(session = session)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showAddGameDialog) {
        AddGameDialog(
            installedApps = installedApps,
            isLoading = isLoadingApps,
            onDismiss = { showAddGameDialog = false },
            onSelectApp = { app ->
                onAddGameToVault(app.appName, app.packageName)
                showAddGameDialog = false
                Toast.makeText(context, "Added ${app.appName} to Gaming Space", Toast.LENGTH_SHORT).show()
            },
            onManualAdd = { customName, customPkg ->
                onAddGameToVault(customName, customPkg)
                showAddGameDialog = false
                Toast.makeText(context, "Added $customName ($customPkg)", Toast.LENGTH_SHORT).show()
            }
        )
    }

    tuningProfile?.let { profile ->
        FullGameProfileDialog(
            profile = profile,
            onDismiss = { tuningProfile = null },
            onDelete = {
                onDeleteProfilePermanently(profile.id)
                tuningProfile = null
                Toast.makeText(context, "Deleted profile ${profile.name}", Toast.LENGTH_SHORT).show()
            },
            onSave = { updated ->
                onUpdateFullProfile(updated)
                tuningProfile = null
                Toast.makeText(context, "Profile updated for ${updated.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    notInstalledMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { notInstalledMessage = null },
            containerColor = DarkSurfaceElevated,
            title = {
                Text(
                    "App Not Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { notInstalledMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SpaceTabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.45f)) else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HardwareChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
            Text(text = value, style = TelemetryMonoStyle.copy(fontSize = 13.sp), color = tint, maxLines = 1)
            Text(text = sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
fun GameVaultCard(
    game: GameProfile,
    context: Context,
    onLaunch: () -> Unit,
    onTune: () -> Unit,
    onRestorePrevious: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    val appIcon: ImageBitmap? = remember(game.packageName) {
        GameSpaceManager.getAppIcon(context, game.packageName)
    }

    GameBoostSurfaceCard(
        containerColor = DarkSurface,
        borderColor = if (game.isFavorite) NeonCyan.copy(alpha = 0.4f) else DarkSurfaceBorder,
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
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkSurfaceBorderBright)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = game.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = game.performanceMode.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GamingBlue.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, GamingBlue.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "${game.fpsLock} FPS",
                                style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                                color = NeonCyanVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (game.previousMode != null) {
                        Text(
                            text = "Previous: ${game.previousMode.displayName} (${game.previousFpsLock} FPS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    if (game.customNotes.isNotBlank()) {
                        Text(
                            text = game.customNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (game.isFavorite) NeonRed else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove from Vault",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = DarkSurfaceBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTune,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkSurfaceBorderBright),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Tune", style = MaterialTheme.typography.labelMedium)
                }
                if (game.previousMode != null) {
                    OutlinedButton(
                        onClick = onRestorePrevious,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Restore previous profile", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Button(
                onClick = onLaunch,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(NeonGreen, SuccessGreenBright)))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                        Text("PLAY", color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GameTuningGuideCard(profile: GameProfile) {
    var isExpanded by remember { mutableStateOf(false) }

    GameBoostSurfaceCard(
        containerColor = DarkSurface,
        borderColor = DarkSurfaceBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Target Goal: ${profile.performanceMode.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
            }

            StatusPillBadge(text = "VERIFIED GUIDE", color = NeonGreen, showDot = false)
        }

        HorizontalDivider(color = DarkSurfaceBorder)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Recommended Graphics", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = profile.recommendedGraphics, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text = "Target Frame Rate", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = profile.recommendedFpsCap, style = TelemetryMonoStyle.copy(fontSize = 12.5.sp), color = NeonCyan)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Shadows", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = profile.shadowSetting, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text = "Anti-Aliasing", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(text = profile.antiAliasing, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = DarkSurfaceBorder)

                Text(text = "Thermal Advice", style = MaterialTheme.typography.labelMedium, color = NeonOrange)
                Text(text = profile.thermalAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Text(text = "Battery Management", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                Text(text = profile.batteryAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Text(text = "Network Stability", style = MaterialTheme.typography.labelMedium, color = NeonPurpleBright)
                Text(text = profile.networkAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Text(text = "Realistic Device Limitations", style = MaterialTheme.typography.labelMedium, color = NeonRed)
                Text(text = profile.deviceLimitations, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        TextButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 44.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isExpanded) "Show Less" else "View Detailed Thermal & Device Advice",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan
            )
        }
    }
}

@Composable
fun SessionSummaryCard(session: BenchmarkSessionReport) {
    GameBoostSurfaceCard(
        containerColor = DarkSurface,
        borderColor = if (session.throttlingObserved) NeonRed.copy(alpha = 0.4f) else DarkSurfaceBorder,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.gameTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusPillBadge(
                text = "${session.durationSeconds / 60}m ${session.durationSeconds % 60}s",
                color = NeonCyan,
                showDot = false
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Battery Temp", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                val tempText = if (session.startTempC > 0f && session.peakTempC > 0f) {
                    "${session.startTempC}°C → ${session.peakTempC}°C"
                } else {
                    "N/A"
                }
                Text(text = tempText, style = TelemetryMonoStyle.copy(fontSize = 12.sp), color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Battery Drain", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = "${session.startBatteryLevel}% → ${session.endBatteryLevel}%",
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Throttling", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = if (session.throttlingObserved) "Observed" else "None",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (session.throttlingObserved) NeonRed else NeonGreen
                )
            }
        }

        Text(
            text = session.conclusion,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun AddGameDialog(
    installedApps: List<InstalledAppItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectApp: (InstalledAppItem) -> Unit,
    onManualAdd: (String, String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var isManualEntry by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualPackage by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NeonCyan)
                Text("Add Game to Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(410.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isManualEntry,
                        onClick = { isManualEntry = false },
                        label = { Text("Installed (${installedApps.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isManualEntry,
                        onClick = { isManualEntry = true },
                        label = { Text("Manual Package", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isManualEntry) {
                    Text(
                        text = "Enter any game title and Android package name (e.g. com.dts.freefiremax).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Game Title", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Free Fire MAX", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualPackage,
                        onValueChange = { manualPackage = it },
                        label = { Text("Package Name", fontSize = 11.sp) },
                        placeholder = { Text("e.g. com.dts.freefiremax", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    PrimaryGradientButton(
                        text = "Add Package to Vault",
                        enabled = manualPackage.contains("."),
                        onClick = {
                            if (manualPackage.isNotBlank()) {
                                onManualAdd(manualName.ifBlank { manualPackage.substringAfterLast('.') }, manualPackage)
                            }
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed games...", color = TextMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        ),
                        singleLine = true
                    )

                    if (isLoading) {
                        GameBoostLoadingSkeleton(itemCount = 4)
                    } else if (filteredApps.isEmpty()) {
                        GameBoostEmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No installed apps matched",
                            description = "Try a different keyword or use the Manual Package tab.",
                            accentColor = NeonCyan
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps) { app ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectApp(app) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (app.iconBitmap != null) {
                                            Image(
                                                bitmap = app.iconBitmap,
                                                contentDescription = app.appName,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.SportsEsports,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = app.appName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (app.isGameCategory) {
                                                    StatusPillBadge(text = "GAME", color = NeonGreen, showDot = false)
                                                }
                                            }
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add", tint = NeonCyan, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun FullGameProfileDialog(
    profile: GameProfile,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (GameProfile) -> Unit
) {
    val entitlement by MembershipManager.entitlement.collectAsState()
    var selectedMode by remember { mutableStateOf(profile.performanceMode) }
    var selectedFps by remember { mutableStateOf(profile.fpsLock) }
    var notes by remember { mutableStateOf(profile.customNotes) }
    var proPresetHint by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = {
            Text(
                "Tune ${profile.name}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (entitlement.allowProProfileCustomization) {
                                "Pro Quick Profile Presets (Unlocked)"
                            } else {
                                "Pro Quick Profile Presets (Optional Pro)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonPurpleBright
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("Tournament", PerformanceMode.PERFORMANCE_ASSISTANT, 90),
                                Triple("Cool Long Run", PerformanceMode.THERMAL_PROTECTION, 60),
                                Triple("Battery Sync", PerformanceMode.BATTERY_SAVER, 45)
                            ).forEach { (label, presetMode, presetFps) ->
                                FilterChip(
                                    selected = selectedMode == presetMode && selectedFps == presetFps && notes.contains(label),
                                    onClick = {
                                        if (entitlement.allowProProfileCustomization) {
                                            proPresetHint = null
                                            selectedMode = presetMode
                                            selectedFps = presetFps
                                            notes = "[Pro Preset: $label] Target ${presetFps}fps, ${presetMode.displayName}"
                                        } else {
                                            proPresetHint = "Pro presets unlock with GAMEBOOST Pro. Manual tuning below stays free."
                                        }
                                    },
                                    label = { Text(if (entitlement.allowProProfileCustomization) label else "$label ★", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonPurpleBright
                                    )
                                )
                            }
                        }
                        if (!proPresetHint.isNullOrBlank()) {
                            Text(text = proPresetHint.orEmpty(), style = MaterialTheme.typography.labelSmall, color = NeonPurpleBright)
                        }
                    }
                    HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(top = 6.dp))
                }

                item {
                    Text("Performance Assistant Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }

                items(PerformanceMode.values()) { mode ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedMode = mode },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedMode == mode) NeonCyan.copy(alpha = 0.1f) else DarkSurface,
                        border = BorderStroke(1.dp, if (selectedMode == mode) NeonCyan else DarkSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                            )
                            Column {
                                Text(text = mode.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(text = mode.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(color = DarkSurfaceBorder)
                    Text("Target FPS Lock", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 45, 60, 90).forEach { fps ->
                            FilterChip(
                                selected = selectedFps == fps,
                                onClick = { selectedFps = fps },
                                label = { Text("$fps FPS", style = TelemetryMonoStyle.copy(fontSize = 11.sp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(color = DarkSurfaceBorder)
                    Text("Tactical Notes (Crosshair / Gyro / Recoil)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("e.g. Sensitivity 120%, gyro always on...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkSurfaceBorder
                        )
                    )
                }

                if (onDelete != null) {
                    item {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            border = BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = NeonRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Saved Profile", color = NeonRed, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(profile.copy(performanceMode = selectedMode, fpsLock = selectedFps, customNotes = notes))
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
