package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.data.MembershipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

enum class BillingConnectionState(val displayName: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting to Google Play..."),
    CONNECTED("Connected to Google Play Billing"),
    SERVICE_UNAVAILABLE("Google Play Billing Unavailable")
}

data class BillingOffer(
    val productId: String,
    val productType: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val billingPeriodLabel: String,
    val offerToken: String? = null,
    val basePlanId: String? = null,
    val hasIntroOrTrial: Boolean = false,
    val introSummary: String? = null,
    val isAvailableInPlayStore: Boolean = true
)

sealed class PurchaseFlowStatus {
    data object Idle : PurchaseFlowStatus()
    data object Connecting : PurchaseFlowStatus()
    data class LaunchingCheckout(val productId: String) : PurchaseFlowStatus()
    data class Pending(val productId: String, val message: String) : PurchaseFlowStatus()
    data class VerifiedActive(val productId: String, val message: String) : PurchaseFlowStatus()
    data class Cancelled(val message: String) : PurchaseFlowStatus()
    data class Failed(val responseCode: Int, val message: String) : PurchaseFlowStatus()
    data object Restoring : PurchaseFlowStatus()
}

data class BillingUiState(
    val connectionState: BillingConnectionState = BillingConnectionState.DISCONNECTED,
    val catalogLoaded: Boolean = false,
    val catalog: BillingProductCatalog = BillingProductCatalog(),
    val monthlyOffer: BillingOffer? = null,
    val yearlyOffer: BillingOffer? = null,
    val oneTimeOffers: List<BillingOffer> = emptyList(),
    val unfetchedProductIds: List<String> = emptyList(),
    val activeProductId: String? = null,
    val activeOrderId: String? = null,
    val activeTokenFingerprint: String? = null,
    val isAutoRenewing: Boolean = false,
    val verificationSource: VerificationSource = VerificationSource.NONE,
    val purchaseStatus: PurchaseFlowStatus = PurchaseFlowStatus.Idle,
    val statusBannerMessage: String = "Connecting to Google Play Billing...",
    val lastDebugMessage: String? = null
)

interface BillingManager {
    val billingState: StateFlow<BillingUiState>
    fun initialize(context: Context)
    fun onAppForegrounded()
    fun refreshCatalogAndPurchases()
    fun restorePurchases()
    fun launchPurchase(activity: Activity, productId: String)
    fun buildManageSubscriptionIntent(context: Context): Intent
    fun endConnection()
}

/**
 * Pure Kotlin coordinator that validates Google Play purchase payloads through
 * [PurchaseVerificationGateway] and reconciles verified entitlements into [MembershipManager].
 */
class BillingPurchaseCoordinator(
    private val catalog: BillingProductCatalog,
    private val verificationGateway: PurchaseVerificationGateway,
    private val onAcknowledgeRequired: suspend (purchaseToken: String) -> BillingResult?
) {
    suspend fun reconcilePurchases(
        purchases: List<PurchaseVerificationRequest>,
        updateUi: ((BillingUiState) -> BillingUiState) -> Unit
    ) {
        if (purchases.isEmpty()) {
            MembershipManager.clearVerifiedEntitlement(
                reasonMessage = "Free Explorer • No active Google Play Pro purchase found"
            )
            updateUi { state ->
                state.copy(
                    activeProductId = null,
                    activeOrderId = null,
                    activeTokenFingerprint = null,
                    isAutoRenewing = false,
                    verificationSource = VerificationSource.NONE,
                    statusBannerMessage = "No active Google Play Pro subscription or license found."
                )
            }
            return
        }

        var firstVerified: PurchaseVerificationResult.Verified? = null
        var firstPending: PurchaseVerificationResult.Pending? = null
        var firstRejected: PurchaseVerificationResult.Rejected? = null
        var firstExpired: PurchaseVerificationResult.ExpiredOrRevoked? = null

        for (request in purchases) {
            when (val result = verificationGateway.verifyPurchase(request)) {
                is PurchaseVerificationResult.Verified -> {
                    if (firstVerified == null) {
                        firstVerified = result
                    }
                    if (!request.isAcknowledged) {
                        val ackResult = onAcknowledgeRequired(request.purchaseToken)
                        if (ackResult != null && ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            Log.w(
                                "PlayBilling",
                                "Acknowledge returned code=${ackResult.responseCode} token=${PurchaseTokenSecurity.redactToken(request.purchaseToken)}"
                            )
                        }
                    }
                }
                is PurchaseVerificationResult.Pending -> {
                    if (firstPending == null) firstPending = result
                }
                is PurchaseVerificationResult.Rejected -> {
                    if (firstRejected == null) firstRejected = result
                    Log.w(
                        "PlayBilling",
                        "Rejected purchase token=${PurchaseTokenSecurity.redactToken(request.purchaseToken)}: ${result.reason}"
                    )
                }
                is PurchaseVerificationResult.ExpiredOrRevoked -> {
                    if (firstExpired == null) firstExpired = result
                }
            }
        }

        when {
            firstVerified != null -> {
                val verified = firstVerified
                val isSub = verified.productType == BillingClient.ProductType.SUBS
                MembershipManager.applyVerifiedPurchase(
                    productId = verified.productId,
                    orderId = verified.orderId,
                    tokenFingerprint = verified.tokenFingerprint,
                    isAutoRenewing = verified.isAutoRenewing,
                    verificationSource = verified.verificationSource.displayName,
                    isSubscription = isSub
                )
                val statusMsg = when {
                    !isSub -> "Pro verified (One-time purchase)."
                    verified.isAutoRenewing -> "Pro subscription active • Auto-renews via Google Play."
                    else -> "Subscription cancelled • Pro remains active until current billing period ends."
                }
                updateUi { state ->
                    state.copy(
                        activeProductId = verified.productId,
                        activeOrderId = verified.orderId,
                        activeTokenFingerprint = verified.tokenFingerprint,
                        isAutoRenewing = verified.isAutoRenewing,
                        verificationSource = verified.verificationSource,
                        purchaseStatus = PurchaseFlowStatus.VerifiedActive(verified.productId, statusMsg),
                        statusBannerMessage = statusMsg
                    )
                }
            }

            firstPending != null -> {
                val productId = firstPending.productIds.firstOrNull() ?: catalog.monthlySubscriptionId
                MembershipManager.applyPendingPurchase(productId)
                updateUi { state ->
                    state.copy(
                        activeProductId = null,
                        activeOrderId = null,
                        activeTokenFingerprint = null,
                        isAutoRenewing = false,
                        verificationSource = VerificationSource.NONE,
                        purchaseStatus = PurchaseFlowStatus.Pending(productId, firstPending.reason),
                        statusBannerMessage = firstPending.reason
                    )
                }
            }

            firstExpired != null -> {
                MembershipManager.clearVerifiedEntitlement(
                    reasonMessage = "Free Explorer • Subscription expired or refunded",
                    forceClearDevPreview = true
                )
                updateUi { state ->
                    state.copy(
                        activeProductId = null,
                        activeOrderId = null,
                        activeTokenFingerprint = null,
                        isAutoRenewing = false,
                        verificationSource = VerificationSource.NONE,
                        purchaseStatus = PurchaseFlowStatus.Failed(
                            BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
                            firstExpired.reason
                        ),
                        statusBannerMessage = firstExpired.reason
                    )
                }
            }

            firstRejected != null -> {
                MembershipManager.clearVerifiedEntitlement(
                    reasonMessage = "Free Explorer • Purchase verification failed",
                    forceClearDevPreview = true
                )
                updateUi { state ->
                    state.copy(
                        activeProductId = null,
                        activeOrderId = null,
                        activeTokenFingerprint = null,
                        isAutoRenewing = false,
                        verificationSource = VerificationSource.NONE,
                        purchaseStatus = PurchaseFlowStatus.Failed(
                            BillingClient.BillingResponseCode.ERROR,
                            firstRejected.reason
                        ),
                        statusBannerMessage = firstRejected.reason
                    )
                }
            }
        }
    }
}

object GooglePlayBillingManager : BillingManager {
    private const val TAG = "PlayBilling"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initialized = AtomicBoolean(false)

    private val _billingState = MutableStateFlow(BillingUiState())
    override val billingState: StateFlow<BillingUiState> = _billingState.asStateFlow()

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var catalog: BillingProductCatalog = BillingProductCatalog()
    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    @Volatile
    private var customVerificationGateway: PurchaseVerificationGateway? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        handlePurchasesUpdated(billingResult, purchases)
    }

    fun setVerificationGateway(gateway: PurchaseVerificationGateway?) {
        customVerificationGateway = gateway
    }

    override fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        catalog = BillingProductCatalog.fromResources(app)
        _billingState.update { it.copy(catalog = catalog) }

        if (!initialized.compareAndSet(false, true)) {
            connectIfNeeded()
            return
        }

        try {
            val client = BillingClient.newBuilder(app)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .enablePrepaidPlans()
                        .build()
                )
                .enableAutoServiceReconnection()
                .build()
            billingClient = client
            connectIfNeeded()
        } catch (t: Throwable) {
            Log.w(TAG, "BillingClient unavailable in this environment: ${t.message}")
            _billingState.update {
                it.copy(
                    connectionState = BillingConnectionState.SERVICE_UNAVAILABLE,
                    statusBannerMessage = "Google Play Billing unavailable on this device or environment.",
                    lastDebugMessage = t.message
                )
            }
        }
    }

    private fun connectIfNeeded() {
        val client = billingClient ?: return
        if (client.isReady) {
            refreshCatalogAndPurchases()
            return
        }
        _billingState.update {
            it.copy(
                connectionState = BillingConnectionState.CONNECTING,
                statusBannerMessage = "Connecting to Google Play Billing..."
            )
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _billingState.update {
                            it.copy(
                                connectionState = BillingConnectionState.CONNECTED,
                                lastDebugMessage = billingResult.debugMessage
                            )
                        }
                        refreshCatalogAndPurchases()
                    } else {
                        val connState = when (billingResult.responseCode) {
                            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                                BillingConnectionState.SERVICE_UNAVAILABLE
                            else -> BillingConnectionState.DISCONNECTED
                        }
                        _billingState.update {
                            it.copy(
                                connectionState = connState,
                                statusBannerMessage = formatBillingErrorMessage(billingResult),
                                lastDebugMessage = billingResult.debugMessage
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection() handles retries automatically;
                    // track connection state accurately for UI awareness.
                    _billingState.update {
                        it.copy(
                            connectionState = BillingConnectionState.DISCONNECTED,
                            statusBannerMessage = "Google Play Billing connection paused; will reconnect automatically."
                        )
                    }
                }
            })
        } catch (t: Throwable) {
            _billingState.update {
                it.copy(
                    connectionState = BillingConnectionState.SERVICE_UNAVAILABLE,
                    statusBannerMessage = "Unable to connect to Google Play Billing: ${t.message}",
                    lastDebugMessage = t.message
                )
            }
        }
    }

    override fun onAppForegrounded() {
        val client = billingClient
        if (client == null) {
            appContext?.let { initialize(it) }
            return
        }
        if (client.isReady) {
            restorePurchases()
        } else {
            connectIfNeeded()
        }
    }

    override fun refreshCatalogAndPurchases() {
        val client = billingClient ?: return
        scope.launch {
            queryProductDetailsInternal(client)
            queryAndReconcileAllPurchasesInternal(client, isUserRestore = false)
        }
    }

    override fun restorePurchases() {
        val client = billingClient
        if (client == null) {
            _billingState.update {
                it.copy(
                    purchaseStatus = PurchaseFlowStatus.Failed(
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                        "Google Play Billing is not initialized on this device."
                    )
                )
            }
            return
        }
        _billingState.update {
            it.copy(
                purchaseStatus = PurchaseFlowStatus.Restoring,
                statusBannerMessage = "Checking Google Play purchases..."
            )
        }
        if (!client.isReady) {
            connectIfNeeded()
            return
        }
        scope.launch {
            queryProductDetailsInternal(client)
            queryAndReconcileAllPurchasesInternal(client, isUserRestore = true)
        }
    }

    override fun launchPurchase(activity: Activity, productId: String) {
        val client = billingClient
        if (client == null || !client.isReady) {
            _billingState.update {
                it.copy(
                    purchaseStatus = PurchaseFlowStatus.Failed(
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                        "Google Play Billing is not connected. Tap Restore / Reconnect and try again."
                    )
                )
            }
            connectIfNeeded()
            return
        }

        val details = productDetailsById[productId]
        if (details == null) {
            _billingState.update {
                it.copy(
                    purchaseStatus = PurchaseFlowStatus.Failed(
                        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                        "Product '$productId' is not configured or published in Google Play Console yet."
                    )
                )
            }
            refreshCatalogAndPurchases()
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = selectBestSubscriptionOffer(details)?.offerToken
            if (offerToken.isNullOrBlank()) {
                _billingState.update {
                    it.copy(
                        purchaseStatus = PurchaseFlowStatus.Failed(
                            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                            "No eligible subscription base plan or offer found for $productId."
                        )
                    )
                }
                return
            }
            productParamsBuilder.setOfferToken(offerToken)
        } else {
            val oneTimeToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                ?: details.oneTimePurchaseOfferDetails?.let { null }
            if (!oneTimeToken.isNullOrBlank()) {
                productParamsBuilder.setOfferToken(oneTimeToken)
            }
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        _billingState.update {
            it.copy(purchaseStatus = PurchaseFlowStatus.LaunchingCheckout(productId))
        }

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                restorePurchases()
            } else {
                _billingState.update {
                    it.copy(
                        purchaseStatus = PurchaseFlowStatus.Failed(
                            result.responseCode,
                            formatBillingErrorMessage(result)
                        ),
                        lastDebugMessage = result.debugMessage
                    )
                }
            }
        }
    }

    override fun buildManageSubscriptionIntent(context: Context): Intent {
        val activeId = _billingState.value.activeProductId
        val pkg = context.packageName
        val url = if (!activeId.isNullOrBlank()) {
            "https://play.google.com/store/account/subscriptions?sku=${Uri.encode(activeId)}&package=${Uri.encode(pkg)}"
        } else {
            "https://play.google.com/store/account/subscriptions"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun endConnection() {
        try {
            billingClient?.endConnection()
        } catch (_: Throwable) {}
        billingClient = null
        initialized.set(false)
        _billingState.update {
            it.copy(connectionState = BillingConnectionState.DISCONNECTED)
        }
    }

    internal fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases == null) return
                scope.launch {
                    val requests = purchases.map { purchase ->
                        val type = inferProductType(purchase.products)
                        purchase.toVerificationRequest(type)
                    }
                    reconcileRequests(requests)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.update {
                    it.copy(
                        purchaseStatus = PurchaseFlowStatus.Cancelled("Purchase cancelled. No charge was made."),
                        statusBannerMessage = "Purchase cancelled. You remain on your current tier.",
                        lastDebugMessage = billingResult.debugMessage
                    )
                }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                restorePurchases()
            }

            else -> {
                _billingState.update {
                    it.copy(
                        purchaseStatus = PurchaseFlowStatus.Failed(
                            billingResult.responseCode,
                            formatBillingErrorMessage(billingResult)
                        ),
                        statusBannerMessage = formatBillingErrorMessage(billingResult),
                        lastDebugMessage = billingResult.debugMessage
                    )
                }
            }
        }
    }

    private suspend fun queryProductDetailsInternal(client: BillingClient) {
        val unfetched = mutableListOf<String>()

        if (catalog.subscriptionProductIds.isNotEmpty()) {
            val subsProducts = catalog.subscriptionProductIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsProducts)
                .build()

            val (subsResult, subsList, subsUnfetched) = queryProductDetailsSuspend(client, subsParams)
            if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsList.forEach { details ->
                    productDetailsById[details.productId] = details
                }
                unfetched.addAll(subsUnfetched)
            }
        }

        if (catalog.normalizedOneTimeProductIds.isNotEmpty()) {
            val inAppProducts = catalog.normalizedOneTimeProductIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inAppProducts)
                .build()

            val (inAppResult, inAppList, inAppUnfetched) = queryProductDetailsSuspend(client, inAppParams)
            if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppList.forEach { details ->
                    productDetailsById[details.productId] = details
                }
                unfetched.addAll(inAppUnfetched)
            }
        }

        val monthlyOffer = productDetailsById[catalog.monthlySubscriptionId]?.toBillingOffer("/ month")
        val yearlyOffer = productDetailsById[catalog.yearlySubscriptionId]?.toBillingOffer("/ year")
        val oneTimeOffers = catalog.normalizedOneTimeProductIds.mapNotNull { id ->
            productDetailsById[id]?.toBillingOffer("One-time")
        }

        _billingState.update { state ->
            state.copy(
                catalogLoaded = true,
                monthlyOffer = monthlyOffer,
                yearlyOffer = yearlyOffer,
                oneTimeOffers = oneTimeOffers,
                unfetchedProductIds = unfetched.distinct(),
                statusBannerMessage = when {
                    monthlyOffer != null || yearlyOffer != null ->
                        "Google Play catalog loaded. Verified prices ready."
                    unfetched.isNotEmpty() ->
                        "Connected to Google Play. Waiting for Play Console SKU configuration (${unfetched.joinToString()})."
                    else -> state.statusBannerMessage
                }
            )
        }
    }

    private suspend fun queryAndReconcileAllPurchasesInternal(client: BillingClient, isUserRestore: Boolean) {
        val allRequests = mutableListOf<PurchaseVerificationRequest>()
        var queryError: BillingResult? = null

        val subsRes = queryPurchasesSuspend(client, BillingClient.ProductType.SUBS)
        if (subsRes.first.responseCode == BillingClient.BillingResponseCode.OK) {
            subsRes.second.forEach { purchase ->
                allRequests.add(purchase.toVerificationRequest(BillingClient.ProductType.SUBS))
            }
        } else {
            queryError = subsRes.first
        }

        if (catalog.normalizedOneTimeProductIds.isNotEmpty()) {
            val inAppRes = queryPurchasesSuspend(client, BillingClient.ProductType.INAPP)
            if (inAppRes.first.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppRes.second.forEach { purchase ->
                    allRequests.add(purchase.toVerificationRequest(BillingClient.ProductType.INAPP))
                }
            }
        }

        if (queryError != null && allRequests.isEmpty()) {
            _billingState.update {
                it.copy(
                    purchaseStatus = if (isUserRestore) {
                        PurchaseFlowStatus.Failed(queryError.responseCode, formatBillingErrorMessage(queryError))
                    } else it.purchaseStatus,
                    statusBannerMessage = formatBillingErrorMessage(queryError),
                    lastDebugMessage = queryError.debugMessage
                )
            }
            return
        }

        reconcileRequests(allRequests)
        if (isUserRestore && allRequests.isEmpty()) {
            _billingState.update {
                it.copy(
                    purchaseStatus = PurchaseFlowStatus.Idle,
                    statusBannerMessage = "Restore checked: no active Google Play Pro purchases on this account."
                )
            }
        }
    }

    private suspend fun reconcileRequests(requests: List<PurchaseVerificationRequest>) {
        val pkg = appContext?.packageName.orEmpty()
        val gateway = customVerificationGateway ?: PlayPurchasePreCheckVerifier(
            expectedPackageName = pkg,
            allowedProductIds = catalog.allConfiguredProductIds,
            requirePlaySignature = true
        )
        val coordinator = BillingPurchaseCoordinator(
            catalog = catalog,
            verificationGateway = gateway,
            onAcknowledgeRequired = { token -> acknowledgePurchaseSuspend(token) }
        )
        coordinator.reconcilePurchases(requests) { transform ->
            _billingState.update(transform)
        }
    }

    private suspend fun queryProductDetailsSuspend(
        client: BillingClient,
        params: QueryProductDetailsParams
    ): Triple<BillingResult, List<ProductDetails>, List<String>> = suspendCancellableCoroutine { cont ->
        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (cont.isActive) {
                cont.resume(
                    Triple(
                        billingResult,
                        queryProductDetailsResult.productDetailsList,
                        queryProductDetailsResult.unfetchedProductList.map { it.productId }
                    )
                )
            }
        }
    }

    private suspend fun queryPurchasesSuspend(
        client: BillingClient,
        productType: String
    ): Pair<BillingResult, List<Purchase>> = suspendCancellableCoroutine { cont ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (cont.isActive) {
                cont.resume(billingResult to purchases)
            }
        }
    }

    private suspend fun acknowledgePurchaseSuspend(purchaseToken: String): BillingResult? {
        val client = billingClient ?: return null
        if (!client.isReady) return null
        return suspendCancellableCoroutine { cont ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }
    }

    private fun inferProductType(products: List<String>): String {
        val first = products.firstOrNull() ?: return BillingClient.ProductType.SUBS
        val cached = productDetailsById[first]
        if (cached != null) return cached.productType
        return if (first in catalog.normalizedOneTimeProductIds) {
            BillingClient.ProductType.INAPP
        } else {
            BillingClient.ProductType.SUBS
        }
    }

    private fun Purchase.toVerificationRequest(productType: String): PurchaseVerificationRequest {
        return PurchaseVerificationRequest(
            packageName = packageName,
            productIds = products,
            productType = productType,
            purchaseToken = purchaseToken,
            orderId = orderId,
            purchaseTimeMs = purchaseTime,
            purchaseState = purchaseState,
            isAutoRenewing = isAutoRenewing,
            isAcknowledged = isAcknowledged,
            originalJson = originalJson,
            signature = signature,
            quantity = quantity,
            obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId
        )
    }

    private fun selectBestSubscriptionOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        } ?: offers.firstOrNull()
    }

    private fun ProductDetails.toBillingOffer(fallbackPeriodLabel: String): BillingOffer? {
        return if (productType == BillingClient.ProductType.SUBS) {
            val selectedOffer = selectBestSubscriptionOffer(this) ?: return null
            val phases = selectedOffer.pricingPhases.pricingPhaseList
            val recurringPhase = phases.lastOrNull() ?: return null
            val introPhase = if (phases.size > 1) phases.firstOrNull() else null
            val periodText = formatIsoBillingPeriod(recurringPhase.billingPeriod).ifBlank { fallbackPeriodLabel }
            val introSummary = introPhase?.let {
                if (it.priceAmountMicros == 0L) "Includes intro trial (${formatIsoBillingPeriod(it.billingPeriod).removePrefix("/ ")})"
                else "Intro ${it.formattedPrice} ${formatIsoBillingPeriod(it.billingPeriod)}"
            }
            BillingOffer(
                productId = productId,
                productType = productType,
                title = title.ifBlank { name },
                description = description,
                formattedPrice = recurringPhase.formattedPrice,
                billingPeriodLabel = periodText,
                offerToken = selectedOffer.offerToken,
                basePlanId = selectedOffer.basePlanId,
                hasIntroOrTrial = introPhase != null,
                introSummary = introSummary,
                isAvailableInPlayStore = true
            )
        } else {
            val multiOffer = oneTimePurchaseOfferDetailsList?.firstOrNull()
            val formatted = multiOffer?.formattedPrice ?: oneTimePurchaseOfferDetails?.formattedPrice ?: return null
            BillingOffer(
                productId = productId,
                productType = productType,
                title = title.ifBlank { name },
                description = description,
                formattedPrice = formatted,
                billingPeriodLabel = fallbackPeriodLabel,
                offerToken = multiOffer?.offerToken,
                basePlanId = null,
                isAvailableInPlayStore = true
            )
        }
    }

    private fun formatIsoBillingPeriod(isoPeriod: String): String = when (isoPeriod) {
        "P1W" -> "/ week"
        "P1M" -> "/ month"
        "P3M" -> "/ 3 months"
        "P6M" -> "/ 6 months"
        "P1Y" -> "/ year"
        else -> isoPeriod
    }

    private fun formatBillingErrorMessage(result: BillingResult): String {
        val debug = result.debugMessage
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play Billing is unavailable. Ensure Google Play Store is installed and signed in."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Network or Google Play service error. Check connection and try again."
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                "Disconnected from Google Play Billing. Reconnecting..."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "Product is not yet published for this track or country in Google Play Console."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "Billing configuration mismatch. Verify package ID, signature, and Play Console product setup."
            else -> if (debug.isNotBlank()) {
                "Billing failed (code ${result.responseCode}): $debug"
            } else {
                "Billing failed (code ${result.responseCode}). Please try again later."
            }
        }
    }
}
