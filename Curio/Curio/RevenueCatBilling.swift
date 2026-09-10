import Foundation
import RevenueCat
import ComposeApp

/// RevenueCat implementation of the Kotlin `BillingBridge` protocol.
///
/// Everything about *what premium unlocks* lives in Kotlin, in `Entitlements.kt`,
/// where it's unit tested. This file only answers "is this person premium" and
/// "make a purchase happen" — the same four questions the Android side answers.
/// Keeping the surface this small is what makes the second platform cheap.
///
/// Every failure path resolves to `false` (free) rather than throwing. A billing
/// outage should briefly cost someone their premium features — annoying,
/// recoverable, and Restore fixes it — rather than crash the app.
final class RevenueCatBilling: BillingBridge {

    /// Must match the entitlement identifier in the RevenueCat dashboard,
    /// Billing.android.kt, and the Worker's tier lookup.
    private let entitlementID = "premium"

    func isPremium(onResult: @escaping (KotlinBoolean) -> Void) {
        Purchases.shared.getCustomerInfo { info, _ in
            onResult(KotlinBoolean(bool: info?.isPremium(self.entitlementID) ?? false))
        }
    }

    func offerings(onResult: @escaping ([BridgeProduct]) -> Void) {
        Purchases.shared.getOfferings { offerings, _ in
            guard let packages = offerings?.current?.availablePackages else {
                onResult([])
                return
            }
            onResult(packages.map { package in
                BridgeProduct(
                    id: package.storeProduct.productIdentifier,
                    // StoreKit's localised string. Never format currency by hand.
                    price: package.storeProduct.localizedPriceString,
                    isAnnual: package.packageType == .annual
                )
            })
        }
    }

    func purchase(
        productId: String,
        onResult: @escaping (KotlinBoolean, KotlinBoolean, String?) -> Void
    ) {
        Purchases.shared.getOfferings { offerings, _ in
            guard let package = offerings?.current?.availablePackages.first(
                where: { $0.storeProduct.productIdentifier == productId }
            ) else {
                onResult(KotlinBoolean(bool: false), KotlinBoolean(bool: false),
                         "That plan isn't available.")
                return
            }

            Purchases.shared.purchase(package: package) { _, info, error, userCancelled in
                // Cancellation is reported first and without an error message.
                // Backing out of a paywall is a normal choice, and telling
                // someone it "failed" makes the app feel resentful.
                if userCancelled {
                    onResult(KotlinBoolean(bool: false), KotlinBoolean(bool: true), nil)
                    return
                }
                if error != nil {
                    onResult(KotlinBoolean(bool: false), KotlinBoolean(bool: false),
                             "That didn't go through. You haven't been charged.")
                    return
                }
                onResult(
                    KotlinBoolean(bool: info?.isPremium(self.entitlementID) ?? false),
                    KotlinBoolean(bool: false),
                    nil
                )
            }
        }
    }

    func restore(onResult: @escaping (KotlinBoolean) -> Void) {
        Purchases.shared.restorePurchases { info, _ in
            onResult(KotlinBoolean(bool: info?.isPremium(self.entitlementID) ?? false))
        }
    }

    /// Bind purchases to a Curio account.
    ///
    /// Until this runs a subscription belongs to an anonymous per-install ID: it
    /// survives a restart but not a new phone, and the user can't prove it was
    /// theirs. Logging in also merges any anonymous purchase made before signing
    /// up, which is the common order — people buy first and create an account
    /// later.
    func logIn(appUserId: String, onResult: @escaping (KotlinBoolean) -> Void) {
        Purchases.shared.logIn(appUserId) { info, _, _ in
            onResult(KotlinBoolean(bool: info?.isPremium(self.entitlementID) ?? false))
        }
    }

    /// Return to an anonymous ID, so the next person to use this device doesn't
    /// inherit the previous user's subscription.
    func logOut(onResult: @escaping () -> Void) {
        guard !Purchases.shared.isAnonymous else {
            onResult()
            return
        }
        Purchases.shared.logOut { _, _ in onResult() }
    }
}

private extension CustomerInfo {
    func isPremium(_ id: String) -> Bool {
        entitlements[id]?.isActive == true
    }
}
