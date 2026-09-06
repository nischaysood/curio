package app.curio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.pm.ApplicationInfo
import app.curio.billing.BillingKeys
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Application context, never `this` — see the note in TokenStore.android.kt.
        app.curio.auth.appContext = applicationContext
        configureBilling()
        setContent { App() }
    }

    /**
     * RevenueCat has to be configured before anything reads an entitlement.
     *
     * Anonymous by default — no login, no account, no email. RevenueCat mints an
     * anonymous ID and a purchase attaches to it, which is why "anonymous now,
     * account at the paywall later" works without anyone losing what they paid
     * for. Calling logIn() later merges the two.
     */
    private fun configureBilling() {
        if (Purchases.isConfigured) return

        // Read from the manifest flag rather than BuildConfig — this module
        // doesn't enable buildConfig generation, and turning it on just for a
        // log level isn't worth the extra build step.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Debug builds talk to RevenueCat's Test Store: purchases open a
        // simulated modal instead of Google's sheet, so every branch of the
        // purchase code is reachable on an emulator. Release builds get the real
        // Play key.
        //
        // Derived from the build, never chosen by hand. A release binary
        // carrying a test key would take payments that don't exist, and nothing
        // in the UI would look wrong.
        val key = if (debuggable) {
            BillingKeys.ANDROID_TEST_STORE_KEY.ifBlank { BillingKeys.ANDROID_API_KEY }
        } else {
            BillingKeys.ANDROID_API_KEY
        }

        // Blank key means billing is simply off — free tier, no paywall, no
        // crash. Same principle as a blank API_BASE_URL.
        if (key.isBlank()) return

        Purchases.logLevel = if (debuggable) LogLevel.DEBUG else LogLevel.ERROR
        Purchases.configure(PurchasesConfiguration.Builder(this, key).build())
    }
}
