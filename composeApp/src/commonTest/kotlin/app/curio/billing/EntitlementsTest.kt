package app.curio.billing

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Paywall logic you can only verify by making a real purchase is paywall logic
 * that ships broken. All of it is pure so all of it is tested here — including
 * the two cases that bite in production: midnight rollover and a wrong clock.
 */
class EntitlementsTest {

    private val today = LocalDate(2026, 9, 4)
    private val yesterday = LocalDate(2026, 9, 3)

    private fun free(usage: Usage) = Entitlements(Tier.FREE, usage, today)
    private fun premium(usage: Usage) = Entitlements(Tier.PREMIUM, usage, today)
    private fun fresh(day: LocalDate = today) = Usage(day = day)

    // --- free tier -----------------------------------------------------------

    @Test
    fun aNewFreeUserCanStartALessonAndGenerateOnce() {
        val e = free(fresh())
        assertTrue(e.canStartLesson().isAllowed)
        assertTrue(e.canGenerate().isAllowed)
        assertEquals(3, e.lessonsRemainingToday())
    }

    @Test
    fun theThirdLessonIsAllowedAndTheFourthIsNot() {
        // Off-by-one here means either giving away a lesson or cutting someone
        // off early. The boundary is the whole feature.
        assertTrue(free(fresh().copy(lessonsCompleted = 2)).canStartLesson().isAllowed)
        val fourth = free(fresh().copy(lessonsCompleted = 3)).canStartLesson()
        assertFalse(fourth.isAllowed)
        assertEquals(Denial.DAILY_LESSONS, (fourth as Allowance.Denied).reason)
    }

    @Test
    fun aSecondGenerationInOneDayIsRefused() {
        val second = free(fresh().copy(generations = 1)).canGenerate()
        assertFalse(second.isAllowed)
        assertEquals(Denial.DAILY_GENERATIONS, (second as Allowance.Denied).reason)
    }

    @Test
    fun holdingACourseAlreadyBlocksGeneratingAnother() {
        // Distinct from the daily cap: this is the product rule, and the UI
        // needs to say "finish or delete your course", not "come back tomorrow".
        val denied = free(fresh().copy(activeCourses = 1)).canGenerate()
        assertFalse(denied.isAllowed)
        assertEquals(Denial.COURSE_LIMIT, (denied as Allowance.Denied).reason)
    }

    @Test
    fun theDailyCapIsReportedBeforeTheCourseCapWhenBothApply() {
        // Both are true, but "come back tomorrow" is the real blocker — telling
        // someone to delete a course wouldn't actually unblock them.
        val denied = free(fresh().copy(generations = 1, activeCourses = 1)).canGenerate()
        assertEquals(Denial.DAILY_GENERATIONS, (denied as Allowance.Denied).reason)
    }

    @Test
    fun remainingCountNeverGoesNegative() {
        assertEquals(0, free(fresh().copy(lessonsCompleted = 99)).lessonsRemainingToday())
    }

    // --- premium -------------------------------------------------------------

    @Test
    fun premiumIgnoresLessonAndCourseLimits() {
        val e = premium(fresh().copy(lessonsCompleted = 50, activeCourses = 20))
        assertTrue(e.canStartLesson().isAllowed)
        assertTrue(e.canGenerate().isAllowed)
    }

    @Test
    fun premiumStillHasAGenerationCeiling() {
        // Not a product limit — a cost one. Unlimited generation on a flat fee
        // is how you get a bill that ends the project.
        val e = premium(fresh().copy(generations = Limits.PREMIUM_GENERATIONS_PER_DAY))
        assertFalse(e.canGenerate().isAllowed)
    }

    @Test
    fun premiumIsNotShownARemainingCount() {
        // Showing a countdown to someone who paid to remove it is how you make
        // them regret paying.
        assertNull(premium(fresh()).lessonsRemainingToday())
    }

    // --- rollover ------------------------------------------------------------

    @Test
    fun yesterdaysUsageDoesNotCountAgainstToday() {
        val stale = Usage(day = yesterday, lessonsCompleted = 3, generations = 1)
        val e = free(stale)
        assertTrue(e.canStartLesson().isAllowed)
        assertEquals(3, e.lessonsRemainingToday())
    }

    @Test
    fun activeCoursesSurviveTheDailyReset() {
        // Lessons and generations are daily. Courses you own are not — waking up
        // to a course you didn't finish still counts against the free limit.
        val stale = Usage(day = yesterday, lessonsCompleted = 3, generations = 1, activeCourses = 1)
        val denied = free(stale).canGenerate()
        assertEquals(Denial.COURSE_LIMIT, (denied as Allowance.Denied).reason)
    }

    @Test
    fun aFutureDatedRecordResetsRatherThanLockingTheUserOut() {
        // Timezone travel or a wrong clock writes a record dated ahead of today.
        // Being strict here locks someone out until the date catches up, which
        // could be days. Being generous costs one extra course.
        val future = Usage(day = LocalDate(2026, 12, 25), lessonsCompleted = 3, generations = 1)
        assertTrue(free(future).canStartLesson().isAllowed)
    }

    // --- recording -----------------------------------------------------------

    @Test
    fun completingALessonIncrementsTodayNotTheStaleDay() {
        val stale = Usage(day = yesterday, lessonsCompleted = 3)
        val after = free(stale).afterLessonCompleted()

        assertEquals(today, after.day)
        assertEquals(1, after.lessonsCompleted, "should count from a fresh day, not 4")
    }

    @Test
    fun generatingCountsAgainstBothTheDailyCapAndTheCourseCap() {
        val after = free(fresh()).afterGeneration()
        assertEquals(1, after.generations)
        assertEquals(1, after.activeCourses)
        assertFalse(free(after).canGenerate().isAllowed)
    }

    @Test
    fun deletingACourseFreesTheSlotButNotTheDailyGeneration() {
        // Otherwise generate-delete-generate is an infinite free tier.
        val used = free(fresh()).afterGeneration()
        val afterDelete = Entitlements(Tier.FREE, used, today).afterCourseDeleted()

        assertEquals(0, afterDelete.activeCourses)
        assertEquals(1, afterDelete.generations)
        val denied = Entitlements(Tier.FREE, afterDelete, today).canGenerate()
        assertEquals(Denial.DAILY_GENERATIONS, (denied as Allowance.Denied).reason)
    }

    @Test
    fun deletingWithNoCoursesDoesNotGoNegative() {
        assertEquals(0, free(fresh()).afterCourseDeleted().activeCourses)
    }
}
