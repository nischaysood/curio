package app.curio.billing

/**
 * RevenueCat public SDK keys.
 *
 * These are PUBLIC keys and belong in the app — that's what they're for. They
 * identify the app to RevenueCat and can't be used to grant entitlements or read
 * anyone's data. The secret key (v1 API, dashboard-only) must never appear here.
 *
 * Blank means billing is off: the app runs entirely on the free tier, no paywall,
 * no crash. That's deliberate, so a missing key degrades rather than breaks —
 * same principle as a blank API_BASE_URL leaving generation off.
 *
 * Get yours from app.revenuecat.com -> Project -> API keys -> Public app key.
 */
object BillingKeys {
    const val ANDROID_API_KEY: String = ""
    const val IOS_API_KEY: String = ""
}
