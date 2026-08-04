package app.curio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
actual fun rememberHaptics(): Haptics = remember { IosHaptics() }

private class IosHaptics : Haptics {

    // Generators are kept warm rather than created per event — a cold generator
    // costs ~100ms on first fire, which is exactly the latency you notice.
    private val selection = UISelectionFeedbackGenerator().apply { prepare() }
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply { prepare() }
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply { prepare() }
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).apply { prepare() }
    private val notification = UINotificationFeedbackGenerator().apply { prepare() }

    override fun play(haptic: Haptic) {
        when (haptic) {
            Haptic.TAP -> selection.selectionChanged()
            Haptic.SNAP -> light.impactOccurred()
            Haptic.LOCK -> medium.impactOccurred()
            Haptic.CORRECT ->
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            // Deliberately NOT ...TypeError — that pattern reads as an alarm.
            // A wrong answer is information, not a failure.
            Haptic.INCORRECT ->
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
            Haptic.COMPLETE -> heavy.impactOccurred()
            Haptic.STREAK ->
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
        // Re-arm for the next event.
        when (haptic) {
            Haptic.TAP -> selection.prepare()
            Haptic.SNAP -> light.prepare()
            Haptic.LOCK -> medium.prepare()
            Haptic.COMPLETE -> heavy.prepare()
            else -> notification.prepare()
        }
    }
}
