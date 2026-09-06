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
    const val ANDROID_API_KEY: String = "goog_dKSAjRCZQmCgVBCIbntwzzDXspe"
    const val IOS_API_KEY: String = ""

    /**
     * RevenueCat Test Store — debug builds only.
     *
     * Swapping this key in place of the platform key replaces Google's purchase
     * sheet with a modal offering success / failure / cancel, so every branch of
     * the purchase code can be exercised on an emulator with no Play track, no
     * licence tester, and no signed bundle.
     *
     * Selected by build type in MainActivity, never by hand. RevenueCat's
     * documentation is unusually blunt about this — shipping a release build
     * configured with a test key means real customers cannot pay you, and the
     * failure is silent. Choosing it from the debuggable flag makes that
     * mistake impossible rather than merely unlikely.
     *
     * Test subscriptions renew fast on purpose: a monthly renews every 5 minutes
     * and expires after 5 renewals, so expiry handling is testable in 25 minutes
     * instead of five months.
     */
    const val ANDROID_TEST_STORE_KEY: String = "test_HIknDLHkoxxonZOFBDYsCegbxUf"
}
