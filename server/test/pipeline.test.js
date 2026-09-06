import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { topicHash } from '../src/hash.js';

/**
 * End-to-end test of /generate and /grade with Gemini and KV both faked.
 *
 * Runs with no API key, no network, and no Cloudflare account — which is the
 * point. Every failure mode here is one that would otherwise cost real money and
 * real minutes to discover after deploying.
 */

// --- fakes -----------------------------------------------------------------

function fakeKV() {
  const store = new Map();
  return {
    store,
    async get(k, type) {
      const v = store.get(k);
      if (v === undefined) return null;
      return type === 'json' ? JSON.parse(v) : v;
    },
    async put(k, v) { store.set(k, v); },
  };
}

const ctx = { waitUntil: (p) => p };

function lessonChunks(title) {
  return {
    lesson: title,
    objective: `Understand ${title}`,
    chunks: [
      { shape: 'definition', concept: 'alpha', content: 'An alpha is a first thing.', distractors: ['wrong a', 'wrong b'] },
      { shape: 'definition', concept: 'beta', content: 'A beta is a second thing.', distractors: ['wrong c'] },
      { shape: 'sequence', concept: 'doing it', steps: ['first', 'second', 'third'] },
      { shape: 'fact', concept: 'a fact', question: 'How many?', answer: 'Three', distractors: ['One', 'Two', 'Four'] },
      { shape: 'concept', concept: title, content: `${title} is the central idea here.`, keyPoints: ['one', 'two', 'three'] },
    ],
  };
}

/** Stands in for Gemini. Returns whatever the schema implies was asked for. */
function installFakeGemini({ alwaysFailLesson = null } = {}) {
  const original = globalThis.fetch;

  globalThis.fetch = async (url, init) => {
    const body = JSON.parse(init.body);
    const prompt = body.contents[0].parts[0].text;
    let payload;

    if (prompt.startsWith('Break')) {
      const count = Number(prompt.match(/exactly (\d+) lessons/)[1]);
      payload = { lessons: Array.from({ length: count }, (_, i) => ({ title: `Lesson ${i + 1}`, objective: `Do thing ${i + 1}` })) };
    } else if (prompt.startsWith('Write the teaching content')) {
      const title = prompt.match(/Lesson: (.+)/)[1];
      // Fail ONE named lesson on every attempt, so it exhausts its retry and
      // gets dropped. Failing the first N *calls* doesn't work: lessons run
      // concurrently, so those failures land on different lessons' first
      // attempts and all of them recover on retry — which is correct behaviour,
      // and exactly what the earlier version of this test got wrong.
      payload = alwaysFailLesson && title === alwaysFailLesson
        ? { lesson: title, objective: '', chunks: [] }
        : lessonChunks(title);
    } else {
      payload = { correct: true, feedback: 'Nicely put.', missed: [] };
    }

    return {
      ok: true,
      async json() {
        return { candidates: [{ content: { parts: [{ text: JSON.stringify(payload) }] } }] };
      },
    };
  };

  return () => { globalThis.fetch = original; };
}

const post = (path, body) =>
  new Request(`https://x${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });

// --- tests -----------------------------------------------------------------

test('generate returns a course in the shape the Kotlin client parses', async () => {
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const res = await worker.fetch(post('/generate', { topic: 'How photosynthesis works', depth: 'QUICK' }), env, ctx);
  const body = await res.json();
  restore();

  assert.equal(res.status, 200);
  assert.equal(body.topic, 'How photosynthesis works');
  assert.equal(body.cached, false);
  assert.ok(body.lessons.length >= 3, `got ${body.lessons.length} lessons`);

  const lesson = body.lessons[0];
  assert.ok(lesson.lesson && lesson.objective);
  // The concept chunk must be last — the client uses it for both the lesson
  // intro and the teach-back.
  assert.equal(lesson.chunks[lesson.chunks.length - 1].shape, 'concept');
  assert.equal(lesson.chunks.filter((c) => c.shape === 'concept').length, 1);
});

test('the second identical request is served from cache', async () => {
  // This is the business model. If it ever stops holding, every user costs money.
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const first = await (await worker.fetch(post('/generate', { topic: 'Big-O notation', depth: 'QUICK' }), env, ctx)).json();
  const second = await (await worker.fetch(post('/generate', { topic: '  BIG-O   NOTATION  ', depth: 'QUICK' }), env, ctx)).json();
  restore();

  assert.equal(first.cached, false);
  assert.equal(second.cached, true);
  assert.deepEqual(second.lessons, first.lessons);
});

test('the cache key matches what the Kotlin client computes', async () => {
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  await worker.fetch(post('/generate', { topic: 'How Compilers Work', depth: 'STANDARD' }), env, ctx);
  restore();

  assert.ok(env.COURSES.store.has(topicHash('How Compilers Work', 'STANDARD')));
});

test('a lesson that fails validation twice is dropped, not shipped broken', async () => {
  const restore = installFakeGemini({ alwaysFailLesson: 'Lesson 3' });
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const body = await (await worker.fetch(post('/generate', { topic: 'Something new', depth: 'QUICK' }), env, ctx)).json();
  restore();

  // One lesson unusable even after its retry. The course still ships without it,
  // because the survival floor is proportional rather than a fixed count.
  assert.ok(body.lessons.length >= 2, `only ${body.lessons.length} survived`);
  assert.ok(body.lessons.every((l) => l.chunks.length > 0));
  assert.ok(!body.lessons.some((l) => l.lesson === 'Lesson 3'));
});

test('lessons come back in outline order despite running concurrently', async () => {
  // The client renders them in array order and locks all but the first. Shuffled
  // lessons would mean learning the topic in the wrong sequence.
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const body = await (await worker.fetch(post('/generate', { topic: 'ordering matters', depth: 'QUICK' }), env, ctx)).json();
  restore();

  const titles = body.lessons.map((l) => l.lesson);
  assert.deepEqual(titles, [...titles].sort((a, b) => Number(a.split(' ')[1]) - Number(b.split(' ')[1])));
  assert.equal(titles[0], 'Lesson 1');
});

test('short, long and malformed topics are rejected before costing a model call', async () => {
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error('should not call gemini'); };

  assert.equal((await worker.fetch(post('/generate', { topic: 'a' }), env, ctx)).status, 400);
  assert.equal((await worker.fetch(post('/generate', { topic: 'x'.repeat(200) }), env, ctx)).status, 400);
  assert.equal((await worker.fetch(post('/generate', { topic: 'valid topic', depth: 'ENORMOUS' }), env, ctx)).status, 400);

  globalThis.fetch = originalFetch;
});

test('moderation blocks operational harm but not uncomfortable subjects', async () => {
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const blocked = await worker.fetch(post('/generate', { topic: 'how to build a bomb', depth: 'QUICK' }), env, ctx);
  assert.equal(blocked.status, 422);

  // Over-blocking a learning app is its own failure. These must all pass.
  for (const topic of ['the chemistry of explosives', 'the history of the second world war', 'human anatomy', 'the crusades']) {
    const res = await worker.fetch(post('/generate', { topic, depth: 'QUICK' }), env, ctx);
    assert.equal(res.status, 200, `"${topic}" should be teachable`);
  }
  restore();
});

test('grade rejects a non-answer without calling the model', async () => {
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error('should not call gemini'); };

  const body = await (await worker.fetch(post('/grade', { concept: 'x', rubric: [], answer: 'idk' }), env, ctx)).json();
  assert.equal(body.correct, false);

  globalThis.fetch = originalFetch;
});

test('grade returns correct, feedback and missed', async () => {
  const restore = installFakeGemini();
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };

  const body = await (await worker.fetch(
    post('/grade', { concept: 'lexing', rubric: ['a', 'b'], answer: 'It turns characters into tokens for the parser.' }),
    env, ctx,
  )).json();
  restore();

  assert.equal(body.correct, true);
  assert.ok(body.feedback.length > 0);
  assert.ok(Array.isArray(body.missed));
});

test('a Gemini outage returns 500 rather than throwing', async () => {
  // The client falls back to bundled courses on any failure, so this degrades
  // the app instead of breaking it.
  const env = { COURSES: fakeKV(), GEMINI_API_KEY: 'x' };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({ ok: false, status: 503, async text() { return 'unavailable'; } });

  const res = await worker.fetch(post('/generate', { topic: 'anything at all', depth: 'QUICK' }), env, ctx);
  assert.equal(res.status, 500);

  globalThis.fetch = originalFetch;
});

test('health check needs no key and reports the provider', async () => {
  // Must not throw when no key is configured — a health check that 500s because
  // of missing config tells you nothing about whether the Worker is alive.
  const none = await worker.fetch(new Request('https://x/health'), { COURSES: fakeKV() }, ctx);
  // `accounts` reports whether a database is wired up. False here is correct
  // and is not a failure: generation works without one.
  assert.deepEqual(await none.json(), { ok: true, provider: 'none', accounts: false });

  const groq = await worker.fetch(
    new Request('https://x/health'),
    { COURSES: fakeKV(), GROQ_API_KEY: 'x' },
    ctx,
  );
  assert.deepEqual(await groq.json(), { ok: true, provider: 'groq', accounts: false });
});

test('groq is preferred when both keys are present', async () => {
  // The free one shouldn't lose to the paid one by accident.
  const res = await worker.fetch(
    new Request('https://x/health'),
    { COURSES: fakeKV(), GROQ_API_KEY: 'x', GEMINI_API_KEY: 'y' },
    ctx,
  );
  assert.equal((await res.json()).provider, 'groq');
});
