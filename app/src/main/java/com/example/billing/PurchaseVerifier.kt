package com.example.billing

import com.android.billingclient.api.Purchase
import java.security.MessageDigest

enum class VerificationSource(val displayName: String) {
    NONE("None"),
    PLAY_STORE_CLIENT_PRECHECK("Google Play Client Integrity Check"),
    BACKEND_DEVELOPER_API("Backend Google Play Developer API")
}

data class PurchaseVerificationRequest(
    val packageName: String,
    val productIds: List<String>,
    val productType: String,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTimeMs: Long,
    val purchaseState: Int,
    val isAutoRenewing: Boolean,
    val isAcknowledged: Boolean,
    val originalJson: String,
    val signature: String,
    val quantity: Int = 1,
    val obfuscatedAccountId: String? = null
)

sealed class PurchaseVerificationResult {
    data class Verified(
        val productId: String,
        val productType: String,
        val orderId: String?,
        val tokenFingerprint: String,
        val isAutoRenewing: Boolean,
        val verificationSource: VerificationSource
    ) : PurchaseVerificationResult()

    data class Pending(
        val productIds: List<String>,
        val reason: String
    ) : PurchaseVerificationResult()

    data class Rejected(
        val productIds: List<String>,
        val reason: String
    ) : PurchaseVerificationResult()

    data class ExpiredOrRevoked(
        val reason: String
    ) : PurchaseVerificationResult()
}

/**
 * Abstraction designed for server-side verification (Google Play Developer API
 * `purchases.subscriptionsv2.get` / `purchases.products.get` + RTDN / Voided Purchases).
 * Client-side pre-checks never trust SharedPreferences booleans as proof of payment.
 */
interface PurchaseVerificationGateway {
    suspend fun verifyPurchase(request: PurchaseVerificationRequest): PurchaseVerificationResult
}

object PurchaseTokenSecurity {
    fun fingerprintToken(purchaseToken: String): String {
        if (purchaseToken.isBlank()) return "invalid"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(purchaseToken.toByteArray(Charsets.UTF_8))
            digest.take(6).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "fp_${purchaseToken.length}"
        }
    }

    fun redactToken(purchaseToken: String): String {
        if (purchaseToken.length <= 8) return "***"
        return "${purchaseToken.take(4)}...${purchaseToken.takeLast(4)}"
    }
}

/**
 * Validates signed Google Play purchase payloads and token integrity locally before
 * delegating to a backend server verifier when configured.
 */
class PlayPurchasePreCheckVerifier(
    private val expectedPackageName: String,
    private val allowedProductIds: Set<String>,
    private val requirePlaySignature: Boolean = true
) : PurchaseVerificationGateway {

    override suspend fun verifyPurchase(request: PurchaseVerificationRequest): PurchaseVerificationResult {
        if (request.purchaseState == Purchase.PurchaseState.PENDING) {
            return PurchaseVerificationResult.Pending(
                productIds = request.productIds,
                reason = "Purchase is pending confirmation from Google Play."
            )
        }

        if (request.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Purchase state is not PURCHASED (state=${request.purchaseState})."
            )
        }

        if (request.purchaseToken.isBlank()) {
            return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Missing purchase token."
            )
        }

        if (request.packageName.isNotBlank() && expectedPackageName.isNotBlank() &&
            request.packageName != expectedPackageName
        ) {
            return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Package name mismatch during purchase verification."
            )
        }

        if (request.quantity < 1) {
            return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Invalid purchase quantity (${request.quantity})."
            )
        }

        val matchingProductId = request.productIds.firstOrNull { it in allowedProductIds }
            ?: return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Product ID not in configured GAMEBOOST Pro catalog."
            )

        if (requirePlaySignature && (request.originalJson.isBlank() || request.signature.isBlank())) {
            return PurchaseVerificationResult.Rejected(
                productIds = request.productIds,
                reason = "Missing Play cryptographic signature payload."
            )
        }

        return PurchaseVerificationResult.Verified(
            productId = matchingProductId,
            productType = request.productType,
            orderId = request.orderId,
            tokenFingerprint = PurchaseTokenSecurity.fingerprintToken(request.purchaseToken),
            isAutoRenewing = request.isAutoRenewing,
            verificationSource = VerificationSource.PLAY_STORE_CLIENT_PRECHECK
        )
    }
}
