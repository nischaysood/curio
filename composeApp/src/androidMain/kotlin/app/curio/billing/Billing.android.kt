package app.curio.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** The entitlement identifier configured in the RevenueCat dashboard. */
private const val PREMIUM_ENTITLEMENT = "premium"

@Composable
actual fun rememberBilling(): Billing {
    val context = LocalContext.current
    return remember(context) { AndroidBilling(context) }
}

private class AndroidBilling(private val context: Context) : Billing {

    /**
     * Every failure path returns FREE rather than throwing.
     *
     * A billing outage should cost someone their premium features temporarily —
     * annoying, recoverable, and they can hit Restore. Crashing the app instead
     * turns a vendor's bad afternoon into a one-star review.
     */
    override suspend fun tier(): Tier = suspendCoroutine { cont ->
        if (!Purchases.isConfigured) {
            cont.resume(Tier.FREE)
            return@suspendCoroutine
        }
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { cont.resume(Tier.FREE) },
            onSuccess = { cont.resume(it.tier()) },
        )
    }

    override suspend fun offerings(): List<Product> = suspendCoroutine { cont ->
        if (!Purchases.isConfigured) {
            cont.resume(emptyList())
            return@suspendCoroutine
        }
        Purchases.sharedInstance.getOfferingsWith(
            onError = { cont.resume(emptyList()) },
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages.orEmpty()
                cont.resume(packages.map { it.product.toProduct() })
            },
        )
    }

    override suspend fun purchase(productId: String): PurchaseResult = suspendCoroutine { cont ->
        val activity = context as? Activity
        if (activity == null || !Purchases.isConfigured) {
            cont.resume(PurchaseResult.Failed("Purchases aren't available right now."))
            return@suspendCoroutine
        }

        Purchases.sharedInstance.getOfferingsWith(
            onError = { cont.resume(PurchaseResult.Failed(it.friendly())) },
            onSuccess = { offerings ->
                val target = offerings.current?.availablePackages
                    ?.firstOrNull { it.product.id == productId }

                if (target == null) {
                    cont.resume(PurchaseResult.Failed("That plan isn't available."))
                    return@getOfferingsWith
                }

                Purchases.sharedInstance.purchaseWith(
                    purchaseParams = com.revenuecat.purchases.PurchaseParams.Builder(activity, target).build(),
                    onError = { error, userCancelled ->
                        // Cancelling is a normal choice, not a failure. Showing an
                        // error for it makes backing out of a paywall feel punitive.
                        cont.resume(
                            if (userCancelled) PurchaseResult.Cancelled
                            else PurchaseResult.Failed(error.friendly()),
                        )
                    },
                    onSuccess = { _, info -> cont.resume(PurchaseResult.Success(info.tier())) },
                )
            },
        )
    }

    override suspend fun restore(): Tier = suspendCoroutine { cont ->
        if (!Purchases.isConfigured) {
            cont.resume(Tier.FREE)
            return@suspendCoroutine
        }
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { cont.resume(Tier.FREE) },
            onSuccess = { cont.resume(it.tier()) },
        )
    }

    override suspend fun logIn(appUserId: String): Tier = suspendCoroutine { cont ->
        if (!Purchases.isConfigured) {
            cont.resume(Tier.FREE)
            return@suspendCoroutine
        }
        Purchases.sharedInstance.logInWith(
            appUserID = appUserId,
            onError = {
                // Identity is a nicety; a failure here must not block sign-in.
                // The user stays on their anonymous id and Restore still works
                // on this device.
                cont.resume(Tier.FREE)
            },
            onSuccess = { info, _ -> cont.resume(info.tier()) },
        )
    }

    override suspend fun logOut() = suspendCoroutine { cont ->
        // logOut() throws if already anonymous, which is a normal state to be in
        // — signing out twice, or signing out having never signed in.
        if (!Purchases.isConfigured || Purchases.sharedInstance.isAnonymous) {
            cont.resume(Unit)
            return@suspendCoroutine
        }
        Purchases.sharedInstance.logOutWith(
            onError = { cont.resume(Unit) },
            onSuccess = { cont.resume(Unit) },
        )
    }
}

private fun CustomerInfo.tier(): Tier =
    if (entitlements[PREMIUM_ENTITLEMENT]?.isActive == true) Tier.PREMIUM else Tier.FREE

private fun StoreProduct.toProduct(): Product = Product(
    id = id,
    // Always the store's localised string. Formatting currency yourself gets it
    // wrong for someone, and that someone is usually not in your timezone.
    price = price.formatted,
    period = if (period?.iso8601?.contains("Y") == true) Period.ANNUAL else Period.MONTHLY,
)

/** Store errors are written for developers. Users get something they can act on. */
private fun PurchasesError.friendly(): String = when (code) {
    PurchasesErrorCode.NetworkError -> "Couldn't reach the store. Check your connection."
    PurchasesErrorCode.PurchaseNotAllowedError -> "Purchases aren't allowed on this device."
    PurchasesErrorCode.ProductAlreadyPurchasedError -> "You already have this — tap Restore."
    PurchasesErrorCode.StoreProblemError -> "The store is having trouble. Try again shortly."
    else -> "That didn't go through. You haven't been charged."
}
