package app.curio.data

import app.curio.domain.Depth
import app.curio.domain.Exercise
import app.curio.domain.LessonState
import app.curio.domain.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseCatalogTest {

    @Test
    fun everyBundledCourseParsesAndRoutes() {
        // If any bundled course throws, the app crashes on the topic that
        // triggers it — for a user we can't reproduce. Check them all up front.
        CourseCatalog.suggestions.forEach { topic ->
            val course = CourseCatalog.find(topic)
            assertNotNull(course, "'$topic' is offered as a suggestion but doesn't resolve")
            assertTrue(course.lessons.isNotEmpty(), "'$topic' has no lessons")
        }
    }

    @Test
    fun everyLessonInEveryCourseSatisfiesTheProductConstraint() {
        CourseCatalog.suggestions.forEach { topic ->
            CourseCatalog.find(topic)!!.lessons.forEach { lesson ->
                val types = lesson.exercises.map { it.type }.distinct()
                assertTrue(
                    types.size >= 3,
                    "${lesson.title} has only $types — that reads as a quiz generator",
                )
                assertEquals(1, lesson.exercises.count { it is Exercise.TeachBack }, lesson.title)
                assertTrue(lesson.exercises.last() is Exercise.TeachBack, lesson.title)
                assertTrue(lesson.exercises.size >= 4, "${lesson.title} is too short")
            }
        }
    }

    @Test
    fun everyLessonTeachesBeforeItTests() {
        // Testing someone on material they were never shown isn't a learning app,
        // it's a quiz. Every lesson must carry a summary and key ideas.
        CourseCatalog.suggestions.forEach { topic ->
            CourseCatalog.find(topic)!!.lessons.forEach { lesson ->
                assertTrue(lesson.summary.isNotBlank(), "${lesson.title} has no summary")
                assertTrue(lesson.keyIdeas.size >= 3, "${lesson.title} has ${lesson.keyIdeas.size} key ideas")
                assertTrue(lesson.objective.isNotBlank(), "${lesson.title} has no objective")
            }
        }
    }

    @Test
    fun theIntroStaysShortEnoughToRead() {
        // If the intro grows past a screen the lesson is too big and should split.
        // Curio outputs exercises, not articles.
        CourseCatalog.suggestions.forEach { topic ->
            CourseCatalog.find(topic)!!.lessons.forEach { lesson ->
                assertTrue(lesson.summary.length <= 320, "${lesson.title} summary is an essay")
                assertTrue(lesson.keyIdeas.size <= 5, "${lesson.title} has too many key ideas")
            }
        }
    }

    @Test
    fun everyCourseHasMoreThanOneLesson() {
        CourseCatalog.suggestions.forEach { topic ->
            val n = CourseCatalog.find(topic)!!.lessons.size
            assertTrue(n >= 2, "'$topic' has only $n lesson — that isn't a course")
        }
    }

    @Test
    fun onlyTheFirstLessonStartsUnlocked() {
        val course = CourseCatalog.find("How Compilers Work")!!

        assertEquals(LessonState.AVAILABLE, course.lessons.first().state)
        course.lessons.drop(1).forEach {
            assertEquals(LessonState.LOCKED, it.state, "${it.title} should start locked")
        }
    }

    @Test
    fun lookupIgnoresCasingSpacingAndPunctuation() {
        val canonical = CourseCatalog.find("How Compilers Work")!!.id

        assertEquals(canonical, CourseCatalog.find("how compilers work")!!.id)
        assertEquals(canonical, CourseCatalog.find("  HOW   COMPILERS WORK  ")!!.id)
        assertEquals(canonical, CourseCatalog.find("How compilers work?")!!.id)
    }

    @Test
    fun partialTopicsFindTheirCourse() {
        // Someone types "compilers", not the full bundled title.
        assertNotNull(CourseCatalog.find("compilers"))
        assertNotNull(CourseCatalog.find("big-o"))
    }

    @Test
    fun shortInputDoesNotMatchEverything() {
        // The substring fallback must not turn one letter into a wildcard,
        // or every topic silently serves the first course in the map.
        assertNull(CourseCatalog.find("c"))
        assertNull(CourseCatalog.find("ho"))
        assertNull(CourseCatalog.find(""))
        assertNull(CourseCatalog.find("   "))
    }

    @Test
    fun unknownTopicsReturnNullRatherThanSomethingWrong() {
        // Serving a compilers course to someone who asked about crochet is worse
        // than admitting we don't have it.
        assertNull(CourseCatalog.find("crochet"))
        assertNull(CourseCatalog.find("mughal architecture"))
    }

    @Test
    fun lessonIdsAreUniqueAcrossACourse() {
        CourseCatalog.suggestions.forEach { topic ->
            val ids = CourseCatalog.find(topic)!!.lessons.map { it.id }
            assertEquals(ids.size, ids.distinct().size, "$topic has duplicate lesson ids")
        }
    }

    @Test
    fun exerciseIdsAreUniqueAcrossACourse() {
        // Progress is keyed by exercise id. Collisions across lessons would make
        // completing one exercise appear to complete another.
        CourseCatalog.suggestions.forEach { topic ->
            val ids = CourseCatalog.find(topic)!!.lessons.flatMap { l -> l.exercises.map { it.id } }
            assertEquals(ids.size, ids.distinct().size, "$topic has duplicate exercise ids")
        }
    }

    @Test
    fun depthIsPartOfTheCourseIdentity() {
        val quick = CourseCatalog.find("How Compilers Work", Depth.QUICK)!!
        val deep = CourseCatalog.find("How Compilers Work", Depth.DEEP)!!

        assertTrue(quick.id != deep.id, "different depths must be different cache entries")
    }

    @Test
    fun defaultCourseAlwaysExists() {
        val course = CourseCatalog.default()

        assertTrue(course.lessons.isNotEmpty())
        assertEquals(LessonState.AVAILABLE, course.lessons.first().state)
    }
}
