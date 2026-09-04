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
        if (BillingKeys.ANDROID_API_KEY.isBlank()) return
        if (Purchases.isConfigured) return

        // Read from the manifest flag rather than BuildConfig — this module
        // doesn't enable buildConfig generation, and turning it on just for a
        // log level isn't worth the extra build step.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Purchases.logLevel = if (debuggable) LogLevel.DEBUG else LogLevel.ERROR
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BillingKeys.ANDROID_API_KEY).build(),
        )
    }
}
