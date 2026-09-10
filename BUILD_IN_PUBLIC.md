# #BuildInPublic — Curio

The category is judged on **process**, not polish. Specific beats impressive.
"Here's the bug that ate my evening" outperforms "excited to announce" every time.

Post as yourself. These are drafts, not scripts — rewrite them in your own words
or they'll read like someone else wrote them, because someone else did.

**Tag every post `#BuildInPublic` and `#Shipaton`.** That's how judges find you.

---

## Post 1 — today (the iOS win)

> Got Curio running on iOS today.
>
> Same Kotlin codebase as Android. ~95% shared — every screen, the course
> generator, the exercise router, all of it. Only four things are platform
> specific: haptics, back button, billing, and where the login token is stored.
>
> Took 6 hours. Five of them were Xcode.
>
> The actual blocker: Compose Multiplatform needs a Gradle script to build the
> Kotlin framework, and Xcode 15+ sandboxes build scripts by default. Turning
> that off isn't enough — the Gradle daemon caches the old sandboxed environment,
> so you also have to `./gradlew --stop`. Nothing tells you that. The error just
> says a directory isn't accessible.
>
> [screenshot: same screen, Android + iOS side by side]
>
> #BuildInPublic #Shipaton #KotlinMultiplatform

The side-by-side screenshot is the whole post. Take it.

---

## Post 2 — the format router (your actual IP)

> Most AI learning apps generate multiple choice for everything, because
> multiple choice is easy to generate.
>
> Curio doesn't. The model returns content tagged with what *kind* of idea it is
> — a definition, a sequence, a comparison, a taxonomy — and then deterministic
> Kotlin decides the exercise type from that shape.
>
> A process becomes a reordering task. A definition becomes matching pairs. A set
> of categories becomes sorting.
>
> The model never picks the format. That's the whole design, and it's the part
> I'd defend hardest.
>
> 23 tests on that router. It guarantees: exactly one teach-back, always last.
> No format repeated more than twice. At least 3 distinct types per lesson. And
> it degrades rather than throwing.
>
> #BuildInPublic #Shipaton

---

## Post 3 — a failure post (these do the best)

Pick whichever is truest on the day. Real candidates from this build:

- Four rejected Play uploads in a row: debug-signed, then a missing icon, then
  targetSdk 35 when Play wants 36, then a versionCode that was already consumed.
  Every one a different reason.
- The Worker logged `Ok` while users saw failures for a week, because a
  `.catch(() => null)` swallowed every lesson error.
- Committed `node_modules` without noticing, then had 1,504 files in one commit.
- Shipped a paywall promising "offline downloads" and "review history". Neither
  exists. Caught it in review, cut the lines.

> [the failure]
>
> [what you actually changed]
>
> [the one-line lesson]

Three sentences. No moral. People trust the ones that don't perform humility.

---

## Post 4 — the economics

> Curio's unit economics get *better* with scale, which is the opposite of most
> AI apps.
>
> Courses are cached by a hash of (topic, depth). "How compilers work" is
> identical for everyone who asks, so the first person pays for generation and
> the next thousand are free. Marginal cost trends to zero.
>
> That's also why the free tier has limits at all. Generation costs real money.
> The caps are honest constraints, not fake scarcity — and I put that sentence in
> the actual store listing.
>
> #BuildInPublic #Shipaton

---

## Post 5 — Sept 20-ish, the testing rule

> Play requires new personal developer accounts to run a closed test with 12
> testers, opted in continuously for 14 days, before you can even *apply* for
> production. Then review takes up to 7 days.
>
> So shipping an Android app now takes three weeks minimum regardless of how
> fast you build.
>
> [how you found 12 people, what they broke]
>
> #BuildInPublic #Shipaton

---

## Cadence

Daily is ideal, every other day is fine. **Consistency beats quality here** —
the category rewards showing the process, and a gap reads as abandonment.

Ten minutes a day. Screenshot whatever you were looking at, write three
sentences about what broke, post it.

## What to keep screenshotting

- Anything red. Error screens are the best content you have.
- Before/after of a screen you redesigned
- The RevenueCat sandbox dashboard the first time real money appeared
- Tester count climbing
- The Play "in review" banner, then the live listing

## Don't

- "Excited to announce" — nobody is
- Threads with no screenshots
- Posting only wins. The failure posts are what makes the win posts land.
- Explaining what an LLM is
