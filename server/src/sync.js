import { db } from './db.js';

/**
 * Everything a signed-in learner's device needs to stay in sync.
 *
 * Two rules run through all of it:
 *
 *   1. Limits are counted here, not on the device. A client-side counter is a
 *      suggestion — clear app data and it resets. Generation costs real money.
 *   2. The server never trusts a client-supplied date. Otherwise changing the
 *      device clock buys an unlimited quota.
 */

// Mirrors Entitlements.kt. Duplicated deliberately: the client copy drives the
// UI (grey out the button before the tap), the server copy is the one that
// decides. If they drift, the server wins and the client shows a stale hint —
// which is the right way round to be wrong.
const LIMITS = {
  FREE: { courses: 1, lessonsPerDay: 3, generationsPerDay: 1 },
  PREMIUM: { courses: Infinity, lessonsPerDay: Infinity, generationsPerDay: 10 },
};

// ---------------------------------------------------------------------------
// Courses
// ---------------------------------------------------------------------------

/**
 * Every course this user has started, newest first, with progress folded in.
 *
 * One query, not N+1: the lesson states arrive as a JSON object keyed by lesson
 * id, so the app can rebuild a Course without a request per lesson.
 */
export async function listCourses(env, userId) {
  const rows = await db(env)`
    SELECT c.topic_hash, c.topic, c.depth, c.payload, uc.last_seen_at,
           COALESCE(
             (SELECT jsonb_object_agg(lp.lesson_id, lp.state)
                FROM lesson_progress lp
               WHERE lp.user_id = uc.user_id AND lp.topic_hash = c.topic_hash),
             '{}'::jsonb
           ) AS progress
      FROM user_courses uc
      JOIN courses c ON c.topic_hash = uc.topic_hash
     WHERE uc.user_id = ${userId}
     ORDER BY uc.last_seen_at DESC
     LIMIT 50
  `;

  return rows.map((r) => ({
    topicHash: r.topic_hash,
    topic: r.topic,
    depth: r.depth,
    lessons: r.payload.lessons ?? [],
    progress: r.progress ?? {},
    lastSeenAt: r.last_seen_at,
  }));
}

/**
 * Attach a generated course to a user.
 *
 * The course row is shared — one row per topic_hash for everyone — so this
 * upserts the course, then upserts the membership. Re-opening a course you
 * already have just bumps last_seen_at, which is what orders the home screen.
 */
export async function saveCourse(env, userId, course) {
  const sql = db(env);

  await sql`
    INSERT INTO courses (topic_hash, topic, depth, payload)
    VALUES (${course.topicHash}, ${course.topic}, ${course.depth}, ${JSON.stringify(course)})
    ON CONFLICT (topic_hash) DO NOTHING
  `;

  await sql`
    INSERT INTO user_courses (user_id, topic_hash)
    VALUES (${userId}, ${course.topicHash})
    ON CONFLICT (user_id, topic_hash) DO UPDATE SET last_seen_at = now()
  `;
}

/**
 * Everything the profile screen needs, in one round trip.
 *
 * Three separate endpoints would be tidier REST and three times the latency on
 * a screen the user opens to check one number. Assembled here instead.
 */
export async function profile(env, userId) {
  const sql = db(env);

  const rows = await sql`
    SELECT u.email,
           u.created_at,
           (SELECT count(*)::int FROM user_courses uc WHERE uc.user_id = u.id) AS courses,
           (SELECT count(*)::int FROM lesson_progress lp
             WHERE lp.user_id = u.id AND lp.state <> 'AVAILABLE') AS lessons_done
      FROM users u
     WHERE u.id = ${userId}
  `;

  const user = rows[0];
  if (!user) return null;

  return {
    email: user.email,
    memberSince: user.created_at,
    courses: user.courses,
    lessonsCompleted: user.lessons_done,
    usage: await usage(env, userId),
    limits: LIMITS.FREE,
  };
}

/** Free tier allows one course. Counted server-side so a reinstall doesn't reset it. */
export async function courseCount(env, userId) {
  const rows = await db(env)`SELECT count(*)::int AS n FROM user_courses WHERE user_id = ${userId}`;
  return rows[0]?.n ?? 0;
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

/**
 * Record a finished lesson and count it against today.
 *
 * A lesson counts as done however it went. Getting things wrong is how learning
 * works; score decides whether it's flagged for review, never whether you may
 * continue. Same rule as `Course.completing` on the client.
 */
export async function recordProgress(env, userId, { topicHash, lessonId, correct, total }) {
  const state = total > 0 && correct >= total * 0.6 ? 'COMPLETE' : 'NEEDS_REVIEW';
  const sql = db(env);

  await sql`
    INSERT INTO lesson_progress (user_id, topic_hash, lesson_id, state, correct, total)
    VALUES (${userId}, ${topicHash}, ${lessonId}, ${state}, ${correct}, ${total})
    ON CONFLICT (user_id, topic_hash, lesson_id) DO UPDATE
      SET state = EXCLUDED.state,
          -- Keep the best attempt. Re-reading a lesson you already passed
          -- shouldn't be able to demote it to NEEDS_REVIEW.
          correct = GREATEST(lesson_progress.correct, EXCLUDED.correct),
          total = EXCLUDED.total,
          completed_at = now()
  `;

  await sql`
    INSERT INTO daily_usage (user_id, day, lessons)
    VALUES (${userId}, CURRENT_DATE, 1)
    ON CONFLICT (user_id, day) DO UPDATE SET lessons = daily_usage.lessons + 1
  `;

  return { state };
}

// ---------------------------------------------------------------------------
// Usage and limits
// ---------------------------------------------------------------------------

/** Today's counters. Absent row means a fresh day, which is zeroes, not an error. */
export async function usage(env, userId) {
  const rows = await db(env)`
    SELECT lessons, generations FROM daily_usage
     WHERE user_id = ${userId} AND day = CURRENT_DATE
  `;
  return { lessons: rows[0]?.lessons ?? 0, generations: rows[0]?.generations ?? 0 };
}

/**
 * May this user generate another course right now?
 *
 * Returns a reason rather than a bare boolean so the app can say which wall was
 * hit — "you've used today's course" and "free accounts keep one course" need
 * different copy, and a generic "limit reached" is the kind of dead end that
 * makes people uninstall instead of subscribe.
 */
export async function canGenerate(env, userId, tier = 'FREE') {
  const limits = LIMITS[tier] ?? LIMITS.FREE;
  const [today, courses] = await Promise.all([usage(env, userId), courseCount(env, userId)]);

  if (today.generations >= limits.generationsPerDay) {
    return { allowed: false, reason: 'daily_generation_limit' };
  }
  if (courses >= limits.courses) {
    return { allowed: false, reason: 'course_limit' };
  }
  return { allowed: true };
}

/** Count a generation. Called only after the model actually produced a course. */
export async function countGeneration(env, userId) {
  await db(env)`
    INSERT INTO daily_usage (user_id, day, generations)
    VALUES (${userId}, CURRENT_DATE, 1)
    ON CONFLICT (user_id, day) DO UPDATE SET generations = daily_usage.generations + 1
  `;
}

export { LIMITS };
