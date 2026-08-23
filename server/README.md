# Curio API

A Cloudflare Worker. Three endpoints, one KV namespace, no database.

```
POST /generate  { topic, depth }             -> { topic, depth, lessons: [...] }
POST /grade     { concept, rubric, answer }  -> { correct, feedback, missed }
GET  /health
```

## Deploy (~10 minutes)

```bash
cd server
npm install

npx wrangler login
npx wrangler kv namespace create COURSES
```

Paste the returned id into `wrangler.toml` under `kv_namespaces`, then:

```bash
npx wrangler secret put GEMINI_API_KEY     # from aistudio.google.com/apikey
npx wrangler deploy
```

Take the printed `https://curio-api.<you>.workers.dev` URL and put it in
`composeApp/src/commonMain/kotlin/app/curio/data/CurioConfig.kt`.

Until you do, the app runs on bundled courses only — fully functional, just a
smaller library. Generation is additive, never load-bearing.

## Test

```bash
npm test          # 17 tests, no network, no key required
```

## Why it's shaped like this

**The cache is the architecture.** A course on Big-O is identical for every user:
the first pays for generation, the next thousand are free. Marginal cost trends
toward zero and unit economics improve with scale — the inverse of most AI apps.
That's the HAMM writeup, and it's why this is still affordable in December.

**`topicHash` must stay byte-identical to `domain/Course.kt`.** If it drifts by a
single bit, the cache silently never hits, every request costs money, and nothing
visibly breaks — you'd find out from the bill. `test/validate.test.js` pins golden
values against the Kotlin implementation.

**The model never generates exercises.** It returns content tagged with a
pedagogical shape; the router (Kotlin, on device) decides which exercise teaches
it. Asking a model for exercises gives inconsistent JSON and hallucinated formats.
Asking for tagged content and mapping in code is testable and debuggable — and it
means a generated course and a bundled course are structurally identical.

**Validation drops, never repairs.** A chunk we can't verify is a chunk we
shouldn't teach. Silently fixing model output is how you ship a confident
falsehood to thousands of people at once.

**No honest distractors means no multiple choice.** If the model can't produce
believable wrong answers, it returns an empty array and the router degrades that
chunk to a format needing none. A learning app that teaches falsehoods is worse
than one that teaches nothing.

## Cost control before launch

- Hard monthly spend cap on the Gemini key — do this before any public build
- KV TTL is 90 days; courses don't go stale
- `count:<hash>` keys record demand, which tells you what to pre-generate
- Moderation is deliberately narrow: it blocks operational harm, not
  uncomfortable subjects. Curio should teach chemistry, war, anatomy and religion.
