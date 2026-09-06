# Play Store listing — copy and paste

Everything below is sized to Play's limits. Character counts are in brackets.

---

## App name (30 max)

```
Curio: Learn Anything
```
[21]

## Short description (80 max)

```
Type any topic. Get an interactive course in under a minute. Built to be done.
```
[77]

## Full description (4000 max)

```
Curio turns anything you want to learn into a course you actually do.

Type a topic — compilers, crop rotation, options pricing, the Treaty of Westphalia, whatever's on your syllabus — and Curio builds a structured course in under a minute. Not an article. Not a video to half-watch. A sequence of lessons made of exercises.

WHY EXERCISES

Reading about something feels like learning and mostly isn't. Curio never hands you a wall of text to skim. Every lesson is built from things you do:

• Tap-to-fill — complete the idea, don't just recognise it
• Match pairs — connect terms to what they actually mean
• Reorder — put a process back in the right sequence
• Sort into buckets — decide what belongs where
• Multiple choice — for the facts that genuinely are facts
• Teach it back — explain the idea in your own words

That last one is the point. Every lesson ends by asking you to explain what you just learned, in writing, in your own words. It's the only honest test of whether something landed.

THE FORMAT FOLLOWS THE CONTENT

Most learning apps turn everything into multiple choice, because multiple choice is easy to generate. Curio doesn't. It works out what kind of idea each piece of material is — a definition, a sequence, a comparison, a taxonomy — and picks the exercise that actually suits it. A process becomes a reordering task. A definition becomes matching pairs. A set of categories becomes sorting.

Different ideas need different practice. That's the whole design.

ANY TOPIC, NOT A CATALOGUE

There's no library to browse and hope someone already made what you need. You type what you want to learn and it exists a minute later. Exam prep, a work thing you're behind on, something you got curious about at 1am — the app doesn't care which.

PICK YOUR DEPTH

Quick for a grounding in a few minutes. Standard for a proper working understanding. Deep when you want the whole thing.

FREE AND PREMIUM

Curio is free: one course, three lessons a day, one new course a day. Premium removes the daily limits and gives you unlimited courses.

To be straight about why the free tier has a ceiling at all: generating a course costs real money to run. The limits are honest constraints, not artificial scarcity to push you into paying.

Subscriptions renew automatically and can be cancelled any time in the Play Store.

—

Privacy: https://nischaysood.github.io/curio/privacy.html
Terms: https://nischaysood.github.io/curio/terms.html
```

---

## Data Safety form — exact answers

Play asks whether data is **collected** (leaves the device) and **shared** (goes to a
third party). Answer these exactly; a mismatch between this form and what the app
does is a common rejection, and it's checkable.

### Does your app collect or share any of the required user data types?
**Yes.**

### Personal info → Email address
- Collected: **Yes**
- Shared: **No**
- Processed ephemerally: **No**
- Required or optional: **Optional** (the app works signed out)
- Purpose: **Account management**

### App activity → Other user-generated content
This covers the topics typed and teach-back answers.
- Collected: **Yes**
- Shared: **Yes** — sent to Groq to generate and grade content
- Processed ephemerally: **No** (topics are cached; answers are not retained)
- Required or optional: **Required**
- Purpose: **App functionality**

### App activity → Other actions
Lesson progress and daily usage counters.
- Collected: **Yes**
- Shared: **No**
- Required or optional: **Optional**
- Purpose: **App functionality**, **Account management**

### Financial info → Purchase history
- Collected: **Yes**
- Shared: **No**
- Required or optional: **Optional**
- Purpose: **App functionality**

### Do NOT declare
No location, contacts, photos, files, messages, health, calendar, device IDs, or
advertising IDs. The app requests none of these and has no advertising SDK.

### Security practices
- Encrypted in transit: **Yes**
- Users can request data deletion: **Yes** → deletion URL is the privacy policy page
- Committed to Play Families policy: **No** (not a children's app)
- Independent security review: **No**

---

## Content rating questionnaire

- Category: **Reference, News, or Educational**
- Violence, sexuality, profanity, drugs, gambling: **No** to all
- User-generated content: **No** — nothing users write is shown to other users
- Users can interact / share location: **No**
- Digital purchases: **Yes**

Expected outcome: **Everyone** / PEGI 3.

Note: the app generates content on arbitrary topics, which can include war,
disease and other difficult subjects in an educational context. That's normal for
a reference app and doesn't require a higher rating — but answer honestly if
asked directly.

---

## Graphics still needed

| Asset | Spec | Notes |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | Raccoon on warm paper (#FAF6F0) |
| Feature graphic | 1024×500 PNG/JPG, no alpha | Raccoon left, "Learn anything, one exercise at a time" right |
| Phone screenshots | 2–8, min 1080px on short side, 16:9 or 9:16 | See below |

### Screenshots worth taking

1. **Generate screen** — "What do you want to learn?" with a topic typed
2. **Path screen** — the lesson sequence, showing structure
3. **A match-pairs or reorder exercise** — proves it isn't a quiz app
4. **Teach-back** — the differentiator
5. **Profile** — legitimacy, and shows the subscription is real

Capture with the emulator at 1080×1920:
```
adb exec-out screencap -p > shot1.png
```

---

## Also required in Play Console

- **App category:** Education
- **Tags:** Education, Self-improvement
- **Contact email:** nischay02sood@gmail.com
- **Privacy policy URL:** the GitHub Pages `/privacy.html` link
- **Ads:** No
- **Target audience:** 13+
- **News app:** No
- **COVID-19 app:** No
- **Data deletion:** account deletion via the email in the privacy policy
- **Government app:** No
- **Financial features:** None
