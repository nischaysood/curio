package app.curio.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The six exercise types. One composable per type.
 *
 * Adding type seven later = one composable + one `when` branch.
 * Do not add a seventh before v1.1 ships.
 */
@Serializable
sealed interface Exercise {
    val id: String

    /** The instruction shown above the interaction. Never the answer. */
    val prompt: String

    /** The concept this exercise is testing. Used by spaced repetition. */
    val concept: String

    @Serializable
    @SerialName("multiple_choice")
    data class MultipleChoice(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String? = null,
    ) : Exercise {
        init {
            require(options.size in 2..4) { "MultipleChoice needs 2-4 options, got ${options.size}" }
            require(correctIndex in options.indices) { "correctIndex $correctIndex out of bounds" }
        }
    }

    /**
     * A sentence broken at the blanks. [segments] has exactly [blanks].size + 1 entries;
     * blank *i* sits between segment *i* and segment *i+1*.
     *
     * "A [token] is the smallest unit a [lexer] emits."
     *   segments = ["A ", " is the smallest unit a ", " emits."]
     *   blanks   = ["token", "lexer"]
     */
    @Serializable
    @SerialName("tap_to_fill")
    data class TapToFill(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        val segments: List<String>,
        val blanks: List<String>,
        val distractors: List<String> = emptyList(),
    ) : Exercise {
        init {
            require(segments.size == blanks.size + 1) {
                "TapToFill needs ${blanks.size + 1} segments for ${blanks.size} blanks, got ${segments.size}"
            }
            require(blanks.isNotEmpty()) { "TapToFill needs at least one blank" }
        }

        /** Every tile shown below the sentence, in stable order. Shuffle at render with a seeded rng. */
        val tiles: List<String> get() = blanks + distractors
    }

    /** [items] are stored in the CORRECT order. Shuffle at render. */
    @Serializable
    @SerialName("reorder")
    data class Reorder(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        val items: List<String>,
    ) : Exercise {
        init {
            require(items.size in 3..6) { "Reorder needs 3-6 items, got ${items.size}" }
        }
    }

    @Serializable
    @SerialName("match_pairs")
    data class MatchPairs(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        val pairs: List<MatchPair>,
    ) : Exercise {
        init {
            require(pairs.size in 3..5) { "MatchPairs needs 3-5 pairs, got ${pairs.size}" }
        }
    }

    @Serializable
    @SerialName("sort_buckets")
    data class SortBuckets(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        val buckets: List<String>,
        val items: List<BucketItem>,
    ) : Exercise {
        init {
            require(buckets.size in 2..3) { "SortBuckets needs 2-3 buckets, got ${buckets.size}" }
            require(items.size in 4..8) { "SortBuckets needs 4-8 items, got ${items.size}" }
            val unknown = items.map { it.bucket }.toSet() - buckets.toSet()
            require(unknown.isEmpty()) { "Items reference unknown buckets: $unknown" }
        }
    }

    /**
     * The differentiator. Retrieval-and-explain.
     * The only exercise requiring a live model call at answer time.
     */
    @Serializable
    @SerialName("teach_back")
    data class TeachBack(
        override val id: String,
        override val prompt: String,
        override val concept: String,
        /** Points a good answer should hit. Sent to the grader, never shown before answering. */
        val rubric: List<String>,
        val minChars: Int = 40,
    ) : Exercise
}

@Serializable
data class MatchPair(
    val left: String,
    val right: String,
)

@Serializable
data class BucketItem(
    val text: String,
    val bucket: String,
)

/** Stable discriminator used by the router's repeat-weighting. */
enum class ExerciseType {
    MULTIPLE_CHOICE,
    TAP_TO_FILL,
    REORDER,
    MATCH_PAIRS,
    SORT_BUCKETS,
    TEACH_BACK,
}

val Exercise.type: ExerciseType
    get() = when (this) {
        is Exercise.MultipleChoice -> ExerciseType.MULTIPLE_CHOICE
        is Exercise.TapToFill -> ExerciseType.TAP_TO_FILL
        is Exercise.Reorder -> ExerciseType.REORDER
        is Exercise.MatchPairs -> ExerciseType.MATCH_PAIRS
        is Exercise.SortBuckets -> ExerciseType.SORT_BUCKETS
        is Exercise.TeachBack -> ExerciseType.TEACH_BACK
    }
