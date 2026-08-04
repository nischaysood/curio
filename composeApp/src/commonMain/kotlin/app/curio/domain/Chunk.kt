package app.curio.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model returns. NOT exercises.
 *
 * The model does knowledge; the router (code) does structure. Asking a model to
 * "generate exercises" produces inconsistent JSON, hallucinated formats, and output
 * you cannot fix. Asking for content tagged with a *pedagogical shape* gives you
 * something testable, debuggable and cheap.
 *
 * The polymorphic discriminator is "shape" — this is exactly the field Gemini's
 * schema-constrained output enum-locks, so an invalid shape cannot reach us.
 */
@Serializable
sealed interface Chunk {
    /** The single idea this chunk carries. Becomes Exercise.concept. */
    val concept: String

    @Serializable
    @SerialName("definition")
    data class Definition(
        override val concept: String,
        /** The definition itself, one sentence. */
        val content: String,
        /** Plausible-but-wrong alternatives. 2-3. */
        val distractors: List<String> = emptyList(),
    ) : Chunk

    @Serializable
    @SerialName("sequence")
    data class Sequence(
        override val concept: String,
        /** Steps in correct order, 3-6. */
        val steps: List<String>,
    ) : Chunk

    @Serializable
    @SerialName("taxonomy")
    data class Taxonomy(
        override val concept: String,
        /** category -> members. 2-3 categories. */
        val categories: Map<String, List<String>>,
    ) : Chunk

    @Serializable
    @SerialName("comparison")
    data class Comparison(
        override val concept: String,
        val leftLabel: String,
        val rightLabel: String,
        /** item text -> true if it belongs to LEFT. */
        val items: Map<String, Boolean>,
    ) : Chunk

    @Serializable
    @SerialName("fact")
    data class Fact(
        override val concept: String,
        val question: String,
        val answer: String,
        val distractors: List<String> = emptyList(),
    ) : Chunk

    @Serializable
    @SerialName("concept")
    data class Conceptual(
        override val concept: String,
        /** The explanation. Used to build the rubric, never shown before answering. */
        val content: String,
        val keyPoints: List<String> = emptyList(),
    ) : Chunk
}

/** One lesson's worth of tagged content, straight off the wire. */
@Serializable
data class LessonChunks(
    val lesson: String,
    val objective: String = "",
    val chunks: List<Chunk>,
)

/** Stage 1 output: the outline. Returns in ~3s and drives the assembly animation. */
@Serializable
data class Outline(
    val topic: String,
    val lessons: List<OutlineEntry>,
)

@Serializable
data class OutlineEntry(
    val title: String,
    val objective: String,
)
