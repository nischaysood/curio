/**
 * Prompts.
 *
 * The schema guarantees SHAPE. These prompts have to earn everything else:
 * accuracy, the right pedagogical shape for the material, and distractors that
 * are wrong without being absurd.
 *
 * The hardest instruction here is "don't invent distractors". A model asked for
 * three wrong answers will always produce three wrong answers, even when it has
 * to make up a plausible-sounding falsehood to do it — and a learning app that
 * teaches falsehoods is worse than one that teaches nothing. Better an empty
 * array; the router degrades that chunk to a format that needs no distractors.
 */

export function outlinePrompt(topic, lessonCount) {
  return `Break "${topic}" into exactly ${lessonCount} lessons for a curious adult beginner.

Rules:
- Order them so each lesson only relies on earlier ones. Lesson 1 assumes nothing.
- Titles are concrete and specific: "Lexical Analysis", not "Getting Started" or "Introduction".
- The objective is one sentence saying what the learner can DO afterwards.
- No lesson about "what you'll learn", history-of-the-field, or "next steps".
- If the topic is very narrow, go deeper rather than padding with filler lessons.`;
}

export function chunksPrompt(topic, lessonTitle, objective) {
  return `Write the teaching content for one lesson of a course on "${topic}".

Lesson: ${lessonTitle}
Objective: ${objective}

Return 5-6 content chunks. Each chunk is one idea, tagged with the SHAPE that
best teaches it. Choose the shape from the material, not from variety:

- definition  — a term with a precise meaning.
                "content" must be one sentence AND must contain the term itself.
                "distractors": 2-3 plausible but WRONG definitions.
- sequence    — an ordered process. "steps": 3-6, in correct order.
- taxonomy    — categories with members.
                "categoryNames": 2-3 names.
                "categoryMembers": every member, prefixed with its category and a
                colon, e.g. "keyword: while".
- comparison  — two things that contrast.
                "leftLabel"/"rightLabel", then "leftItems"/"rightItems", 2-4 each.
- fact        — a specific checkable fact. "question", "answer",
                "distractors": 3 wrong answers.
- concept     — the lesson's central idea. EXACTLY ONE per lesson.
                "content": 1-2 sentences.
                "keyPoints": 3-4 things a good explanation would mention.

Hard rules:
- Everything must be factually correct. If you are unsure of a detail, leave that
  chunk out rather than guessing.
- Distractors must be genuinely wrong but believable to a beginner. Never absurd,
  never a synonym of the right answer, never "all of the above".
- If you cannot write honest wrong answers for a chunk, return an empty
  distractors array. Do NOT invent a falsehood to fill the slot.
- Include exactly one "concept" chunk and at least three other shapes.
- No markdown, no emoji, no "In this lesson we will...".`;
}

export function gradePrompt(concept, rubric, answer) {
  return `A learner was asked to explain "${concept}" in their own words.

A good answer covers:
${rubric.map((r) => `- ${r}`).join('\n')}

Their answer:
"""
${answer}
"""

Judge whether they understand it. Rules:
- Their own wording is the POINT. Never penalise phrasing, grammar, or spelling.
- Partial understanding passes. Missing detail is not the same as being wrong.
- An answer that is confidently WRONG fails, even if it is fluent.
- "missed": rubric points they did not touch. Empty if they covered everything.
- "feedback": one warm sentence, max 15 words. Never sarcastic, never
  disappointed. If they got it wrong, say what the idea actually is.`;
}
