package app.curio.billing

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable

/**
 * What the user is allowed to do today.
 *
 * Deliberately pure: no SDK, no network, no Compose. The billing SDK's only job
 * is to answer "is this person premium?" — every rule about what that unlocks
 * lives here, where it can be unit tested. Paywall logic that can only be
 * verified by making a real purchase is paywall logic that ships broken.
 */

@Serializable
enum class Tier { FREE, PREMIUM }

/**
 * Free tier, from the PRD:
 *   1 active course · 3 lessons/day · 1 generation/day
 *
 * The daily caps are the honest constraint — generation costs money and a free
 * user shouldn't be able to run up the bill. The single-course limit is the
 * product one: it's what makes "unlimited courses" worth paying for.
 */
object Limits {
    const val FREE_ACTIVE_COURSES = 1
    const val FREE_LESSONS_PER_DAY = 3
    const val FREE_GENERATIONS_PER_DAY = 1

    /** Premium isn't unlimited generation — it's a ceiling nobody legitimately hits. */
    const val PREMIUM_GENERATIONS_PER_DAY = 10
}

/**
 * Everything consumed today, plus the day it refers to.
 *
 * [day] is stored rather than inferred so the rollover is explicit and testable.
 * A counter that resets "when the date changes" without recording which date is
 * a counter that silently never resets on a device with a wrong clock.
 */
@Serializable
data class Usage(
    val day: LocalDate,
    val lessonsCompleted: Int = 0,
    val generations: Int = 0,
    val activeCourses: Int = 0,
) {
    /**
     * Usage for [today], rolling over if this record is from an earlier day.
     *
     * A future-dated record (clock changed, timezone travel) also resets rather
     * than locking the user out until the date catches up. Being generous here
     * costs one extra course; being strict costs a user.
     */
    fun on(today: LocalDate): Usage =
        if (day == today) this else Usage(day = today, activeCourses = activeCourses)
}

/** Why an action was refused. The UI turns these into copy; nothing else does. */
enum class Denial {
    DAILY_LESSONS,
    DAILY_GENERATIONS,
    COURSE_LIMIT,
}

sealed interface Allowance {
    data object Allowed : Allowance
    data class Denied(val reason: Denial) : Allowance

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * The rules. Every gate in the app asks this object, so there is exactly one
 * place where "can they?" is decided.
 */
class Entitlements(
    private val tier: Tier,
    private val usage: Usage,
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
    private val current = usage.on(today)

    val isPremium: Boolean get() = tier == Tier.PREMIUM

    fun canStartLesson(): Allowance = when {
        isPremium -> Allowance.Allowed
        current.lessonsCompleted < Limits.FREE_LESSONS_PER_DAY -> Allowance.Allowed
        else -> Allowance.Denied(Denial.DAILY_LESSONS)
    }

    fun canGenerate(): Allowance {
        val cap = if (isPremium) Limits.PREMIUM_GENERATIONS_PER_DAY else Limits.FREE_GENERATIONS_PER_DAY
        if (current.generations >= cap) return Allowance.Denied(Denial.DAILY_GENERATIONS)

        // Free users hold one course at a time. Checked here rather than at
        // generation time on the server, because it's a product rule, not a
        // cost one — and the server has no idea what's on this device.
        if (!isPremium && current.activeCourses >= Limits.FREE_ACTIVE_COURSES) {
            return Allowance.Denied(Denial.COURSE_LIMIT)
        }
        return Allowance.Allowed
    }

    /**
     * Lessons remaining today, for the "2 lessons left" hint.
     * Null for premium — showing a countdown to someone who paid to remove it
     * is the kind of detail that makes people regret paying.
     */
    fun lessonsRemainingToday(): Int? =
        if (isPremium) null
        else (Limits.FREE_LESSONS_PER_DAY - current.lessonsCompleted).coerceAtLeast(0)

    fun afterLessonCompleted(): Usage =
        current.copy(lessonsCompleted = current.lessonsCompleted + 1)

    fun afterGeneration(): Usage =
        current.copy(generations = current.generations + 1, activeCourses = current.activeCourses + 1)

    fun afterCourseDeleted(): Usage =
        current.copy(activeCourses = (current.activeCourses - 1).coerceAtLeast(0))
}
