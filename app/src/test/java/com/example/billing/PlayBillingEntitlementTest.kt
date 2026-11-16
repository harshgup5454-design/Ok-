package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.example.R
import com.example.ads.AdScreenLocation
import com.example.ads.AdVisibilityContext
import com.example.ads.DefaultAdManager
import com.example.data.DeviceStats
import com.example.data.MembershipManager
import com.example.data.MembershipTier
import com.example.ui.screens.PremiumScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.GameBoostTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayBillingEntitlementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        MembershipManager.resetForTesting()
    }

    @After
    fun tearDown() {
        MembershipManager.resetForTesting()
    }

    @Test
    fun testConfigurableProductIdsLoadedFromResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("gameboost_pro_monthly", context.getString(R.string.billing_product_pro_monthly))
        assertEquals("gameboost_pro_yearly", context.getString(R.string.billing_product_pro_yearly))

        val catalog = BillingProductCatalog.fromResources(context)
        assertEquals("gameboost_pro_monthly", catalog.monthlySubscriptionId)
        assertEquals("gameboost_pro_yearly", catalog.yearlySubscriptionId)
        assertTrue(catalog.subscriptionProductIds.containsAll(listOf("gameboost_pro_monthly", "gameboost_pro_yearly")))
    }

    @Test
    fun testPurchaseVerificationHandlesValidPendingRejectedAndExpiredStates() = runTest {
        val catalog = BillingProductCatalog(
            monthlySubscriptionId = "gameboost_pro_monthly",
            yearlySubscriptionId = "gameboost_pro_yearly",
            oneTimeProductIds = listOf("gameboost_pro_pass")
        )
        val verifier = PlayPurchasePreCheckVerifier(
            expectedPackageName = "com.aistudio.gameboost.gmqxw",
            allowedProductIds = catalog.allConfiguredProductIds,
            requirePlaySignature = true
        )

        val ackedTokens = mutableListOf<String>()
        val coordinator = BillingPurchaseCoordinator(
            catalog = catalog,
            verificationGateway = verifier,
            onAcknowledgeRequired = { token ->
                ackedTokens.add(token)
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
            }
        )
        val uiState = MutableStateFlow(BillingUiState(catalog = catalog))

        // 1. Pending purchase must NOT grant Pro and must NOT acknowledge
        coordinator.reconcilePurchases(
            purchases = listOf(
                PurchaseVerificationRequest(
                    packageName = "com.aistudio.gameboost.gmqxw",
                    productIds = listOf("gameboost_pro_monthly"),
                    productType = BillingClient.ProductType.SUBS,
                    purchaseToken = "tok_pending_123456",
                    orderId = "GPA.0001",
                    purchaseTimeMs = 1000L,
                    purchaseState = Purchase.PurchaseState.PENDING,
                    isAutoRenewing = true,
                    isAcknowledged = false,
                    originalJson = "{}",
                    signature = "sig"
                )
            ),
            updateUi = { transform -> uiState.value = transform(uiState.value) }
        )
        assertFalse(MembershipManager.isPremium())
        assertTrue(MembershipManager.entitlement.value.isPendingPurchase)
        assertTrue(ackedTokens.isEmpty())
        assertTrue(uiState.value.purchaseStatus is PurchaseFlowStatus.Pending)

        // 2. Tampered / unsigned or mismatched package purchase must NOT grant Pro
        coordinator.reconcilePurchases(
            purchases = listOf(
                PurchaseVerificationRequest(
                    packageName = "com.attacker.spoof",
                    productIds = listOf("gameboost_pro_monthly"),
                    productType = BillingClient.ProductType.SUBS,
                    purchaseToken = "tok_bad_999",
                    orderId = "GPA.0002",
                    purchaseTimeMs = 1000L,
                    purchaseState = Purchase.PurchaseState.PURCHASED,
                    isAutoRenewing = true,
                    isAcknowledged = false,
                    originalJson = "{}",
                    signature = "sig"
                )
            ),
            updateUi = { transform -> uiState.value = transform(uiState.value) }
        )
        assertFalse(MembershipManager.isPremium())
        assertTrue(ackedTokens.isEmpty())
        assertTrue(uiState.value.purchaseStatus is PurchaseFlowStatus.Failed)

        // 3. Valid unacknowledged monthly subscription grants Pro and gets acknowledged
        coordinator.reconcilePurchases(
            purchases = listOf(
                PurchaseVerificationRequest(
                    packageName = "com.aistudio.gameboost.gmqxw",
                    productIds = listOf("gameboost_pro_monthly"),
                    productType = BillingClient.ProductType.SUBS,
                    purchaseToken = "tok_valid_monthly_abcdef",
                    orderId = "GPA.1234-5678-9012",
                    purchaseTimeMs = 2000L,
                    purchaseState = Purchase.PurchaseState.PURCHASED,
                    isAutoRenewing = true,
                    isAcknowledged = false,
                    originalJson = "{\"orderId\":\"GPA.1234-5678-9012\"}",
                    signature = "valid_rsa_sig_payload"
                )
            ),
            updateUi = { transform -> uiState.value = transform(uiState.value) }
        )
        assertTrue(MembershipManager.isPremium())
        assertEquals(MembershipTier.PREMIUM, MembershipManager.entitlement.value.tier)
        assertEquals("gameboost_pro_monthly", MembershipManager.entitlement.value.activeProductId)
        assertTrue(MembershipManager.entitlement.value.allowOverlayTheming)
        assertTrue(MembershipManager.entitlement.value.allowAdvancedSessionReports)
        assertEquals(50, MembershipManager.entitlement.value.maxExportHistoryCount)
        assertEquals(listOf("tok_valid_monthly_abcdef"), ackedTokens)
        assertNotEquals("tok_valid_monthly_abcdef", MembershipManager.entitlement.value.tokenFingerprint)

        // 4. Cancelled auto-renew still valid until period end
        coordinator.reconcilePurchases(
            purchases = listOf(
                PurchaseVerificationRequest(
                    packageName = "com.aistudio.gameboost.gmqxw",
                    productIds = listOf("gameboost_pro_yearly"),
                    productType = BillingClient.ProductType.SUBS,
                    purchaseToken = "tok_cancelled_yearly",
                    orderId = "GPA.2222-3333",
                    purchaseTimeMs = 3000L,
                    purchaseState = Purchase.PurchaseState.PURCHASED,
                    isAutoRenewing = false,
                    isAcknowledged = true,
                    originalJson = "{\"orderId\":\"GPA.2222-3333\"}",
                    signature = "valid_rsa_sig_payload"
                )
            ),
            updateUi = { transform -> uiState.value = transform(uiState.value) }
        )
        assertTrue(MembershipManager.isPremium())
        assertFalse(MembershipManager.entitlement.value.isAutoRenewing)
        assertTrue(MembershipManager.entitlement.value.billingStatusMessage.contains("Cancelled"))
        assertEquals(1, ackedTokens.size) // Already acknowledged -> not re-acknowledged

        // 5. Subscription expired / refunded (Play returns empty active list) revokes Pro cleanly
        coordinator.reconcilePurchases(
            purchases = emptyList(),
            updateUi = { transform -> uiState.value = transform(uiState.value) }
        )
        assertFalse(MembershipManager.isPremium())
        assertEquals(MembershipTier.FREE, MembershipManager.entitlement.value.tier)
        assertNull(MembershipManager.entitlement.value.activeProductId)
        assertFalse(MembershipManager.entitlement.value.allowOverlayTheming)
        assertEquals(10, MembershipManager.entitlement.value.maxExportHistoryCount)
    }

    @Test
    fun testAdSuppressionRespondsDynamicallyToVerifiedBillingAndRevocation() {
        val adManager = DefaultAdManager(isProEntitlementActive = { MembershipManager.isPremium() }).apply {
            updateStateForTesting {
                it.copy(canRequestAds = true, isNetworkAvailable = true, isSdkInitialized = true)
            }
        }

        val homeCtx = AdVisibilityContext(location = AdScreenLocation.HOME_DASHBOARD)
        val reportsCtx = AdVisibilityContext(location = AdScreenLocation.REPORTS_BANNER)

        // Free tier allows eligible Home & Reports ads
        assertFalse(adManager.isAdFreeEntitled())
        assertTrue(adManager.canDisplayAds(homeCtx))
        assertTrue(adManager.canDisplayAds(reportsCtx))

        // Verified Pro purchase suppresses all ads immediately
        MembershipManager.applyVerifiedPurchase(
            productId = "gameboost_pro_yearly",
            orderId = "GPA.9999",
            tokenFingerprint = "a1b2c3d4e5f6",
            isAutoRenewing = true,
            verificationSource = VerificationSource.PLAY_STORE_CLIENT_PRECHECK.displayName
        )
        assertTrue(adManager.isAdFreeEntitled())
        assertFalse(adManager.canDisplayAds(homeCtx))
        assertFalse(adManager.canDisplayAds(reportsCtx))

        // Revoked / refunded / expired subscription re-enables eligible ads cleanly
        MembershipManager.clearVerifiedEntitlement("Expired")
        assertFalse(adManager.isAdFreeEntitled())
        assertTrue(adManager.canDisplayAds(homeCtx))
    }

    @Test
    fun testBillingManagerHandlesCancelledAndFailedResponsesWithoutGrantingEntitlement() {
        val cancelResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED)
            .build()
        GooglePlayBillingManager.handlePurchasesUpdated(cancelResult, null)
        assertFalse(MembershipManager.isPremium())
        assertTrue(GooglePlayBillingManager.billingState.value.purchaseStatus is PurchaseFlowStatus.Cancelled)

        val errorResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
            .build()
        GooglePlayBillingManager.handlePurchasesUpdated(errorResult, null)
        assertFalse(MembershipManager.isPremium())
        assertTrue(GooglePlayBillingManager.billingState.value.purchaseStatus is PurchaseFlowStatus.Failed)
    }

    @Test
    fun testPremiumScreenDisplaysActualCatalogPricesAndRestoreControls() {
        val catalog = BillingProductCatalog(
            monthlySubscriptionId = "gameboost_pro_monthly",
            yearlySubscriptionId = "gameboost_pro_yearly"
        )
        val fakeBillingManager = FakeBillingManager(
            BillingUiState(
                connectionState = BillingConnectionState.CONNECTED,
                catalogLoaded = true,
                catalog = catalog,
                monthlyOffer = BillingOffer(
                    productId = "gameboost_pro_monthly",
                    productType = BillingClient.ProductType.SUBS,
                    title = "GAMEBOOST Pro Monthly",
                    description = "Monthly Pro access",
                    formattedPrice = "$2.99",
                    billingPeriodLabel = "/ month",
                    offerToken = "offer_m"
                ),
                yearlyOffer = BillingOffer(
                    productId = "gameboost_pro_yearly",
                    productType = BillingClient.ProductType.SUBS,
                    title = "GAMEBOOST Pro Yearly",
                    description = "Yearly Pro access",
                    formattedPrice = "$19.99",
                    billingPeriodLabel = "/ year",
                    offerToken = "offer_y"
                ),
                statusBannerMessage = "Google Play catalog loaded. Verified prices ready."
            )
        )

        composeTestRule.setContent {
            GameBoostTheme {
                PremiumScreen(billingManager = fakeBillingManager)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("premium_lazy_column")
            .performScrollToNode(hasTestTag("premium_subscribe_monthly_button"))
        composeTestRule.onNodeWithText("$2.99").assertIsDisplayed()
        composeTestRule.onNodeWithTag("premium_lazy_column")
            .performScrollToNode(hasTestTag("premium_subscribe_yearly_button"))
        composeTestRule.onNodeWithText("$19.99").assertIsDisplayed()

        composeTestRule.onNodeWithTag("premium_lazy_column")
            .performScrollToNode(hasTestTag("premium_restore_purchases_button"))
        composeTestRule.onNodeWithTag("premium_restore_purchases_button").performClick()
        assertTrue(fakeBillingManager.restoreCalled)
        composeTestRule.onNodeWithTag("premium_manage_subscription_button").assertIsDisplayed()
    }

    @Test
    fun testPremiumScreenShowsHonestPendingPriceStateWhenDisconnected() {
        val fakeBillingManager = FakeBillingManager(
            BillingUiState(
                connectionState = BillingConnectionState.SERVICE_UNAVAILABLE,
                catalogLoaded = false,
                monthlyOffer = null,
                yearlyOffer = null,
                statusBannerMessage = "Google Play Billing unavailable on this device or environment."
            )
        )

        composeTestRule.setContent {
            GameBoostTheme {
                PremiumScreen(billingManager = fakeBillingManager)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("premium_lazy_column")
            .performScrollToNode(hasTestTag("premium_subscribe_monthly_button"))
        composeTestRule.onNodeWithTag("premium_subscribe_monthly_button").assertExists()
        composeTestRule.onNodeWithTag("premium_subscribe_yearly_button").assertExists()
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Awaiting Google Play Product Price"))
            .fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test
    fun testReportsScreenShowsProExportWhenVerifiedProActive() {
        MembershipManager.applyVerifiedPurchase(
            productId = "gameboost_pro_yearly",
            orderId = "GPA.777",
            tokenFingerprint = "f1e2d3c4b5a6",
            isAutoRenewing = true,
            verificationSource = VerificationSource.PLAY_STORE_CLIENT_PRECHECK.displayName
        )

        composeTestRule.setContent {
            GameBoostTheme {
                ReportsScreen(
                    stats = DeviceStats(deviceModel = "TestPhone"),
                    isBenchmarking = false,
                    benchmarkSeconds = 0,
                    benchmarkSessions = emptyList(),
                    onStartBenchmark = { _, _, _, _, _ -> },
                    onStopBenchmark = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reports_lazy_column")
            .performScrollToNode(hasTestTag("reports_pro_export_button"))
        composeTestRule.onNodeWithTag("reports_pro_export_button").assertIsDisplayed()
    }

    private class FakeBillingManager(initialState: BillingUiState) : BillingManager {
        private val _state = MutableStateFlow(initialState)
        override val billingState: StateFlow<BillingUiState> = _state.asStateFlow()
        var restoreCalled = false

        override fun initialize(context: Context) {}
        override fun onAppForegrounded() {}
        override fun refreshCatalogAndPurchases() {}
        override fun restorePurchases() {
            restoreCalled = true
        }
        override fun launchPurchase(activity: Activity, productId: String) {}
        override fun buildManageSubscriptionIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW)
        override fun endConnection() {}
    }
}
