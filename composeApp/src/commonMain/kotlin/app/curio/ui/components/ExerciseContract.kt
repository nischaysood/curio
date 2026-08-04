package app.curio.ui.components

/**
 * Every exercise composable answers the same question: did they get it right?
 *
 * There are NO submit buttons anywhere in Curio. An exercise decides for itself
 * when the user has committed to an answer (tapping an option, filling the last
 * blank, locking the last pair) and calls [onResult] exactly once.
 */
fun interface OnExerciseResult {
    operator fun invoke(correct: Boolean)
}

enum class AnswerState {
    /** Nothing committed yet. Interactive. */
    OPEN,

    /** Committed and right. Locked, showing success colour. */
    CORRECT,

    /** Committed and wrong. Locked, showing the correct answer. */
    INCORRECT,
    ;

    val isSettled: Boolean get() = this != OPEN
}
