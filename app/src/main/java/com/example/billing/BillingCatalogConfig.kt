package com.example.billing

import android.content.Context
import com.example.R

/**
 * Configurable product catalog for GAMEBOOST Pro.
 * Product IDs are loaded from string resources (`billing_product_pro_*`) so Play Console
 * SKUs can be adjusted without modifying billing logic.
 */
data class BillingProductCatalog(
    val monthlySubscriptionId: String = DEFAULT_PRO_MONTHLY_ID,
    val yearlySubscriptionId: String = DEFAULT_PRO_YEARLY_ID,
    val oneTimeProductIds: List<String> = emptyList()
) {
    val subscriptionProductIds: List<String>
        get() = listOf(monthlySubscriptionId, yearlySubscriptionId)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val normalizedOneTimeProductIds: List<String>
        get() = oneTimeProductIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val allConfiguredProductIds: Set<String>
        get() = (subscriptionProductIds + normalizedOneTimeProductIds).toSet()

    companion object {
        const val DEFAULT_PRO_MONTHLY_ID = "gameboost_pro_monthly"
        const val DEFAULT_PRO_YEARLY_ID = "gameboost_pro_yearly"

        fun fromResources(context: Context): BillingProductCatalog {
            val monthly = runCatching { context.getString(R.string.billing_product_pro_monthly) }
                .getOrDefault(DEFAULT_PRO_MONTHLY_ID)
                .ifBlank { DEFAULT_PRO_MONTHLY_ID }
            val yearly = runCatching { context.getString(R.string.billing_product_pro_yearly) }
                .getOrDefault(DEFAULT_PRO_YEARLY_ID)
                .ifBlank { DEFAULT_PRO_YEARLY_ID }
            val oneTime = runCatching { context.getString(R.string.billing_product_pro_one_time) }
                .getOrDefault("")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return BillingProductCatalog(
                monthlySubscriptionId = monthly,
                yearlySubscriptionId = yearly,
                oneTimeProductIds = oneTime
            )
        }
    }
}
