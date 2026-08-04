package app.curio.ui.cue

/**
 * Cue is a small glowing orb. Fully procedural — Compose Canvas, zero image assets.
 *
 * Not a compromise for lack of an illustrator. Character art is the single most
 * common way a solo dev ships something that reads as cheap: a rigged animal needs
 * hundreds of consistent poses and if any one is off, the whole app looks amateur.
 * An orb is expressive through shape, motion and colour — squash, stretch, pulse,
 * wobble, orbit, scatter — and it is infinitely consistent, because it is maths.
 *
 * Rules:
 *  - Cue never speaks in text bubbles. It reacts. All copy lives in the UI.
 *  - Cue never guilt-trips. No sad face for a missed streak.
 *  - Every state pairs with a haptic. That pairing is the Design Award submission.
 */
sealed interface CueState {

    /** Slow breathing pulse, gentle drift. The resting state. */
    data object Idle : CueState

    /** Generation. Fragments into particles, swirls, reassembles. */
    data object Thinking : CueState

    /** Quick squash-stretch bounce, warm colour flash, particle burst. */
    data object Correct : CueState

    /** Small wobble, cool colour shift. Never sad, never scolding. */
    data object Incorrect : CueState

    /** One orbit ring per milestone. */
    data class Streak(val milestones: Int) : CueState

    /** Leans toward the text field, pulses in rhythm with typing. */
    data class Listening(val intensity: Float = 0f) : CueState

    /** Expands, bursts, settles bigger. */
    data object LessonComplete : CueState

    /** True for states that play once and fall back to [Idle]. */
    val isTransient: Boolean
        get() = this is Correct || this is Incorrect || this is LessonComplete
}
