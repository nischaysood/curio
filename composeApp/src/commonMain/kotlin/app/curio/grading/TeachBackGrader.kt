package app.curio.grading

import app.curio.domain.Exercise

/**
 * Grades a teach-back answer WITHOUT a model call.
 *
 * Teach-back is the differentiator, and it is the only exercise that needs a live
 * call at answer time. That call is also the only thing standing between the app
 * and working fully offline — so v0 grades on-device and the Groq call becomes an
 * upgrade rather than a dependency.
 *
 * The heuristic: how much of the rubric did they recall, in their own words?
 * That is a weaker signal than a model, but it is not a fake one — retrieval
 * coverage is most of what makes teach-back work pedagogically. What it cannot
 * judge is whether the explanation is *correct*, only whether it is *complete*.
 * Hence [Grade.confidence], which the UI uses to soften its language.
 *
 * Pure and dependency-free so it is unit-testable and can be ported to the server
 * verbatim as the fallback when Groq is rate-limited.
 */
object TeachBackGrader {

    /**
     * Recalling ONE rubric point in your own words is a pass.
     *
     * Tuned against real answers, not picked. A learner who writes "it turns
     * characters into tokens" has done the retrieval that makes teach-back work,
     * even though they covered a quarter of the rubric. Demanding breadth here
     * punishes concision, and this grader cannot tell the two apart.
     */
    private const val MIN_POINTS_HIT = 1

    /** Above this share of rubric points, we can say "yes" rather than "close". */
    private const val STRONG_THRESHOLD = 0.6f

    /**
     * Words too common to count as recall. Deliberately short: a domain-specific
     * stopword list would need tuning per topic, and Curio's topics are arbitrary.
     */
    private val STOPWORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "and", "or", "but", "if", "then", "than", "that", "this", "these", "those",
        "of", "to", "in", "into", "on", "at", "by", "for", "with", "from", "as",
        "it", "its", "you", "your", "we", "our", "they", "their", "i", "my",
        "can", "will", "would", "should", "could", "may", "might", "do", "does",
        "not", "no", "so", "what", "which", "when", "where", "how", "why",
        "one", "some", "any", "all", "each", "more", "most", "other", "there",
    )

    fun grade(exercise: Exercise.TeachBack, answer: String): Grade {
        val trimmed = answer.trim()

        if (trimmed.length < exercise.minChars) {
            return Grade(
                correct = false,
                coverage = 0f,
                confidence = Confidence.HIGH,
                feedback = "Say a little more — one or two full sentences.",
                missed = emptyList(),
            )
        }

        val answerWords = tokenise(trimmed)
        if (answerWords.isEmpty()) {
            return Grade(false, 0f, Confidence.HIGH, "Try explaining it in your own words.", emptyList())
        }

        // No rubric to check against. Reward the attempt rather than punishing the
        // learner for our content pipeline's gap — this is our bug, not theirs.
        if (exercise.rubric.isEmpty()) {
            return Grade(true, 1f, Confidence.LOW, "Thanks — noted.", emptyList())
        }

        // A rubric point counts as hit on TWO matching keywords, or half of them.
        // Not exact-phrase matching: the entire point is that they explain it in
        // their OWN words. The two-word floor is what lets a short, correct answer
        // pass a long-winded rubric point.
        val hits = mutableListOf<String>()
        val misses = mutableListOf<String>()

        for (point in exercise.rubric) {
            val keyWords = tokenise(point)
            if (keyWords.isEmpty()) continue
            val matched = keyWords.count { key -> answerWords.any { it.matchesLoosely(key) } }
            val hit = matched >= 2 || matched.toFloat() / keyWords.size >= 0.5f
            if (hit) hits += point else misses += point
        }

        val coverage = hits.size.toFloat() / exercise.rubric.size
        val correct = hits.size >= MIN_POINTS_HIT

        return Grade(
            correct = correct,
            coverage = coverage,
            confidence = if (coverage >= STRONG_THRESHOLD) Confidence.HIGH else if (correct) Confidence.LOW else Confidence.HIGH,
            feedback = when {
                coverage >= STRONG_THRESHOLD -> "That's it — you've got the shape of it."
                correct -> "Good start. Worth remembering too:"
                else -> "Not quite. The key idea:"
            },
            missed = misses,
        )
    }

    private fun tokenise(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9+#]+"))
            .filter { it.length > 2 && it !in STOPWORDS }

    /**
     * Tolerates the endings people actually type: token/tokens, compile/compiles/
     * compiling. Cheap stemming — a real stemmer is not worth the dependency when
     * the model call replaces this in a week.
     */
    private fun String.matchesLoosely(other: String): Boolean {
        if (this == other) return true
        val a = trimEnding()
        val b = other.trimEnding()
        return a == b || (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a)))
    }

    private fun String.trimEnding(): String = when {
        endsWith("ies") && length > 4 -> dropLast(3) + "y"
        endsWith("ing") && length > 5 -> dropLast(3)
        endsWith("es") && length > 4 -> dropLast(2)
        endsWith("s") && length > 3 && !endsWith("ss") -> dropLast(1)
        endsWith("ed") && length > 4 -> dropLast(2)
        else -> this
    }
}

data class Grade(
    val correct: Boolean,
    /** 0f..1f — how much of the rubric the answer recalled. */
    val coverage: Float,
    val confidence: Confidence,
    val feedback: String,
    /** Rubric points the answer missed. Shown after answering, never before. */
    val missed: List<String>,
)

/**
 * How much the UI should trust this grade.
 *
 * [LOW] means "we think this passed but we're not sure" — the UI hedges its
 * wording rather than asserting. Pretending to certainty we don't have is how a
 * learning app loses trust.
 */
enum class Confidence { LOW, HIGH }
