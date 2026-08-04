# Curio

Type any topic. Get a structured, interactive course in under a minute.

Duolingo's interaction model applied to everything, not just languages. Existing AI
learning tools output walls of text; Curio outputs **exercises**.

Built for [RevenueCat Shipaton 2026](https://shipaton.revenuecat.com). Compose Multiplatform, Android + iOS.

---

## Status — Aug 3

Day one is playable. `./gradlew :composeApp:installDebug` puts a real lesson on a phone:
a hand-written course on how compilers work, parsed from the exact JSON the API will
return, routed into exercises by the format router, and played through the exercise
player with haptics and Cue reacting.

| Piece | State |
|---|---|
| Format router (`router/`) + unit tests | Done |
| Domain model, six exercise types | Done |
| `Theme.kt` — colour, type, space, motion | Done (type faces TBD) |
| Cue — procedural orb, 7 states | v1 |
| Exercise player, page turns, progress | Done |
| Multiple choice, tap-to-fill | Done |
| Reorder, match pairs, sort buckets, teach-back | Aug 10–12 |
| SQLDelight persistence | Aug 4–5 |
| `/generate`, `/grade`, cache | Aug 8–9 |
| RevenueCat, OneSignal | Aug 13–14 |

## Build

```bash
gradle wrapper --gradle-version 8.11.1   # once, if gradlew is absent
./gradlew :composeApp:installDebug       # Android
./gradlew :composeApp:allTests           # router + cache-key tests
```

iOS needs a Mac: open `iosApp/iosApp.xcodeproj` after running
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.

Requires JDK 17+ and the Android SDK (`compileSdk 35`).

## The interesting part: the format router

Anyone can clone six exercise UIs. The hard part is picking the right one
consistently across arbitrary topics — a process should become a Reorder, a
taxonomy should become a Sort. Get this wrong and it feels like a quiz generator;
get it right and it feels like a teacher.

So the model never generates exercises. It returns **content tagged with a
pedagogical shape**, and `FormatRouter` maps shapes to exercise types in pure,
deterministic, unit-tested Kotlin:

```
definition   → MatchPairs (aggregated) | TapToFill | MultipleChoice
sequence     → Reorder
taxonomy     → SortBuckets
comparison   → SortBuckets | MultipleChoice
fact         → MultipleChoice
concept      → TeachBack
```

The model does knowledge; the code does structure. That split is why the output is
testable and debuggable instead of a pile of inconsistent JSON.

Guarantees, all enforced by tests in `FormatRouterTest`:

1. Exactly one Teach-back, always last.
2. No format repeats more than twice per lesson.
3. At least three distinct exercise types per lesson.
4. Same input and seed produce byte-identical output — a cached course renders the
   same in December as it did in August.
5. A chunk that cannot satisfy an exercise's invariants degrades to a simpler
   format rather than throwing. **It is never allowed to invent an answer** — a fact
   with no distractors becomes a tap-to-fill, not a multiple choice with fabricated
   wrong options.

## The cache is the architecture

`topicHash(topic, depth)` is the cache key, and it is the whole business model.
A course on Big-O is identical for every user: the first pays for generation, the
next thousand are free. Marginal cost trends toward zero and unit economics
improve with scale — the inverse of most AI apps.

Normalisation is deliberately *conservative*: `+` and `#` survive, because
collapsing "c++" into "c" does not just miss the cache, it serves the wrong course.

`topicHash` must stay byte-identical on client and server. Port it verbatim.

## Layout

```
composeApp/src/commonMain/kotlin/app/curio/
├── domain/      Course, Lesson, Exercise (sealed), Chunk (wire format)
├── router/      the format router — pure, no Compose, no IO
├── data/        JSON config, the day-one sample course
├── platform/    expect declarations (haptics only)
└── ui/
    ├── theme/       Theme.kt — never hardcode a value outside this file
    ├── cue/         the orb: state machine + Canvas renderer
    ├── components/  one composable per exercise type
    └── screens/     lesson player (home / generate / path land Aug 8-9)
```

Haptics are the only platform-specific code in the app. Everything else, including
spaced repetition, lives in `commonMain`.

## Design notes

**Cue is an orb, not a character.** Character art is the most common way a solo dev
ships something that reads as cheap — a rigged animal needs hundreds of consistent
assets. Cue is procedural Compose Canvas, zero image files, expressive through
squash, stretch, pulse, wobble, orbit and scatter. Infinitely consistent, because
it is maths.

**No submit buttons.** Answers register on tap. **No ripple** — feedback is scale,
colour and haptic, owned in `Theme.kt`. **No spinners.** Page turns, not fades.

Every Cue state pairs with a haptic; `Haptic` is a set of semantic events, not
waveforms, so each platform decides how "correct" should feel. A wrong answer uses
a warning, never an error pattern — a wrong answer is information, not a failure.

## Licence

MIT. See [LICENSE](LICENSE).
