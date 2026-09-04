package app.curio.billing

import androidx.compose.runtime.Composable

/**
 * The billing SDK's entire job is to answer one question: is this person
 * premium? Everything that follows from that answer lives in Entitlements.kt,
 * where it can be tested without a store account.
 *
 * Keeping the surface this small is also what makes the store-specific halves
 * cheap — Android and iOS each implement four methods, not a purchasing model.
 */
interface Billing {

    /** Current tier. Cached and refreshed on resume, so reads are cheap. */
    suspend fun tier(): Tier

    /** Products available to buy, already localised and priced by the store. */
    suspend fun offerings(): List<Product>

    /**
     * Launch the store's purchase sheet.
     *
     * Returns the resulting tier rather than a success boolean, because "the
     * user cancelled" and "the purchase failed" both leave them on FREE and the
     * caller doesn't need to care which.
     */
    suspend fun purchase(productId: String): PurchaseResult

    /**
     * Restore prior purchases.
     *
     * Both stores REQUIRE a visible restore path, and a missing one is a common
     * rejection reason. It also matters honestly: someone who paid on another
     * device shouldn't have to pay again.
     */
    suspend fun restore(): Tier
}

data class Product(
    val id: String,
    /** Already formatted in the user's currency by the store — never format this yourself. */
    val price: String,
    val period: Period,
    /** e.g. "Save 40%" — computed by the store, shown as-is. */
    val savings: String? = null,
)

enum class Period { MONTHLY, ANNUAL }

sealed interface PurchaseResult {
    data class Success(val tier: Tier) : PurchaseResult

    /** Not an error. Backing out of a paywall is a normal, frequent choice. */
    data object Cancelled : PurchaseResult

    data class Failed(val message: String) : PurchaseResult
}

@Composable
expect fun rememberBilling(): Billing
