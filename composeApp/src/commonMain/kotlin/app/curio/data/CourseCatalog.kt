package app.curio.data

import app.curio.domain.Course
import app.curio.domain.Depth
import app.curio.domain.LessonChunks
import app.curio.domain.LessonState
import app.curio.domain.normaliseTopic
import app.curio.domain.topicHash
import app.curio.router.FormatRouter

/**
 * Every course the app can serve without a network call.
 *
 * This is the resilience layer, not a placeholder. Even once /generate exists,
 * these stay bundled: they make the app work offline, they make the demo
 * immune to a rate limit at the worst possible moment, and they give a
 * cold-start user something instant while their real course generates.
 *
 * Adding a course is one entry in [BUNDLED]. Nothing else changes.
 */
object CourseCatalog {

    /** topic -> lessons, written in the exact wire format /generate returns. */
    private val BUNDLED: Map<String, List<String>> = mapOf(
        "How Compilers Work" to listOf(LEXICAL_ANALYSIS, PARSING, CODE_GENERATION),
        "Big-O Notation" to listOf(BIG_O_BASICS, BIG_O_IN_PRACTICE),
        "How HTTPS Works" to listOf(HTTPS_HANDSHAKE, HTTPS_CERTIFICATES),
    )

    /** Topics offered as chips on the Generate screen. Ordered, not alphabetical. */
    val suggestions: List<String> get() = BUNDLED.keys.toList()

    /**
     * Look a topic up the same way the server cache will: normalise, then match.
     * Returns null when we have nothing — the caller must say so honestly rather
     * than quietly serving a course about something else.
     */
    fun find(topic: String, depth: Depth = Depth.STANDARD): Course? {
        val wanted = normaliseTopic(topic)
        if (wanted.isEmpty()) return null

        val key = BUNDLED.keys.firstOrNull { normaliseTopic(it) == wanted }
            // Forgive near-misses: "compilers" should find "How Compilers Work".
            // Deliberately one-directional — a typed topic may be a subset of a
            // bundled title, but never the reverse, or "c" would match everything.
            ?: BUNDLED.keys.firstOrNull { bundled ->
                val n = normaliseTopic(bundled)
                wanted.length >= 4 && n.contains(wanted)
            }
            ?: return null

        return build(key, BUNDLED.getValue(key), depth)
    }

    fun default(): Course = build("How Compilers Work", BUNDLED.getValue("How Compilers Work"), Depth.STANDARD)

    private fun build(topic: String, lessonJson: List<String>, depth: Depth): Course {
        val lessons = lessonJson.mapIndexed { i, json ->
            val chunks = CurioJson.chunks.decodeFromString<LessonChunks>(json)
            FormatRouter.route(
                source = chunks,
                lessonId = topicHash(topic, depth) + "-l$i",
                position = i,
                // Only the first lesson opens. The rest unlock on completion —
                // that sequencing is the product, not a limitation.
                state = if (i == 0) LessonState.AVAILABLE else LessonState.LOCKED,
            ).lesson
        }

        return Course(
            id = topicHash(topic, depth),
            topic = topic,
            topicHash = topicHash(topic, depth),
            depth = depth,
            lessons = lessons,
            createdAtEpochMs = 0L,
        )
    }
}
