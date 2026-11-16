package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.billing.BillingConnectionState
import com.example.billing.BillingManager
import com.example.billing.BillingOffer
import com.example.billing.BillingUiState
import com.example.billing.GooglePlayBillingManager
import com.example.billing.PurchaseFlowStatus
import com.example.data.MembershipManager
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun PremiumScreen(
    billingManager: BillingManager = GooglePlayBillingManager
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findBillingActivity() }
    val entitlement by MembershipManager.entitlement.collectAsState()
    val billingState by billingManager.billingState.collectAsState()
    var manageActionError by remember { mutableStateOf<String?>(null) }

    GameBoostAppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("premium_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Header
            item {
                GameBoostHeroCard(
                    accentStart = NeonPurple,
                    accentEnd = NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NeonPurple.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = NeonPurpleBright,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "GAMEBOOST PRO",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "Google Play Billing Upgrade",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPurpleBright
                                )
                            }
                        }

                        StatusPillBadge(
                            text = when {
                                entitlement.isPremium -> "PRO ACTIVE"
                                entitlement.isPendingPurchase -> "PENDING"
                                else -> "FREE TIER"
                            },
                            color = when {
                                entitlement.isPremium -> NeonPurpleBright
                                entitlement.isPendingPurchase -> NeonOrange
                                else -> NeonCyan
                            }
                        )
                    }

                    Text(
                        text = "Optional Pro upgrade via Google Play Billing. All core diagnostics, Autopilot, and benchmark tools remain 100% Free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Verified Subscription / Entitlement Status Card
            item {
                GameBoostSurfaceCard(
                    modifier = Modifier.testTag("premium_status_card"),
                    containerColor = if (entitlement.isPremium) DarkSurfaceElevated else DarkSurface,
                    borderColor = if (entitlement.isPremium) NeonPurple.copy(alpha = 0.55f) else DarkSurfaceBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Subscription & Entitlement Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = entitlement.tier.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (entitlement.isPremium) NeonPurpleBright else NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entitlement.billingStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    if (entitlement.activeProductId != null || billingState.activeTokenFingerprint != null) {
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Plan ID: ${entitlement.activeProductId ?: "None"} • Verification: ${entitlement.verificationSource}",
                                style = TelemetryMonoStyle.copy(fontSize = 10.5.sp),
                                color = TextMuted
                            )
                            val fp = entitlement.tokenFingerprint ?: billingState.activeTokenFingerprint
                            if (!fp.isNullOrBlank()) {
                                Text(
                                    text = "Verified Token Fingerprint: $fp",
                                    style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    BillingFeedbackBanner(billingState = billingState)

                    if (BuildConfig.DEBUG) {
                        HorizontalDivider(color = DarkSurfaceBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Debug UI Preview Only (Not Play Billing)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { MembershipManager.toggleDevPreviewPremium() },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("premium_debug_preview_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (entitlement.isPremium) NeonGreen else NeonPurpleBright
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (entitlement.isPremium) "Revert Preview" else "Preview Pro UI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Google Play Subscription Plan Cards
            item {
                SectionLabel("Google Play Plans", accentColor = NeonPurpleBright)
            }

            item {
                val catalog = billingState.catalog
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanOfferCard(
                        planName = "GAMEBOOST Pro Monthly",
                        productId = catalog.monthlySubscriptionId,
                        offer = billingState.monthlyOffer,
                        subtitle = "Flexible monthly Google Play subscription • Cancel anytime",
                        accentColor = NeonCyan,
                        isCurrentlyActive = entitlement.isPremium && entitlement.activeProductId == catalog.monthlySubscriptionId,
                        isBillingReady = billingState.connectionState == BillingConnectionState.CONNECTED,
                        isPurchaseInProgress = billingState.purchaseStatus is PurchaseFlowStatus.LaunchingCheckout,
                        buttonTag = "premium_subscribe_monthly_button",
                        onSubscribeClick = {
                            activity?.let { billingManager.launchPurchase(it, catalog.monthlySubscriptionId) }
                        }
                    )

                    PlanOfferCard(
                        planName = "GAMEBOOST Pro Yearly",
                        productId = catalog.yearlySubscriptionId,
                        offer = billingState.yearlyOffer,
                        subtitle = "Annual Google Play subscription",
                        accentColor = NeonPurpleBright,
                        isCurrentlyActive = entitlement.isPremium && entitlement.activeProductId == catalog.yearlySubscriptionId,
                        isBillingReady = billingState.connectionState == BillingConnectionState.CONNECTED,
                        isPurchaseInProgress = billingState.purchaseStatus is PurchaseFlowStatus.LaunchingCheckout,
                        buttonTag = "premium_subscribe_yearly_button",
                        onSubscribeClick = {
                            activity?.let { billingManager.launchPurchase(it, catalog.yearlySubscriptionId) }
                        }
                    )

                    billingState.oneTimeOffers.forEach { oneTimeOffer ->
                        PlanOfferCard(
                            planName = oneTimeOffer.title.ifBlank { "GAMEBOOST Pro Pass" },
                            productId = oneTimeOffer.productId,
                            offer = oneTimeOffer,
                            subtitle = "One-time Google Play product • No recurring charges",
                            accentColor = NeonGreen,
                            isCurrentlyActive = entitlement.isPremium && entitlement.activeProductId == oneTimeOffer.productId,
                            isBillingReady = billingState.connectionState == BillingConnectionState.CONNECTED,
                            isPurchaseInProgress = billingState.purchaseStatus is PurchaseFlowStatus.LaunchingCheckout,
                            buttonTag = "premium_subscribe_onetime_${oneTimeOffer.productId}",
                            onSubscribeClick = {
                                activity?.let { billingManager.launchPurchase(it, oneTimeOffer.productId) }
                            }
                        )
                    }
                }
            }

            // Restore Purchases & Manage Subscription Controls
            item {
                GameBoostSurfaceCard {
                    Text(
                        text = "Account & Subscription Controls",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { billingManager.restorePurchases() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("premium_restore_purchases_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Purchases", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(billingManager.buildManageSubscriptionIntent(context))
                                    manageActionError = null
                                } catch (_: Exception) {
                                    manageActionError = "Google Play Store app not available to open subscriptions page."
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("premium_manage_subscription_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Subscription", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!manageActionError.isNullOrBlank()) {
                        Text(
                            text = manageActionError.orEmpty(),
                            color = NeonOrange,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Comparison Table Card
            item {
                GameBoostSurfaceCard {
                    Text(
                        text = "Free vs Pro Feature Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Capability / Module", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1.8f))
                        Text("Free", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Pro", style = MaterialTheme.typography.labelSmall, color = NeonPurpleBright, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider(color = DarkSurfaceBorder)

                    ComparisonRow("Core Hardware Diagnostics", "Included", "Included")
                    ComparisonRow("Gateway Latency & Jitter", "Included", "Included")
                    ComparisonRow("Benchmark Lab & Aim Lab", "Included", "Included")
                    ComparisonRow("Game Space & Autopilot 3.0", "Included", "Included")
                    ComparisonRow("Advertisements", "Home & Reports", "Completely Ad-Free")
                    ComparisonRow("Overlay Themes & Accents", "Cyan Default", "Purple / Green / OLED")
                    ComparisonRow("Game Profile Customization", "3 Core Profiles", "+ Pro Presets & Tags")
                    ComparisonRow("Advanced Session Reports", "Standard", "Efficiency & Export")
                    ComparisonRow("Report History & Compare", "Last 10 Runs", "Extended (50+ Runs)")
                }
            }

            // Renewal & Cancellation Disclosure Card
            item {
                GameBoostSurfaceCard(containerColor = DarkSurfaceElevated) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Renewal, Cancellation & Verification Policy",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Billing & Auto-Renewal: Subscriptions are billed exclusively through your Google Play account and renew automatically unless cancelled at least 24 hours before the end of the current period.\n" +
                            "• Easy Cancellation: Tap 'Manage Subscription' above or open Google Play → Payments & subscriptions anytime. Cancelled plans stay active until the paid period ends.\n" +
                            "• Pending Payments & Refunds: Pending payments unlock Pro only after Google Play confirms payment. Refunded, voided, or expired subscriptions revoke Pro access automatically.\n" +
                            "• Essential Tools Always Free: Hardware telemetry, Network diagnostics, Benchmark Lab, Aim Lab, Game Space, and Autopilot 3.0 remain 100% Free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingFeedbackBanner(billingState: BillingUiState) {
    val (tint, text) = when (val status = billingState.purchaseStatus) {
        is PurchaseFlowStatus.VerifiedActive -> NeonGreen to status.message
        is PurchaseFlowStatus.Pending -> NeonOrange to status.message
        is PurchaseFlowStatus.Cancelled -> TextSecondary to status.message
        is PurchaseFlowStatus.Failed -> NeonRed to status.message
        is PurchaseFlowStatus.Restoring -> NeonCyan to "Restoring purchases from Google Play..."
        is PurchaseFlowStatus.LaunchingCheckout -> NeonCyan to "Opening Google Play checkout for ${status.productId}..."
        PurchaseFlowStatus.Connecting -> NeonCyan to "Connecting to Google Play Billing..."
        PurchaseFlowStatus.Idle -> when (billingState.connectionState) {
            BillingConnectionState.CONNECTED -> NeonGreen to billingState.statusBannerMessage
            BillingConnectionState.CONNECTING -> NeonCyan to billingState.statusBannerMessage
            BillingConnectionState.DISCONNECTED,
            BillingConnectionState.SERVICE_UNAVAILABLE -> TextMuted to billingState.statusBannerMessage
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("premium_billing_feedback_banner"),
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PlanOfferCard(
    planName: String,
    productId: String,
    offer: BillingOffer?,
    subtitle: String,
    accentColor: Color,
    isCurrentlyActive: Boolean,
    isBillingReady: Boolean,
    isPurchaseInProgress: Boolean,
    buttonTag: String,
    onSubscribeClick: () -> Unit
) {
    GameBoostSurfaceCard(
        borderColor = if (isCurrentlyActive) accentColor else DarkSurfaceBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = planName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "SKU: $productId",
                    style = TelemetryMonoStyle.copy(fontSize = 10.sp),
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (offer != null && offer.isAvailableInPlayStore) {
                    Text(
                        text = offer.formattedPrice,
                        style = TelemetryMonoStyle.copy(fontSize = 19.sp),
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = offer.billingPeriodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "Price Pending",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Play Store SKU",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        if (offer?.introSummary != null) {
            StatusPillBadge(text = offer.introSummary, color = accentColor, showDot = false)
        }

        val canTapPurchase = !isCurrentlyActive && !isPurchaseInProgress && isBillingReady && offer != null
        Button(
            onClick = onSubscribeClick,
            enabled = canTapPurchase,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(buttonTag),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black,
                disabledContainerColor = DarkSurfaceElevated,
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                text = when {
                    isCurrentlyActive -> "Active Plan"
                    isPurchaseInProgress -> "Processing..."
                    offer == null -> "Awaiting Google Play Product Price"
                    !isBillingReady -> "Google Play Not Connected"
                    else -> "Subscribe (${offer.formattedPrice} ${offer.billingPeriodLabel})"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ComparisonRow(feature: String, free: String, pro: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(feature, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1.8f))
        Text(free, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(pro, style = MaterialTheme.typography.labelSmall, color = NeonPurpleBright, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

private tailrec fun Context.findBillingActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findBillingActivity()
    else -> null
}
