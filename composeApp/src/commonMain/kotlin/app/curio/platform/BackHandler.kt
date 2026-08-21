package app.curio.platform

import androidx.compose.runtime.Composable

/**
 * Intercept the platform's back gesture.
 *
 * Android has a hardware/gesture back that users expect to work everywhere —
 * ignoring it is the fastest way to get one-star reviews. iOS has no global
 * back, so the actual there is a no-op and screens rely on their own controls.
 *
 * Either way, every screen ALSO needs a visible back affordance. A gesture that
 * only some platforms have cannot be the only way out of a screen.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
