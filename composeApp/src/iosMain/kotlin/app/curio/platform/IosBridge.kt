package app.curio.platform

/**
 * The seam between Kotlin and Swift.
 *
 * Two things on iOS are genuinely better written in Swift than in Kotlin/Native:
 * the Keychain (Security framework is a wall of CFDictionary pointer work from
 * Kotlin, and thirty readable lines from Swift) and RevenueCat (a Swift Package
 * with no Kotlin bindings; cinterop against it is a day of yak-shaving).
 *
 * So rather than reaching down into those APIs from Kotlin, Kotlin declares what
 * it needs and Swift provides it at launch. Kotlin/Native exports these
 * interfaces to Swift as protocols, so the Swift side is ordinary Swift.
 *
 * Everything here is callback-based rather than suspend. Kotlin suspend
 * functions are exported to Swift as completion handlers for CALLING, but
 * implementing one from Swift is awkward — plain callbacks keep the Swift side
 * boring, and the Kotlin side wraps them back into suspend where it's used.
 */

/** Keychain-backed storage. Implemented in Swift, called from Kotlin. */
interface SecureStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

/** What the RevenueCat iOS SDK can answer, in callback form. */
interface BillingBridge {
    /** true when the `premium` entitlement is active. */
    fun isPremium(onResult: (Boolean) -> Unit)

    /** Products in the current offering: id, localised price, and whether annual. */
    fun offerings(onResult: (List<BridgeProduct>) -> Unit)

    /**
     * Purchase. [onResult] receives (isPremium, cancelled, errorMessage).
     * Cancellation is not an error — see PurchaseResult on the Kotlin side.
     */
    fun purchase(productId: String, onResult: (Boolean, Boolean, String?) -> Unit)

    fun restore(onResult: (Boolean) -> Unit)

    fun logIn(appUserId: String, onResult: (Boolean) -> Unit)

    fun logOut(onResult: () -> Unit)
}

/**
 * A product, flattened for the bridge.
 *
 * Deliberately not the domain [app.curio.billing.Product] — that lives in
 * commonMain and shouldn't grow an iOS-shaped constructor just to make Swift's
 * life easier. Mapped on the Kotlin side.
 */
data class BridgeProduct(
    val id: String,
    val price: String,
    val isAnnual: Boolean,
)

/**
 * Set once from Swift at launch, before any Compose content exists.
 *
 * Nullable because both are optional: without a [SecureStore] the app falls back
 * to in-memory storage (you log in again next launch), and without a
 * [BillingBridge] everyone is FREE and nothing is purchasable. Neither absence
 * crashes anything, which is what makes the iOS app runnable before RevenueCat
 * is wired up at all.
 */
object IosBridge {
    var secureStore: SecureStore? = null
    var billing: BillingBridge? = null
}
