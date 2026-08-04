package app.curio.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The cache key is the whole business model. If normalisation is loose, every
 * user pays for generation and the unit economics invert.
 */
class TopicHashTest {

    @Test
    fun casingSpacingAndPunctuationAllCollapseToTheSameKey() {
        val canonical = topicHash("how compilers work", Depth.STANDARD)

        assertEquals(canonical, topicHash("How Compilers Work", Depth.STANDARD))
        assertEquals(canonical, topicHash("  HOW   COMPILERS  WORK  ", Depth.STANDARD))
        assertEquals(canonical, topicHash("How compilers work!", Depth.STANDARD))
        assertEquals(canonical, topicHash("How Compilers Work?", Depth.STANDARD))
    }

    @Test
    fun depthIsPartOfTheKey() {
        // A Quick course and a Deep course on the same topic are different products.
        assertNotEquals(
            topicHash("how compilers work", Depth.QUICK),
            topicHash("how compilers work", Depth.DEEP),
        )
    }

    @Test
    fun differentTopicsDoNotCollide() {
        assertNotEquals(
            topicHash("how compilers work", Depth.STANDARD),
            topicHash("how interpreters work", Depth.STANDARD),
        )
    }

    @Test
    fun hashIsFixedWidthHexAndStableAcrossRuns() {
        val h = topicHash("big o notation", Depth.STANDARD)

        assertEquals(16, h.length, "fixed-width keys keep the Postgres index tidy")
        assertEquals(h, topicHash("big o notation", Depth.STANDARD))
        assertEquals(true, h.all { it in "0123456789abcdef" })
    }

    @Test
    fun normalisationDoesNotDestroyDistinctTopics() {
        // Over-aggressive normalisation is worse than none: it serves the wrong course.
        assertNotEquals(
            topicHash("c programming", Depth.STANDARD),
            topicHash("c++ programming", Depth.STANDARD),
        )
    }
}
