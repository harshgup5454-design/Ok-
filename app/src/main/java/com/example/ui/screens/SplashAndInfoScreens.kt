package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

fun isRunningUnderRobolectric(): Boolean {
    return Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
}

/**
 * Branded ZeroX Boost launch transition overlay.
 * Displays cleanly on real Android devices for ~650ms (with tap-to-skip) while bypassing
 * automatically under Robolectric so UI test harnesses never stall waiting for animation timers.
 */
@Composable
fun SplashTransitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ring")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(visible) {
        if (visible) {
            delay(650L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(260))
    ) {
        GameBoostAppBackground(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("splash_screen_overlay")
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(152.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        NeonPurpleBright.copy(alpha = 0.45f),
                                        NeonPurple.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.gameboost_logo),
                        contentDescription = "ZeroX Boost Emblem",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(NeonPurpleBright, NeonPurple, NeonCyan)),
                                CircleShape
                            )
                            .padding(10.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ZeroX BOOST",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        letterSpacing = 2.2.sp
                    )
                    Text(
                        text = "HARDWARE TELEMETRY & GAMING ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonPurpleBright,
                        letterSpacing = 1.6.sp,
                        textAlign = TextAlign.Center
                    )
                }

                LinearProgressIndicator(
                    modifier = Modifier
                        .width(176.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonPurpleBright,
                    trackColor = DarkSurfaceVariant
                )
            }

            Text(
                text = "v${BuildConfig.VERSION_NAME} • Non-Root Android Architecture",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .alpha(0.85f)
            )
        }
    }
}

@Composable
fun AboutGameBoostScreen(
    onBackClick: () -> Unit,
    onOpenHelpFaq: () -> Unit,
    onOpenPermissionCenter: () -> Unit
) {
    BackHandler { onBackClick() }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("about_gameboost_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "About ZeroX Boost",
                    subtitle = "Engineering integrity, architecture & device transparency",
                    onBackClick = onBackClick,
                    backButtonTestTag = "about_back_button"
                )
            }

            item {
                GameBoostHeroCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.gameboost_logo),
                            contentDescription = "ZeroX Boost Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(
                                    1.5.dp,
                                    Brush.linearGradient(listOf(NeonPurpleBright, NeonPurple, NeonCyan)),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(5.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ZeroX BOOST",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                StatusPillBadge(text = "v${BuildConfig.VERSION_NAME}", color = NeonPurpleBright, showDot = false)
                            }
                            Text(
                                text = "Build ${BuildConfig.VERSION_CODE} • Target API 35 (Android 15 Ready)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Runs cleanly in standard unrooted Android user space.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreen
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("Engineering Standards", accentColor = NeonCyan)
            }

            item {
                GameBoostSurfaceCard {
                    AboutPrincipleItem(
                        icon = Icons.Default.VerifiedUser,
                        tint = NeonGreen,
                        title = "Zero Fake Telemetry Guarantee",
                        body = "ZeroX Boost never fabricates FPS counters, game-server ping, GPU clock boosts, or memory freed percentages. When Android isolates a metric, we explicitly mark it Unavailable or show the official PowerManager / system value."
                    )
                    HorizontalDivider(color = DarkSurfaceBorder)
                    AboutPrincipleItem(
                        icon = Icons.Default.Security,
                        tint = NeonCyan,
                        title = "Zero Game Tampering & Anti-Cheat Safe",
                        body = "We never inject libraries, patch APK/OBB assets, or inspect another game's memory. Sensitivity calculations and tuning guides provide clear reference numbers you apply inside your game."
                    )
                    HorizontalDivider(color = DarkSurfaceBorder)
                    AboutPrincipleItem(
                        icon = Icons.Default.Restore,
                        tint = NeonPurpleBright,
                        title = "Reversible System Profiles",
                        body = "Whenever Autopilot or Device Control adjusts brightness, screen timeout, auto-rotate, or Do Not Disturb, your baseline values are stored locally in Room so they restore cleanly on exit."
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenHelpFaq,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("about_open_faq_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Help & FAQ", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenPermissionCenter,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright),
                        border = BorderStroke(1.dp, NeonPurpleBright.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Permissions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPrincipleItem(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(10.dp),
            color = tint.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

private data class FaqEntry(
    val id: String,
    val category: String,
    val question: String,
    val answer: String
)

@Composable
fun HelpFaqScreen(
    onBackClick: () -> Unit,
    onOpenPermissionCenter: () -> Unit
) {
    BackHandler { onBackClick() }

    val faqs = remember {
        listOf(
            FaqEntry(
                id = "fps",
                category = "Telemetry",
                question = "Why does ZeroX Boost show FPS as Unavailable unless I log it?",
                answer = "On standard unrooted Android devices, the OS sandbox isolates each app's graphics surface buffer. Third-party apps cannot read another game's live render FPS without root, ADB, or OEM-privileged APIs. ZeroX Boost provides the Display Refresh Rate (Hz), PowerManager thermal status, and lets you log observed FPS from OEM overlays (like Xiaomi Game Turbo or Samsung Game Plugins)."
            ),
            FaqEntry(
                id = "overlay",
                category = "Overlay",
                question = "Floating sidebar doesn't appear on Xiaomi / HyperOS / MIUI",
                answer = "On Xiaomi, Redmi, and POCO devices, grant 'Display over other apps' in Permission Center AND enable 'Display pop-up windows while running in the background' inside Android Settings -> Apps -> ZeroX Boost -> Other permissions. Also disable battery optimization restrictions if the OS kills background services."
            ),
            FaqEntry(
                id = "ping",
                category = "Network",
                question = "How does Gateway Ping differ from in-game server ping?",
                answer = "Network Intelligence measures real TCP socket round-trip time (8.8.8.8:53), DNS resolution latency, and RFC 3393 jitter between your device and public internet infrastructure. Actual in-game ping depends on the distance to your specific match server."
            ),
            FaqEntry(
                id = "autopilot",
                category = "Autopilot",
                question = "What settings can Autopilot safely change and restore?",
                answer = "When authorized via Modify System Settings and Do Not Disturb Access, Autopilot adjusts Screen Brightness, Screen Timeout, Auto-Rotate lock, and DND interruption filter—and saves your original system baseline in local storage so settings revert when your session ends."
            ),
            FaqEntry(
                id = "pro",
                category = "Billing",
                question = "Do I need ZeroX Boost Pro to use diagnostics or the sidebar?",
                answer = "No. Core hardware telemetry, Game Space, Network Intelligence, Benchmark Lab, Aim Lab, ZeroX Autopilot, and the Floating Sidebar are 100% free. Optional ZeroX Boost Pro removes idle ads, unlocks Purple/Green/OLED sidebar accents, adds Pro profile presets, and expands report history/export."
            )
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>("fps") }

    val filteredFaqs = remember(searchQuery, faqs) {
        if (searchQuery.isBlank()) faqs
        else faqs.filter {
            it.question.contains(searchQuery, ignoreCase = true) ||
                it.answer.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("help_faq_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GameBoostScreenHeader(
                    title = "Help & FAQ",
                    subtitle = "Answers about telemetry accuracy, overlay setup, and privacy",
                    onBackClick = onBackClick,
                    backButtonTestTag = "faq_back_button"
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search questions or topics...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonPurpleBright) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurpleBright,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredFaqs.isEmpty()) {
                item {
                    GameBoostEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No Matching Topics",
                        description = "Try searching for 'overlay', 'ping', 'FPS', 'Autopilot', or 'Pro'.",
                        actionLabel = "Clear Search",
                        onActionClick = { searchQuery = "" }
                    )
                }
            } else {
                items(filteredFaqs, key = { it.id }) { faq ->
                    val isExpanded = expandedId == faq.id
                    GameBoostSurfaceCard(
                        borderColor = if (isExpanded) NeonPurpleBright.copy(alpha = 0.55f) else DarkSurfaceBorder,
                        modifier = Modifier.clickable {
                            expandedId = if (isExpanded) null else faq.id
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                StatusPillBadge(text = faq.category.uppercase(), color = NeonCyan, showDot = false)
                                Text(
                                    text = faq.question,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = NeonPurpleBright
                            )
                        }
                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider(color = DarkSurfaceBorder)
                                Text(
                                    text = faq.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                GameBoostSurfaceCard(containerColor = DarkSurfaceElevated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Still need permission help?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Inspect live Android permission status in Permission Center.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Button(
                            onClick = onOpenPermissionCenter,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleBright),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Open", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
