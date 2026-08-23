/**
 * Stage 4 — validation.
 *
 * The schema guarantees the envelope. This guarantees the contents, and it is
 * the last line before content reaches a learner. A cached course serves
 * thousands of people, so a bad chunk that slips through here is not one bad
 * experience — it is thousands.
 *
 * Everything here DROPS rather than repairs. A chunk we can't verify is a chunk
 * we shouldn't teach, and silently "fixing" model output is how you end up
 * shipping a confident falsehood.
 */

/** Flatten Gemini's flat category arrays back into the map the client expects. */
function parseCategories(chunk) {
  // Accept the native map if the model produced one. Schemas can't express a
  // map with arbitrary keys, so the prompt asks for flattened arrays — but
  // models routinely emit the obvious `{ keyword: [...] }` anyway, and throwing
  // that away because it wasn't the shape we asked for loses good content.
  if (chunk.categories && typeof chunk.categories === 'object' && !Array.isArray(chunk.categories)) {
    const out = {};
    for (const [k, v] of Object.entries(chunk.categories)) {
      const items = (Array.isArray(v) ? v : [v]).map(String).map((s) => s.trim()).filter(Boolean);
      if (items.length) out[k.trim()] = items;
    }
    if (Object.keys(out).length) return out;
  }

  const names = (chunk.categoryNames ?? []).map((n) => String(n).trim()).filter(Boolean);
  const members = chunk.categoryMembers ?? [];
  const out = {};
  for (const name of names) out[name] = [];

  for (const raw of members) {
    const entry = String(raw);
    const idx = entry.indexOf(':');
    if (idx < 0) continue;
    const cat = entry.slice(0, idx).trim();
    const value = entry.slice(idx + 1).trim();
    if (!value) continue;
    // Match the declared category case-insensitively, and accept one the model
    // invented — a real category with real members is still teachable.
    const known = Object.keys(out).find((k) => k.toLowerCase() === cat.toLowerCase());
    if (known) out[known].push(value);
    else out[cat] = [value];
  }

  for (const k of Object.keys(out)) if (out[k].length === 0) delete out[k];
  return out;
}

/**
 * Convert one raw chunk into the client's wire format, or null if it can't be
 * trusted. Mirrors the invariants the Kotlin Exercise types enforce in `init`,
 * so a chunk that survives here cannot crash the router downstream.
 */
function cleanChunk(raw) {
  const concept = (raw.concept ?? '').trim();
  if (!concept) return null;

  switch (raw.shape) {
    case 'definition': {
      const content = (raw.content ?? '').trim();
      if (!content) return null;
      // The client blanks the term inside its own definition to build a
      // tap-to-fill. If the term isn't there, that degrades to multiple choice —
      // still fine, but worth keeping the chunk.
      const distractors = (raw.distractors ?? [])
        .map((d) => d.trim())
        .filter((d) => d && d.toLowerCase() !== content.toLowerCase())
        .slice(0, 3);
      return { shape: 'definition', concept, content, distractors };
    }

    case 'sequence': {
      const steps = (raw.steps ?? []).map((s) => s.trim()).filter(Boolean);
      if (steps.length < 3) return null;
      return { shape: 'sequence', concept, steps: steps.slice(0, 6) };
    }

    case 'taxonomy': {
      const categories = parseCategories(raw);
      const names = Object.keys(categories);
      const total = names.reduce((n, k) => n + categories[k].length, 0);
      // SortBuckets needs 2-3 buckets and 4-8 items; anything else can't render.
      if (names.length < 2 || total < 4) return null;
      return { shape: 'taxonomy', concept, categories };
    }

    case 'comparison': {
      // Same tolerance as taxonomy: accept the native `items` map (text -> is-left)
      // if the model produced one instead of the two flattened arrays.
      let left = (raw.leftItems ?? []).map((s) => String(s).trim()).filter(Boolean);
      let right = (raw.rightItems ?? []).map((s) => String(s).trim()).filter(Boolean);

      if (!left.length && !right.length && raw.items && typeof raw.items === 'object') {
        for (const [text, isLeft] of Object.entries(raw.items)) {
          if (!text.trim()) continue;
          (isLeft ? left : right).push(text.trim());
        }
      }

      const leftLabel = (raw.leftLabel ?? '').trim();
      const rightLabel = (raw.rightLabel ?? '').trim();
      if (!leftLabel || !rightLabel || left.length < 2 || right.length < 2) return null;
      if (leftLabel.toLowerCase() === rightLabel.toLowerCase()) return null;

      const items = {};
      for (const l of left.slice(0, 4)) items[l] = true;
      for (const r of right.slice(0, 4)) if (!(r in items)) items[r] = false;
      if (Object.keys(items).length < 4) return null;
      return { shape: 'comparison', concept, leftLabel, rightLabel, items };
    }

    case 'fact': {
      const question = (raw.question ?? '').trim();
      const answer = (raw.answer ?? '').trim();
      if (!question || !answer) return null;
      const distractors = (raw.distractors ?? [])
        .map((d) => d.trim())
        .filter((d) => d && d.toLowerCase() !== answer.toLowerCase())
        .slice(0, 3);
      // No honest wrong answers means no multiple choice. The router degrades
      // this to tap-to-fill rather than us inventing a falsehood here.
      return { shape: 'fact', concept, question, answer, distractors };
    }

    case 'concept': {
      const content = (raw.content ?? '').trim();
      if (!content) return null;
      const keyPoints = (raw.keyPoints ?? []).map((s) => s.trim()).filter(Boolean).slice(0, 4);
      return { shape: 'concept', concept, content, keyPoints };
    }

    default:
      return null;
  }
}

/**
 * Returns a clean LessonChunks, or null if the lesson isn't salvageable.
 *
 * A lesson needs one concept chunk (the teach-back and the intro both come from
 * it) and enough variety that the router can hit three distinct exercise types.
 * Below that it reads as a quiz generator, which is exactly what Curio isn't.
 */
export function validateLesson(raw, fallbackTitle) {
  if (!raw || !Array.isArray(raw.chunks)) return null;

  const cleaned = raw.chunks.map(cleanChunk).filter(Boolean);

  const concepts = cleaned.filter((c) => c.shape === 'concept');
  if (concepts.length === 0) return null;
  // Exactly one: the client uses it for both the lesson intro and the teach-back.
  const single = concepts.reduce((a, b) => (b.keyPoints.length > a.keyPoints.length ? b : a));
  const rest = cleaned.filter((c) => c.shape !== 'concept');

  // Three, not four. The router needs enough material to hit three distinct
  // exercise types once the TeachBack is added; three chunks across two shapes
  // clears that. Demanding four threw away otherwise good lessons that lost a
  // single chunk to cleaning — which is exactly what was happening.
  const distinctShapes = new Set(rest.map((c) => c.shape));
  if (rest.length < 3 || distinctShapes.size < 2) return null;

  return {
    lesson: (raw.lesson ?? fallbackTitle).trim() || fallbackTitle,
    objective: (raw.objective ?? '').trim(),
    chunks: [...rest, single],
  };
}
