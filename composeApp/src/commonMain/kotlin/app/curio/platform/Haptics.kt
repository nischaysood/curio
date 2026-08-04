package app.curio.platform

import androidx.compose.runtime.Composable

/**
 * Haptics are the ONLY platform-specific code in Curio. Everything else,
 * including the database and spaced repetition, lives in commonMain.
 *
 * These are semantic events, not waveforms. The composables say what happened;
 * each platform decides how that feels. Never call a platform vibrator directly
 * from a composable.
 */
enum class Haptic {
    /** Any option tap, before correctness is known. Lightest thing in the app. */
    TAP,

    /** A word tile dropping into a blank, a chip landing in a bucket. */
    SNAP,

    /** A matched pair locking and flying off. Slightly heavier than SNAP. */
    LOCK,

    /** Correct answer. Crisp, single, satisfying. */
    CORRECT,

    /** Wrong answer. Soft double tap — a nudge, not a buzzer. Must never feel punitive. */
    INCORRECT,

    /** Lesson complete. The biggest event in the app; use it sparingly. */
    COMPLETE,

    /** Streak milestone. */
    STREAK,
}

interface Haptics {
    fun play(haptic: Haptic)
}

@Composable
expect fun rememberHaptics(): Haptics
