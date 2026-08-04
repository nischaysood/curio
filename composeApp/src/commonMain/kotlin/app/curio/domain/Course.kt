package app.curio.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Depth {
    QUICK,      // 5 lessons,  4-5 exercises each
    STANDARD,   // 10 lessons, 5-8 exercises each
    DEEP;       // 15 lessons, 6-8 exercises each  (Premium only)

    val lessonCount: Int
        get() = when (this) {
            QUICK -> 5
            STANDARD -> 10
            DEEP -> 15
        }

    val exercisesPerLesson: IntRange
        get() = when (this) {
            QUICK -> 4..5
            STANDARD -> 5..8
            DEEP -> 6..8
        }
}

@Serializable
enum class LessonState {
    LOCKED,
    AVAILABLE,
    COMPLETE,
    NEEDS_REVIEW,
}

@Serializable
data class Course(
    val id: String,
    val topic: String,
    /** sha256(normalise(topic) + ":" + depth). The cache key. */
    val topicHash: String,
    val depth: Depth,
    val lessons: List<Lesson>,
    val createdAtEpochMs: Long,
) {
    val progress: Float
        get() = if (lessons.isEmpty()) 0f
        else lessons.count { it.state == LessonState.COMPLETE }.toFloat() / lessons.size
}

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    /** One sentence: what the learner can do after this lesson. */
    val objective: String,
    val position: Int,
    val exercises: List<Exercise>,
    val state: LessonState = LessonState.LOCKED,
) {
    /** Rough minutes. Used only for the "10-15 min" label on the path node. */
    val estimatedMinutes: Int get() = (exercises.size * 1.8f).toInt().coerceAtLeast(5)
}

/**
 * Normalise before hashing so "How Compilers Work", "how compilers work" and
 * "  How  Compilers  Work  " all hit the same cache row.
 *
 * `+` and `#` survive on purpose. Stripping them collapses "c++" into "c",
 * "c#" into "c" and "f#" into "f" — which does not just miss the cache, it
 * serves the WRONG course. Over-aggressive normalisation is worse than none.
 */
fun normaliseTopic(raw: String): String =
    raw.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9+# ]"), " ")
        .replace(Regex(" +"), " ")
        .trim()

/**
 * The cache key. A course on Big-O is identical for every user: the first one
 * pays for generation, the next thousand are free. This function is the thing
 * that makes marginal cost trend toward zero, so it must be identical on the
 * client and the server — port it verbatim, do not reimplement it.
 *
 * FNV-1a rather than String.hashCode(), which is only stability-guaranteed on
 * the JVM, and rather than sha256, which needs a dependency in commonMain for no
 * benefit — this is a cache key, not a security boundary.
 */
fun topicHash(topic: String, depth: Depth): String {
    val input = normaliseTopic(topic) + ":" + depth.name.lowercase()
    var h = -0x340d631b7bdddcdbL   // FNV-1a 64-bit offset basis
    for (b in input.encodeToByteArray()) {
        h = h xor (b.toLong() and 0xFF)
        h *= 0x100000001b3L
    }
    return h.toULong().toString(16).padStart(16, '0')
}
