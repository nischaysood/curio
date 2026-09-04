# Curio — Roadmap to Play submission

**Window** Aug 4 – Aug 15, 2026 · **Budget** 2–3 focused hours/day · **~30 hours**

**Goal:** signed Curio build submitted to Google Play by Aug 15, with RevenueCat and
OneSignal live and topic generation working.

---

## Division of labour

This matters more than the schedule. Roughly a third of what's left cannot be
delegated, and that third is the critical path.

| Claude does | Only Nischay can do |
|---|---|
| Kotlin, Compose, the four remaining exercise types | Play Console: app record, products, testing tracks |
| FastAPI server, Postgres schema, cache | RevenueCat dashboard: offerings, entitlements |
| Gemini + Groq integration | OneSignal dashboard: app ID, journey config |
| OneSignal `expect`/`actual` wrapper | Testing on a real device — feel, haptics, timing |
| Store listing copy, privacy policy text | Screenshots, icon export, data safety form |
| Bundled course content | API keys, billing, spend caps |
| Debugging from a stack trace | Deciding when something is good enough |

**Budget your hours toward the right column.** Bring me stack traces and decisions;
don't spend your 2 hours writing code I can write in five minutes.

---

## Scope: what ships in the MVP

**In:** all six exercise types · Path screen · Generate screen with live generation ·
assembly animation · streaks · RevenueCat paywall · OneSignal + one journey ·
Android only.

**Out, deliberately:**

| Cut | Why | Lands |
|---|---|---|
| **Drag-and-drop** | Reorder uses tap-to-swap, SortBuckets uses tap-to-assign. Saves ~6 hrs — the single biggest saving available. Duolingo's own reorder is tap-based. | Aug 20+, only if it tests better |
| **iOS** | Needs a Mac. Ship Kotlin Everywhere is a Sept 1 decision. | Sept 1–10 |
| **Home screen** | v0 opens on Path. Home matters when you have many courses; on day one you have one. | Aug 16+ |
| **SQLDelight** | Overkill for v0's streak + progress. `multiplatform-settings` is 30 min. | When courses become downloadable |
| **Offline download, review history, Deep depth** | Premium features. Paywall can advertise them before they exist — that's normal. | Aug 16–31 |

### The one architectural rule that protects Aug 15

**Bundled courses and generated courses go through the identical path.** The app
ships with 4 hand-written courses and works fully without a server.

That's not a fallback bolted on — it's the design. If the server slips, or Gemini
rate-limits you on Aug 14, or the deploy fights you, **you still ship on time** with
a working app. Generation becomes additive rather than load-bearing.

The client already does this: `SampleCourse.kt` parses the exact wire format
`/generate` will return. On Aug 12 the only change is where the string comes from.

---

## Daily plan

One theme per day, scoped to ~2.5 hrs. **If a day overruns, cut its last item —
never push it into tomorrow.** Slipped work compounds; cut work doesn't.

### Aug 4 (today) — unblock
*No features. Every item here is a clock that starts without you.*

- [ ] **Compile.** `./gradlew :composeApp:allTests`, then `installDebug`. Send me
      the errors — don't debug them yourself. *(60 min)*
- [ ] **Play Console: create the app record**, then find out whether the 12-tester /
      14-day closed testing rule applies to your account. ← *the highest-value 30
      minutes of your week; it determines whether Sept 1 is comfortable or brutal*
- [ ] **Upload today's build to internal testing.** One lesson, ugly. It starts the
      review relationship and unlocks IAP product creation. *(20 min)*
- [ ] Repo public · Devpost entry with **chitkara.edu.in** · **devlog #1** *(30 min)*

### Aug 5 — RevenueCat
*Before more UI. It gates the most prize money and is most likely to surprise you.*

- [ ] SDK in `commonMain`, key in `local.properties` · Play products, monthly +
      annual, India regional pricing · `Entitlements` gating (1 course, 3 lessons/day)
      · paywall screen after lesson 3

### Aug 6 — MatchPairs + Reorder
- [ ] Tap left → tap right → pair locks and flies off · `Haptic.LOCK`
- [ ] Tap a row → tap its destination → swap · lock animation on correct order

### Aug 7 — SortBuckets + TeachBack
- [ ] Tap chip → tap bucket
- [ ] Text field · Cue's listening state · **offline grading heuristic** (keyword
      coverage against `rubric` + length floor). Honest for v0 — it rewards recall,
      which is most of the value. Groq replaces it Aug 11.

**End of Aug 7: all six exercise types exist. The core loop is complete.**

### Aug 8 — Path screen + content
- [ ] Node graph: locked / available / complete / needs-review. Your own visual
      language, not a winding road.
- [ ] Path → Lesson → back
- [ ] 3 more bundled courses. Pick topics that demo well **and that you can
      fact-check yourself.**

### Aug 9 — persistence, streaks, OneSignal wrapper
- [ ] `multiplatform-settings`: progress, streak, lesson state
- [ ] Streak counter · `Haptic.STREAK` · Cue's orbit rings (already built)
- [ ] OneSignal `expect`/`actual` wrapper — keep it clean, this becomes the OSS
      library and the GSoC artifact

### Aug 10 — server, part 1
- [ ] FastAPI skeleton, `/generate` `/grade` `/sync` · Postgres schema · cache
      lookup · **`topicHash` ported verbatim from `Course.kt`** ← must be
      byte-identical or the cache silently never hits · deploy to Railway/Fly

### Aug 11 — server, part 2
- [ ] Gemini outline (stage 1) · content chunks (stage 2), schema-constrained ·
      validate + retry once · Groq teach-back grading

### Aug 12 — wire it up
- [ ] Client API client · Generate screen: topic field + depth selector · topic
      moderation · **paid API key + hard spend cap** ← before any public build

### Aug 13 — assembly animation
*The Design Award submission. A full session, not a leftover 20 minutes.*
- [ ] Cue fragments and swirls · nodes drop in with real titles · connections draw ·
      structure settles. Runs over bundled loads too, with staged reveal.

### Aug 14 — store assets
- [ ] Icon (a Cue orb on warm paper — palette's already in `Theme.kt`) · privacy
      policy on GitHub Pages · listing copy · 4 screenshots · feature graphic ·
      data safety + content rating · OneSignal journey #1 (day-1 review nudge,
      referencing the actual topic)

### Aug 15 — ship
*No new features. Something will go wrong and you need the slack.*
- [ ] Full run-through on a real device · fix what's embarrassing, ignore what
      isn't · **signed release → closed testing → submit**

---

## The north star

**Learn anything, gamified.**

The "learn anything" half is done as of Aug 23 — type any topic, get a real
generated course. The gamified half is the post-MVP build, and it's the thing
that turns a good demo into something people open on a Tuesday.

Worth knowing: the cheap half is already in the codebase. Cue's orbit rings and
the `STREAK` haptic exist, and `LessonState` already tracks complete vs
needs-review. A basic streak counter is ~40 minutes on top of persistence. The
expensive half — XP, leagues, quests, daily goals — is September and beyond, and
should be designed against real retention numbers rather than guessed at.

## After the MVP — the full-stack version

The direction: accounts, a database of your past courses, credits, and genuine
"type anything and learn it". Sequenced by what actually unlocks product value.

| # | Thing | Effort | Why this order |
|---|---|---|---|
| 1 | **Generation** — Worker + Gemini + KV cache | ~4 hrs | This IS the promise. Everything below is convenience; this is the product. Ship it before anything else. |
| 2 | **Persistence** — progress, streaks, course history on device | ~2 hrs | Makes the app feel real. No server needed. |
| 3 | **Anonymous → account** | ~4 hrs | Anonymous by default (already the plan). Offer an account at the paywall so a reinstall doesn't wipe 30 days of streak. |
| 4 | **Server-side course library** | ~6 hrs | Sync courses across devices. Only worth it once accounts exist. |
| 5 | **Credits** | ~4 hrs | See the fork below. |

### The credits fork — decide before configuring Play products

| | Subscription (PRD's original plan) | Credits (consumable IAP) |
|---|---|---|
| Revenue | Predictable, compounds | Lumpy, re-sell every time |
| Fits | Unlimited learning, daily habit | Occasional heavy generation |
| RevenueCat | Entitlements — the simple path | Consumables + a server-side balance you must not lose |
| Cost alignment | Poor — a heavy user costs you more than they pay | Good — generation cost maps to credits spent |
| Play setup | Two products | Several tiers, plus refund and restore handling |

**Leaning subscription**, because the cache is what makes generation nearly free
after the first user — which is the entire HAMM writeup. Credits price a marginal
cost that the architecture is specifically designed to eliminate.

Whichever you pick: choose it BEFORE creating Play Console products. Switching
afterwards means redoing the store side and any paywall you've already built.

## Every day, non-negotiable

**#BuildInPublic post — 10 minutes.** Largest single prize in the hackathon ($30k),
and the criterion is the *process*, including the uncertainty. Post the cuts and the
bugs, not just the wins. Highest return per minute in this document, and
unrecoverable if you start late.

---

## Risks

| Risk | Response |
|---|---|
| **Play's 14-day closed testing applies** | You'll know today. If so, recruit 12 testers on Aug 5 — friends, classmates, anyone with an Android phone. |
| **Compile takes 3 hrs, not 1** | Most likely today. Send me errors immediately rather than working through them. |
| **RevenueCat product setup fights you** | Budget a second session Aug 6; push MatchPairs to Aug 7. |
| **Server slips past Aug 12** | Ship with bundled courses. This is exactly what that design protects. Generation lands Aug 16–20. |
| **You lose 2+ days** | Cut in this order: assembly animation → OneSignal → server → extra courses. Never cut RevenueCat; it's eligibility. |
| **Aug 15 arrives and it's rough** | Submit anyway. "Ugly and working, not finished" is your own instruction and it's correct. Review runs while you polish. |
