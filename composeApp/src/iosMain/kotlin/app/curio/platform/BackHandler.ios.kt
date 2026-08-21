package app.curio.platform

import androidx.compose.runtime.Composable

/**
 * No-op. iOS has no system back gesture to intercept at this level — navigation
 * back is the app's own affordance, which every screen provides.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Intentionally empty.
}
