/**
 * Gemini response schemas.
 *
 * This is the reason Gemini is the primary provider. Schema-constrained output
 * makes invalid JSON structurally impossible rather than merely unlikely, which
 * removes the single largest class of runtime failure in the whole pipeline —
 * the model cannot invent a shape, misspell an enum, or return prose.
 *
 * Note what these schemas do NOT contain: exercises. The model returns content
 * tagged with a pedagogical shape; the router (Kotlin, on device) decides which
 * exercise teaches it. The model does knowledge, code does structure.
 */

const TYPE = { STRING: 'STRING', ARRAY: 'ARRAY', OBJECT: 'OBJECT' };

/** Stage 1 — the outline. Cheap and fast; drives the assembly animation. */
export const OUTLINE_SCHEMA = {
  type: TYPE.OBJECT,
  properties: {
    lessons: {
      type: TYPE.ARRAY,
      items: {
        type: TYPE.OBJECT,
        properties: {
          title: { type: TYPE.STRING },
          objective: { type: TYPE.STRING },
        },
        required: ['title', 'objective'],
      },
    },
  },
  required: ['lessons'],
};

/**
 * Stage 2 — one lesson's content chunks.
 *
 * Gemini's schema support has no discriminated unions, so every shape's fields
 * live on one object with `shape` enum-locked. The optional fields are validated
 * per-shape in validate.js — the schema guarantees the envelope, our code
 * guarantees the contents.
 */
export const CHUNKS_SCHEMA = {
  type: TYPE.OBJECT,
  properties: {
    lesson: { type: TYPE.STRING },
    objective: { type: TYPE.STRING },
    chunks: {
      type: TYPE.ARRAY,
      items: {
        type: TYPE.OBJECT,
        properties: {
          shape: {
            type: TYPE.STRING,
            enum: ['definition', 'sequence', 'taxonomy', 'comparison', 'fact', 'concept'],
          },
          concept: { type: TYPE.STRING },
          content: { type: TYPE.STRING },
          distractors: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          steps: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          question: { type: TYPE.STRING },
          answer: { type: TYPE.STRING },
          keyPoints: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          leftLabel: { type: TYPE.STRING },
          rightLabel: { type: TYPE.STRING },
          categoryNames: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          categoryMembers: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          leftItems: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
          rightItems: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
        },
        required: ['shape', 'concept'],
      },
    },
  },
  required: ['lesson', 'objective', 'chunks'],
};

/** Teach-back grading. Kept tiny — this one runs at answer time and latency shows. */
export const GRADE_SCHEMA = {
  type: TYPE.OBJECT,
  properties: {
    correct: { type: 'BOOLEAN' },
    feedback: { type: TYPE.STRING },
    missed: { type: TYPE.ARRAY, items: { type: TYPE.STRING } },
  },
  required: ['correct', 'feedback'],
};
