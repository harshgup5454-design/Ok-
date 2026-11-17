package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MembershipTier(val title: String) {
    FREE("Free Explorer"),
    PREMIUM("ZeroX Boost PRO")
}

data class EntitlementState(
    val tier: MembershipTier = MembershipTier.FREE,
    val isPremium: Boolean = false,
    val billingStatusMessage: String = "Free Explorer • Google Play Billing Ready",
    val activeProductId: String? = null,
    val activeOrderId: String? = null,
    val tokenFingerprint: String? = null,
    val isAutoRenewing: Boolean = false,
    val verificationSource: String = "None",
    val isPendingPurchase: Boolean = false,
    val isDevPreviewOverride: Boolean = false,
    val maxExportHistoryCount: Int = 10,
    val allowProThemes: Boolean = false,
    val allowExtendedComparison: Boolean = false,
    val allowOverlayTheming: Boolean = false,
    val allowProProfileCustomization: Boolean = false,
    val allowAdvancedSessionReports: Boolean = false
)

/**
 * Holds the active runtime Pro entitlement state derived strictly from verified
 * Google Play Billing purchase records (never from SharedPreferences booleans).
 */
object MembershipManager {

    private val _entitlement = MutableStateFlow(EntitlementState())
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    fun isPremium(): Boolean = _entitlement.value.isPremium

    fun applyVerifiedPurchase(
        productId: String,
        orderId: String?,
        tokenFingerprint: String,
        isAutoRenewing: Boolean,
        verificationSource: String,
        isSubscription: Boolean = true
    ) {
        val statusMsg = when {
            !isSubscription -> "Pro Verified ($productId • One-Time License)"
            isAutoRenewing -> "Pro Active ($productId • Auto-renewing)"
            else -> "Pro Active ($productId • Cancelled, valid until period end)"
        }
        _entitlement.value = EntitlementState(
            tier = MembershipTier.PREMIUM,
            isPremium = true,
            billingStatusMessage = statusMsg,
            activeProductId = productId,
            activeOrderId = orderId,
            tokenFingerprint = tokenFingerprint,
            isAutoRenewing = isAutoRenewing,
            verificationSource = verificationSource,
            isPendingPurchase = false,
            isDevPreviewOverride = false,
            maxExportHistoryCount = 50,
            allowProThemes = true,
            allowExtendedComparison = true,
            allowOverlayTheming = true,
            allowProProfileCustomization = true,
            allowAdvancedSessionReports = true
        )
    }

    fun applyPendingPurchase(productId: String) {
        _entitlement.update {
            EntitlementState(
                tier = MembershipTier.FREE,
                isPremium = false,
                billingStatusMessage = "Payment pending ($productId) — Pro activates once Google Play confirms payment",
                activeProductId = productId,
                activeOrderId = null,
                tokenFingerprint = null,
                isAutoRenewing = false,
                verificationSource = "Pending Payment",
                isPendingPurchase = true,
                isDevPreviewOverride = false,
                maxExportHistoryCount = 10,
                allowProThemes = false,
                allowExtendedComparison = false,
                allowOverlayTheming = false,
                allowProProfileCustomization = false,
                allowAdvancedSessionReports = false
            )
        }
    }

    fun clearVerifiedEntitlement(
        reasonMessage: String = "Free Explorer • Google Play Billing Ready",
        forceClearDevPreview: Boolean = false
    ) {
        _entitlement.update { current ->
            if (current.isDevPreviewOverride && !forceClearDevPreview) {
                return@update current
            }
            EntitlementState(
                tier = MembershipTier.FREE,
                isPremium = false,
                billingStatusMessage = reasonMessage,
                activeProductId = null,
                activeOrderId = null,
                tokenFingerprint = null,
                isAutoRenewing = false,
                verificationSource = "None",
                isPendingPurchase = false,
                isDevPreviewOverride = false,
                maxExportHistoryCount = 10,
                allowProThemes = false,
                allowExtendedComparison = false,
                allowOverlayTheming = false,
                allowProProfileCustomization = false,
                allowAdvancedSessionReports = false
            )
        }
    }

    /**
     * Preview toggle for DEBUG development / UI layout testing only.
     * Strictly blocked in release builds; does not simulate real billing purchases.
     */
    fun toggleDevPreviewPremium() {
        if (!BuildConfig.DEBUG) {
            return
        }
        _entitlement.update { current ->
            val nextTier = if (current.isPremium) MembershipTier.FREE else MembershipTier.PREMIUM
            val isPrem = nextTier == MembershipTier.PREMIUM
            EntitlementState(
                tier = nextTier,
                isPremium = isPrem,
                billingStatusMessage = if (isPrem) {
                    "Debug UI Preview Only (Not a Play Purchase)"
                } else {
                    "Free Explorer • Google Play Billing Ready"
                },
                activeProductId = if (isPrem) "debug_ui_preview" else null,
                verificationSource = if (isPrem) "Debug Preview Only" else "None",
                isPendingPurchase = false,
                isDevPreviewOverride = isPrem,
                maxExportHistoryCount = if (isPrem) 50 else 10,
                allowProThemes = isPrem,
                allowExtendedComparison = isPrem,
                allowOverlayTheming = isPrem,
                allowProProfileCustomization = isPrem,
                allowAdvancedSessionReports = isPrem
            )
        }
    }

    fun resetForTesting() {
        _entitlement.value = EntitlementState()
    }
}
