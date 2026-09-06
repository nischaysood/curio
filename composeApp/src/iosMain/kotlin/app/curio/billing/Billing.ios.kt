package app.curio.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Stub until the iOS project exists.
 *
 * Everyone is FREE and nothing is purchasable, which is correct rather than
 * merely convenient: shipping an iOS build where the paywall silently no-ops
 * would be worse than one where it visibly isn't there yet.
 *
 * When iosApp lands, this gets the native RevenueCat iOS SDK and the same four
 * methods. Nothing in commonMain changes — Entitlements.kt already holds every
 * rule, and it's already tested.
 */
@Composable
actual fun rememberBilling(): Billing = remember { IosBilling }

private object IosBilling : Billing {
    override suspend fun tier(): Tier = Tier.FREE
    override suspend fun offerings(): List<Product> = emptyList()
    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Failed("Purchases aren't available on iOS yet.")
    override suspend fun restore(): Tier = Tier.FREE
    override suspend fun logIn(appUserId: String): Tier = Tier.FREE
    override suspend fun logOut() = Unit
}
