package app.curio.data

import app.curio.domain.Exercise
import app.curio.domain.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Aug 3 deliverable, guarded.
 *
 * This is not a toy test — the sample course is written in the exact wire format
 * /generate will return, so if this passes, the JSON -> chunks -> router ->
 * exercises path works before a single line of server exists.
 */
class SampleCourseTest {

    @Test
    fun sampleCourseParsesRoutesAndIsPlayable() {
        val course = sampleCourse()
        val lesson = course.lessons.single()

        assertTrue(lesson.exercises.size >= 5, "got ${lesson.exercises.size} exercises")
        assertEquals("Lexical Analysis", lesson.title)
        assertTrue(lesson.objective.isNotBlank())
    }

    @Test
    fun sampleCourseSatisfiesTheLessonConstraint() {
        // ">=3 distinct exercise types, exactly one TeachBack, always last" is a
        // product constraint, not a nice-to-have. It is what separates Curio from
        // a quiz generator, so it is enforced on real content, not just fixtures.
        val lesson = sampleCourse().lessons.single()
        val types = lesson.exercises.map { it.type }.distinct()

        assertTrue(types.size >= 3, "expected >=3 distinct types, got $types")
        assertEquals(1, lesson.exercises.count { it is Exercise.TeachBack })
        assertTrue(lesson.exercises.last() is Exercise.TeachBack)
    }

    @Test
    fun threeDefinitionsBecameASingleMatchPairs() {
        val lesson = sampleCourse().lessons.single()
        val match = lesson.exercises.filterIsInstance<Exercise.MatchPairs>()

        assertEquals(1, match.size, "token/lexeme/lexer should aggregate into one MatchPairs")
        assertEquals(3, match.single().pairs.size)
    }

    @Test
    fun theSequenceBecameAReorderInTheCorrectOrder() {
        val lesson = sampleCourse().lessons.single()
        val reorder = lesson.exercises.filterIsInstance<Exercise.Reorder>().single()

        assertEquals("Read the next character", reorder.items.first())
        assertTrue(reorder.items.size in 3..6)
    }

    @Test
    fun everyExerciseIsRenderableWithoutCrashing() {
        // Exercise invariants are enforced in `init` blocks, so simply constructing
        // the course proves each one is renderable. This test documents that.
        val lesson = sampleCourse().lessons.single()

        lesson.exercises.forEach { exercise ->
            when (exercise) {
                is Exercise.MultipleChoice ->
                    assertTrue(exercise.correctIndex in exercise.options.indices)
                is Exercise.TapToFill ->
                    assertEquals(exercise.blanks.size + 1, exercise.segments.size)
                is Exercise.Reorder -> assertTrue(exercise.items.size >= 3)
                is Exercise.MatchPairs -> assertTrue(exercise.pairs.size >= 3)
                is Exercise.SortBuckets -> assertTrue(exercise.buckets.size >= 2)
                is Exercise.TeachBack -> assertTrue(exercise.rubric.isNotEmpty())
            }
        }
    }

    @Test
    fun routingIsStableAcrossCalls() {
        // A cached course must render identically today and in December.
        assertEquals(sampleCourse().lessons, sampleCourse().lessons)
    }
}
