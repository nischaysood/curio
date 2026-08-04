package app.curio.platform

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        AndroidHaptics(view, vibrator)
    }
}

private class AndroidHaptics(
    private val view: View,
    private val vibrator: Vibrator?,
) : Haptics {

    override fun play(haptic: Haptic) {
        // Prefer the platform constants: they respect the user's haptic settings
        // and are tuned per-device. Fall back to composed waveforms only for the
        // two events with no good constant.
        val constant = when (haptic) {
            Haptic.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.SNAP -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.LOCK -> HapticFeedbackConstants.CONTEXT_CLICK
            Haptic.CORRECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            Haptic.INCORRECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            Haptic.COMPLETE, Haptic.STREAK -> null
        }

        if (constant != null) {
            view.performHapticFeedback(constant)
            return
        }

        val effect = when (haptic) {
            // Two rising taps then a longer settle. Reads as "arrival".
            Haptic.COMPLETE -> composed(
                timings = longArrayOf(0, 30, 60, 30, 60, 70),
                amplitudes = intArrayOf(0, 90, 0, 140, 0, 200),
            )
            // Three quick ascending ticks, one per ring Cue draws.
            Haptic.STREAK -> composed(
                timings = longArrayOf(0, 25, 50, 25, 50, 35),
                amplitudes = intArrayOf(0, 120, 0, 160, 0, 200),
            )
            else -> null
        } ?: return

        vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect)
    }

    private fun composed(timings: LongArray, amplitudes: IntArray): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            null
        }
}
