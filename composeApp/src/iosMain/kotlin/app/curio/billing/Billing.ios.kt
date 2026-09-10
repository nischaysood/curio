package app.curio.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.curio.platform.BillingBridge
import app.curio.platform.IosBridge
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS billing, delegating to the RevenueCat Swift SDK through [IosBridge].
 *
 * RevenueCat ships purchases-ios as a Swift Package with no Kotlin bindings.
 * Rather than cinterop against it, Swift implements a small callback interface
 * and Kotlin wraps those callbacks back into the suspend API that commonMain
 * already expects — so [Entitlements] and PaywallScreen are untouched.
 *
 * With no bridge registered every answer is FREE and nothing is purchasable.
 * That's the correct behaviour before RevenueCat is configured, and it means the
 * iOS app runs end-to-end from the first build rather than crashing on launch.
 */
@Composable
actual fun rememberBilling(): Billing = remember { IosBilling }

private object IosBilling : Billing {

    private val bridge: BillingBridge? get() = IosBridge.billing

    override suspend fun tier(): Tier = suspendCoroutine { cont ->
        val b = bridge ?: return@suspendCoroutine cont.resume(Tier.FREE)
        b.isPremium { cont.resume(it.toTier()) }
    }

    override suspend fun offerings(): List<Product> = suspendCoroutine { cont ->
        val b = bridge ?: return@suspendCoroutine cont.resume(emptyList())
        b.offerings { products ->
            cont.resume(
                products.map {
                    Product(
                        id = it.id,
                        // Already localised by StoreKit. Never reformat a price.
                        price = it.price,
                        period = if (it.isAnnual) Period.ANNUAL else Period.MONTHLY,
                    )
                },
            )
        }
    }

    override suspend fun purchase(productId: String): PurchaseResult = suspendCoroutine { cont ->
        val b = bridge
            ?: return@suspendCoroutine cont.resume(
                PurchaseResult.Failed("Purchases aren't available right now."),
            )

        b.purchase(productId) { isPremium, cancelled, error ->
            cont.resume(
                when {
                    // Checked first: a cancellation often arrives WITH an error
                    // from StoreKit, and backing out of a paywall must never
                    // show the user a failure message.
                    cancelled -> PurchaseResult.Cancelled
                    error != null -> PurchaseResult.Failed(error)
                    else -> PurchaseResult.Success(isPremium.toTier())
                },
            )
        }
    }

    override suspend fun restore(): Tier = suspendCoroutine { cont ->
        val b = bridge ?: return@suspendCoroutine cont.resume(Tier.FREE)
        b.restore { cont.resume(it.toTier()) }
    }

    override suspend fun logIn(appUserId: String): Tier = suspendCoroutine { cont ->
        val b = bridge ?: return@suspendCoroutine cont.resume(Tier.FREE)
        b.logIn(appUserId) { cont.resume(it.toTier()) }
    }

    override suspend fun logOut(): Unit = suspendCoroutine { cont ->
        val b = bridge ?: return@suspendCoroutine cont.resume(Unit)
        b.logOut { cont.resume(Unit) }
    }
}

private fun Boolean.toTier(): Tier = if (this) Tier.PREMIUM else Tier.FREE
