import SwiftUI
import RevenueCat
import ComposeApp

@main
struct CurioApp: App {

    init() {
        configureBilling()
        registerBridges()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose draws its own safe-area padding via
                // Modifier.windowInsetsPadding(WindowInsets.safeDrawing) at the
                // root of App.kt, so SwiftUI must hand it the full screen.
                // Without this the insets are applied twice and everything sits
                // in a box.
                .ignoresSafeArea(.all)
                // The keyboard is the exception: Compose's IME handling needs to
                // see the real keyboard height rather than have SwiftUI resize
                // the hosting view underneath it.
                .ignoresSafeArea(.keyboard)
        }
    }

    /// RevenueCat must be configured before anything reads an entitlement.
    ///
    /// Anonymous by default — no login, no account, no email. RevenueCat mints
    /// an anonymous ID and a purchase attaches to it, which is why "browse now,
    /// account later" works without anyone losing what they paid for. Calling
    /// `logIn` after sign-up merges the two.
    private func configureBilling() {
        let key = BillingKeys.shared.IOS_API_KEY
        // Blank key means billing is simply off: free tier, no paywall, no
        // crash. Same principle as a blank API_BASE_URL leaving generation off.
        guard !key.isEmpty else { return }

        #if DEBUG
        Purchases.logLevel = .debug
        #else
        Purchases.logLevel = .error
        #endif

        Purchases.configure(withAPIKey: key)
    }

    /// Hand Kotlin its platform implementations before any Compose content runs.
    private func registerBridges() {
        IosBridge.shared.secureStore = KeychainStore()
        if Purchases.isConfigured {
            IosBridge.shared.billing = RevenueCatBilling()
        }
    }
}

/// Hosts the shared Compose UI.
///
/// `MainViewController()` is defined in Kotlin (MainViewController.kt) and
/// returns `ComposeUIViewController { App() }` — so every screen, the format
/// router, the theme and all networking come from commonMain unchanged. This
/// file and the two beside it are the entire iOS-specific surface.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
