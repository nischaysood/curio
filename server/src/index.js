import { topicHash, normaliseTopic } from './hash.js';
import { OUTLINE_SCHEMA, CHUNKS_SCHEMA, GRADE_SCHEMA } from './schema.js';
import { outlinePrompt, chunksPrompt, gradePrompt } from './prompts.js';
import { validateLesson } from './validate.js';
import { generate as callModel, providerName } from './provider.js';
import { hasDb } from './db.js';
import { signup, login, userFromRequest, destroySession, looksLikeEmail } from './auth.js';
import { listCourses, saveCourse, recordProgress, usage, canGenerate, countGeneration, profile } from './sync.js';
import { tierOf } from './tier.js';

/**
 * Curio's entire backend. Three endpoints, deliberately small.
 *
 *   POST /generate     { topic, depth }               -> { topic, depth, lessons: [...] }
 *   POST /grade        { concept, rubric, answer }    -> { correct, feedback, missed }
 *   GET  /health
 *
 *   POST /auth/signup  { email, password }            -> { token, expiresAt }
 *   POST /auth/login   { email, password }            -> { token, expiresAt }
 *   POST /auth/logout                                 -> { ok }
 *   GET  /me/courses                                  -> { courses: [...] }
 *   POST /me/progress  { topicHash, lessonId, ... }   -> { state, usage }
 *   GET  /me/usage                                    -> { lessons, generations }
 *   GET  /me/profile                                  -> { email, courses, usage, ... }
 *
 * Everything under /auth and /me needs a database; everything above it doesn't.
 * That split is deliberate — no DATABASE_URL means accounts are unavailable and
 * generation still works, because generation is the product.
 *
 * The cache is the architecture. A course on Big-O is identical for every user:
 * the first person pays for generation, the next thousand are free. Marginal
 * cost trends to zero and unit economics IMPROVE with scale, which is the
 * inverse of most AI apps.
 */

// Sized against a real token budget, not a guess. Groq's free tier allows 8,000
// tokens per minute and one lesson costs roughly 2,500 — so about three lessons
// per minute, full stop. Quick fits inside one window; Standard takes two.
// Bigger numbers here don't produce bigger courses, they produce failed ones.
const LESSON_COUNT = { QUICK: 3, STANDARD: 5, DEEP: 8 };
const CACHE_TTL_SECONDS = 60 * 60 * 24 * 90;

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', 'access-control-allow-origin': '*' },
  });

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'access-control-allow-origin': '*',
          'access-control-allow-methods': 'POST, GET, OPTIONS',
          'access-control-allow-headers': 'content-type, authorization',
        },
      });
    }

    if (url.pathname === '/health') {
      return json({ ok: true, provider: safeProvider(env), accounts: hasDb(env) });
    }

    try {
      if (url.pathname === '/generate' && request.method === 'POST') {
        return await handleGenerate(request, env, ctx);
      }
      if (url.pathname === '/grade' && request.method === 'POST') {
        return await handleGrade(request, env);
      }

      // --- accounts ---------------------------------------------------------
      // Guarded as a group. Without a database these routes can't do anything
      // useful, and a 503 that names the reason beats a stack trace.
      if (url.pathname.startsWith('/auth/') || url.pathname.startsWith('/me/')) {
        if (!hasDb(env)) return json({ error: 'accounts_unavailable' }, 503);
        return await handleAccount(url, request, env);
      }
    } catch (err) {
      // The client falls back to bundled courses on any failure, so a 500 here
      // degrades the experience rather than breaking it.
      console.error(err.stack || String(err));
      return json({ error: 'generation_failed' }, 500);
    }

    return json({ error: 'not_found' }, 404);
  },
};

// ---------------------------------------------------------------------------
// /auth/* and /me/*
// ---------------------------------------------------------------------------

async function handleAccount(url, request, env) {
  const path = url.pathname;

  // --- public ---------------------------------------------------------------

  if (path === '/auth/signup' && request.method === 'POST') {
    const { email, password } = await request.json();
    const result = await signup(env, email, password);
    // 409 for a taken address, 400 for malformed input. The client shows
    // different copy for each, and "try logging in instead" is only right for one.
    if (result.error) return json(result, result.error === 'email_taken' ? 409 : 400);
    return json(result);
  }

  if (path === '/auth/login' && request.method === 'POST') {
    const { email, password } = await request.json();
    const result = await login(env, email, password);
    // Deliberately vague: never reveal whether the address exists.
    if (result.error) return json({ error: 'bad_credentials' }, 401);
    return json(result);
  }

  if (path === '/auth/logout' && request.method === 'POST') {
    await destroySession(env, request);
    // Always ok. Logging out with a token that's already dead is a success from
    // the user's point of view — they wanted to be logged out, and they are.
    return json({ ok: true });
  }

  // --- authenticated --------------------------------------------------------

  const userId = await userFromRequest(env, request);
  if (!userId) return json({ error: 'unauthorized' }, 401);

  if (path === '/me/courses' && request.method === 'GET') {
    return json({ courses: await listCourses(env, userId) });
  }

  if (path === '/me/courses' && request.method === 'POST') {
    const course = await request.json();
    if (!course?.topicHash) return json({ error: 'bad_course' }, 400);
    await saveCourse(env, userId, course);
    return json({ ok: true });
  }

  if (path === '/me/progress' && request.method === 'POST') {
    const { topicHash, lessonId, correct = 0, total = 0 } = await request.json();
    if (!topicHash || !lessonId) return json({ error: 'bad_progress' }, 400);
    const result = await recordProgress(env, userId, { topicHash, lessonId, correct, total });
    // Return the fresh usage with it, so the app knows it has hit the daily
    // limit without a second round trip on every completed lesson.
    return json({ ...result, usage: await usage(env, userId) });
  }

  if (path === '/me/usage' && request.method === 'GET') {
    return json(await usage(env, userId));
  }

  if (path === '/me/profile' && request.method === 'GET') {
    const me = await profile(env, userId);
    // A valid token whose user row is gone means the account was deleted while
    // the session lived on. 401 tells the app to clear the token and show login,
    // which is right; a 404 would leave it stuck holding a dead token.
    return me ? json(me) : json({ error: 'unauthorized' }, 401)
  }

  return json({ error: 'not_found' }, 404);
}

// ---------------------------------------------------------------------------
// /generate
// ---------------------------------------------------------------------------

async function handleGenerate(request, env, ctx) {
  const { topic, depth = 'STANDARD' } = await request.json();

  const normalised = normaliseTopic(topic ?? '');
  if (normalised.length < 3) return json({ error: 'topic_too_short' }, 400);
  if (normalised.length > 80) return json({ error: 'topic_too_long' }, 400);
  if (!(depth in LESSON_COUNT)) return json({ error: 'bad_depth' }, 400);

  const refusal = moderate(normalised);
  if (refusal) return json({ error: 'topic_refused', reason: refusal }, 422);

  const key = topicHash(topic, depth);

  // Auth is optional here. Signed out, generation still works and simply isn't
  // remembered — the app is usable before anyone has an account, which is what
  // makes the first run good.
  const userId = hasDb(env) ? await userFromRequest(env, request) : null;

  // --- cache ---------------------------------------------------------------
  const cached = await env.COURSES.get(key, 'json');
  if (cached) {
    // Request counts drive cache warming: the topics people actually ask for
    // are the ones worth pre-generating before launch.
    ctx.waitUntil(bumpCount(env, key));
    // A cache hit costs nothing, so it never counts as a generation — but it
    // does still attach the course to the account, so it shows up in history.
    if (userId) ctx.waitUntil(saveCourse(env, userId, cached));
    return json({ ...cached, cached: true });
  }

  // --- limits --------------------------------------------------------------
  // Checked before the model is called, not after. The whole point is to not
  // spend the tokens.
  if (userId) {
    const verdict = await canGenerate(env, userId, await tierOf(userId, env));
    if (!verdict.allowed) return json({ error: verdict.reason }, 402);
  }

  // --- stage 1: outline ----------------------------------------------------
  const outline = await callModel(env, {
    prompt: outlinePrompt(topic, LESSON_COUNT[depth]),
    schema: OUTLINE_SCHEMA,
    temperature: 0.4,
  });

  const entries = (outline.lessons ?? []).slice(0, LESSON_COUNT[depth]);
  if (entries.length === 0) return json({ error: 'no_outline' }, 502);

  // --- stage 2: content, throttled -----------------------------------------
  // Serial, not parallel. The binding limit is TOKENS per minute, not requests,
  // and two lessons in flight means two ~2,500-token responses landing together
  // — which blows an 8,000 token window immediately. One at a time, with the
  // retry backoff absorbing the gaps, is the only thing that actually completes.
  const lessons = (
    await mapWithConcurrency(entries, 1, (entry) =>
      generateLesson(env, topic, entry).catch((err) => {
        // `.catch(() => null)` swallowed the reason and left a 502 with an empty
        // log — the Worker reported Ok while the user saw a failure. Never
        // discard an error you're about to turn into a user-visible failure.
        console.error(`lesson "${entry.title}" failed: ${err.message || err}`);
        return null;
      }),
    )
  ).filter(Boolean);

  // A course that's mostly missing is worse than no course — the client falls
  // back to something coherent instead. Proportional, not a fixed floor: a hard
  // "at least 3" means a 3-lesson Quick course dies if a single lesson fails,
  // while an 8-lesson Deep course ships with 3 out of 8 and calls it fine.
  const minimum = Math.max(2, Math.ceil(entries.length / 2));
  if (lessons.length < minimum) {
    return json(
      { error: 'generation_failed', got: lessons.length, wanted: entries.length },
      502,
    );
  }

  const course = { topic: topic.trim(), depth, topicHash: key, lessons };

  ctx.waitUntil(
    env.COURSES.put(key, JSON.stringify(course), { expirationTtl: CACHE_TTL_SECONDS }),
  );

  // Counted only now — after the model actually produced something. Charging
  // someone a daily generation for a request that 502'd is the kind of thing
  // people remember.
  if (userId) {
    ctx.waitUntil(countGeneration(env, userId));
    ctx.waitUntil(saveCourse(env, userId, course));
  }

  return json({ ...course, cached: false });
}

/**
 * Run `fn` over `items` with at most `limit` in flight.
 *
 * Results stay in input order — the client relies on lesson order, and a course
 * whose lessons arrive shuffled is worse than one that took two seconds longer.
 */
async function mapWithConcurrency(items, limit, fn) {
  const results = new Array(items.length);
  let next = 0;

  async function worker() {
    while (next < items.length) {
      const i = next++;
      results[i] = await fn(items[i], i);
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

/** One lesson, with a single retry. Stage 4 validation decides if it's usable. */
async function generateLesson(env, topic, entry) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const raw = await callModel(env, {
      prompt: chunksPrompt(topic, entry.title, entry.objective),
      schema: CHUNKS_SCHEMA,
      // Second attempt runs colder — if the first was too loose to validate,
      // more creativity won't help.
      temperature: attempt === 0 ? 0.5 : 0.2,
    });

    const clean = validateLesson(raw, entry.title);
    if (clean) return { ...clean, objective: clean.objective || entry.objective };

    // Validation rejection is silent by design — it drops rather than repairs —
    // but silent at the API boundary too means a 502 with nothing to debug.
    console.error(
      `lesson "${entry.title}" attempt ${attempt + 1} failed validation; ` +
      `shapes: ${JSON.stringify((raw?.chunks ?? []).map((c) => c.shape))}`,
    );
  }
  return null;
}

// ---------------------------------------------------------------------------
// /grade
// ---------------------------------------------------------------------------

async function handleGrade(request, env) {
  const { concept, rubric = [], answer } = await request.json();

  if (!answer || answer.trim().length < 10) {
    return json({ correct: false, feedback: 'Say a little more.', missed: [] });
  }

  const result = await callModel(env, {
    prompt: gradePrompt(concept, rubric, answer.slice(0, 2000)),
    schema: GRADE_SCHEMA,
    temperature: 0.1,
  });

  return json({
    correct: Boolean(result.correct),
    feedback: result.feedback ?? '',
    missed: result.missed ?? [],
  });
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------

/**
 * Topic moderation. Required for both cost and store policy — users type
 * arbitrary text, and generation isn't free.
 *
 * Deliberately narrow. Curio should teach chemistry, history, anatomy, war and
 * religion; over-blocking a learning app is its own kind of failure. This
 * catches operational harm, not uncomfortable subjects.
 */
function moderate(normalised) {
  const blocked = [
    /\b(make|build|synthes\w*|manufactur\w*)\b.{0,20}\b(bomb|explosive|meth|nerve agent|sarin)\b/,
    /\bchild\b.{0,20}\b(porn|sexual|abuse)\b/,
    /\b(hack|ddos|keylog\w*)\b.{0,20}\b(someone|my ex|their|account|wifi)\b/,
  ];
  return blocked.some((re) => re.test(normalised)) ? 'policy' : null;
}

async function bumpCount(env, key) {
  const metaKey = `count:${key}`;
  const current = parseInt((await env.COURSES.get(metaKey)) ?? '0', 10);
  await env.COURSES.put(metaKey, String(current + 1));
}

/**
 * Which vendor is configured, for /health. Never throws: a health check that
 * 500s because no key is set tells you nothing useful about the Worker itself.
 */
function safeProvider(env) {
  try {
    return providerName(env);
  } catch {
    return 'none';
  }
}
