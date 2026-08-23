import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateLesson } from '../src/validate.js';
import { topicHash, normaliseTopic } from '../src/hash.js';

/**
 * Validation is the last line before content reaches a learner, and a cached
 * course serves thousands of people. These tests exist because a bad chunk that
 * slips through isn't one bad experience — it's thousands of them.
 */

const concept = {
  shape: 'concept',
  concept: 'lexical analysis',
  content: 'Lexical analysis converts characters into tokens.',
  keyPoints: ['input is characters', 'output is tokens', 'discards whitespace'],
};

const goodChunks = [
  { shape: 'definition', concept: 'token', content: 'A token is the smallest unit.', distractors: ['wrong one', 'wrong two'] },
  { shape: 'sequence', concept: 'tokenizing', steps: ['read', 'classify', 'emit'] },
  { shape: 'fact', concept: 'whitespace', question: 'What happens to whitespace?', answer: 'Discarded', distractors: ['Kept', 'Doubled', 'Escaped'] },
  { shape: 'definition', concept: 'lexer', content: 'A lexer scans characters.', distractors: ['wrong'] },
  concept,
];

const lesson = (chunks) => ({ lesson: 'Lexical Analysis', objective: 'Tokenize source', chunks });

test('a well-formed lesson passes', () => {
  const out = validateLesson(lesson(goodChunks), 'Fallback');
  assert.ok(out);
  assert.equal(out.lesson, 'Lexical Analysis');
  assert.equal(out.chunks.filter((c) => c.shape === 'concept').length, 1);
});

test('the concept chunk is always last, so it can be the teach-back', () => {
  const out = validateLesson(lesson(goodChunks), 'Fallback');
  assert.equal(out.chunks[out.chunks.length - 1].shape, 'concept');
});

test('a lesson with no concept chunk is rejected', () => {
  // Both the intro and the teach-back come from it. Without one there is no lesson.
  const out = validateLesson(lesson(goodChunks.filter((c) => c.shape !== 'concept')), 'F');
  assert.equal(out, null);
});

test('multiple concept chunks collapse to the richest one', () => {
  const thin = { shape: 'concept', concept: 'thin', content: 'Short.', keyPoints: [] };
  const out = validateLesson(lesson([...goodChunks, thin]), 'F');
  assert.equal(out.chunks.filter((c) => c.shape === 'concept').length, 1);
  assert.equal(out.chunks[out.chunks.length - 1].concept, 'lexical analysis');
});

test('a lesson with too little variety is rejected', () => {
  // Four facts and a concept reads as a quiz generator, which is the one thing
  // Curio is explicitly not.
  const facts = Array.from({ length: 4 }, (_, i) => ({
    shape: 'fact', concept: `f${i}`, question: `q${i}?`, answer: `a${i}`, distractors: ['x', 'y', 'z'],
  }));
  assert.equal(validateLesson(lesson([...facts, concept]), 'F'), null);
});

test('a sequence with fewer than three steps is dropped', () => {
  const short = { shape: 'sequence', concept: 'two step', steps: ['one', 'two'] };
  const out = validateLesson(lesson([...goodChunks, short]), 'F');
  assert.ok(!out.chunks.some((c) => c.concept === 'two step'));
});

test('a distractor identical to the answer is removed, not kept', () => {
  const dupe = {
    shape: 'fact', concept: 'dupe', question: 'Q?', answer: 'Correct',
    distractors: ['correct', 'Wrong'],
  };
  const out = validateLesson(lesson([...goodChunks, dupe]), 'F');
  const fact = out.chunks.find((c) => c.concept === 'dupe');
  assert.deepEqual(fact.distractors, ['Wrong']);
});

test('a fact with no usable distractors survives with an empty array', () => {
  // The router degrades it to tap-to-fill. We never invent a falsehood to fill
  // a multiple-choice slot.
  const bare = { shape: 'fact', concept: 'bare', question: 'Q?', answer: 'A', distractors: [] };
  const out = validateLesson(lesson([...goodChunks, bare]), 'F');
  const fact = out.chunks.find((c) => c.concept === 'bare');
  assert.deepEqual(fact.distractors, []);
});

test('taxonomy category strings are parsed back into a map', () => {
  const tax = {
    shape: 'taxonomy', concept: 'token types',
    categoryNames: ['keyword', 'literal'],
    categoryMembers: ['keyword: if', 'keyword: while', 'literal: 42', 'literal: 3.14'],
  };
  const out = validateLesson(lesson([...goodChunks, tax]), 'F');
  const parsed = out.chunks.find((c) => c.shape === 'taxonomy');
  assert.deepEqual(parsed.categories, { keyword: ['if', 'while'], literal: ['42', '3.14'] });
});

test('a taxonomy with too few items is dropped', () => {
  const thin = {
    shape: 'taxonomy', concept: 'thin', categoryNames: ['a', 'b'],
    categoryMembers: ['a: one', 'b: two'],
  };
  const out = validateLesson(lesson([...goodChunks, thin]), 'F');
  assert.ok(!out.chunks.some((c) => c.concept === 'thin'));
});

test('a taxonomy member naming an undeclared category is still kept', () => {
  // Earlier this dropped anything not in categoryNames. Models routinely name a
  // category they forgot to declare, and a real category with real members is
  // teachable — throwing it away cost us whole lessons.
  const tax = {
    shape: 'taxonomy', concept: 'tt',
    categoryNames: ['keyword', 'literal'],
    categoryMembers: ['keyword: if', 'operator: +', 'literal: 42', 'literal: 7', 'keyword: while'],
  };
  const out = validateLesson(lesson([...goodChunks, tax]), 'F');
  const parsed = out.chunks.find((c) => c.shape === 'taxonomy');
  assert.deepEqual(parsed.categories.operator, ['+']);
  assert.deepEqual(parsed.categories.keyword, ['if', 'while']);
});

test('a taxonomy given as a native map is accepted', () => {
  // Schemas can't express arbitrary-keyed maps, so the prompt asks for flattened
  // arrays — but models emit the obvious shape anyway. Accept both.
  const tax = {
    shape: 'taxonomy', concept: 'tt',
    categories: { keyword: ['if', 'while'], literal: ['42', '3.14'] },
  };
  const out = validateLesson(lesson([...goodChunks, tax]), 'F');
  const parsed = out.chunks.find((c) => c.shape === 'taxonomy');
  assert.deepEqual(parsed.categories, { keyword: ['if', 'while'], literal: ['42', '3.14'] });
});

test('a comparison given as a native items map is accepted', () => {
  const cmp = {
    shape: 'comparison', concept: 'c', leftLabel: 'Left', rightLabel: 'Right',
    items: { a: true, b: true, c: false, d: false },
  };
  const out = validateLesson(lesson([...goodChunks, cmp]), 'F');
  const parsed = out.chunks.find((c) => c.shape === 'comparison');
  assert.equal(parsed.items.a, true);
  assert.equal(parsed.items.c, false);
});

test('a lesson surviving with three chunks across two shapes is kept', () => {
  // Cleaning can legitimately drop a chunk or two. Demanding four survivors
  // threw away lessons the router could handle perfectly well.
  const three = [
    { shape: 'definition', concept: 'a', content: 'An a is a thing.', distractors: ['no'] },
    { shape: 'sequence', concept: 'b', steps: ['one', 'two', 'three'] },
    { shape: 'fact', concept: 'c', question: 'Q?', answer: 'A', distractors: ['X'] },
    concept,
  ];
  assert.ok(validateLesson(lesson(three), 'F'));
});

test('a comparison with identical labels is dropped', () => {
  const bad = {
    shape: 'comparison', concept: 'c', leftLabel: 'Same', rightLabel: 'same',
    leftItems: ['a', 'b'], rightItems: ['c', 'd'],
  };
  const out = validateLesson(lesson([...goodChunks, bad]), 'F');
  assert.ok(!out.chunks.some((c) => c.shape === 'comparison'));
});

test('an unknown shape is dropped rather than passed through', () => {
  const alien = { shape: 'diagram', concept: 'x', content: 'y' };
  const out = validateLesson(lesson([...goodChunks, alien]), 'F');
  assert.ok(!out.chunks.some((c) => c.shape === 'diagram'));
});

test('malformed input returns null instead of throwing', () => {
  assert.equal(validateLesson(null, 'F'), null);
  assert.equal(validateLesson({}, 'F'), null);
  assert.equal(validateLesson({ chunks: 'not an array' }, 'F'), null);
});

// --- cache key -------------------------------------------------------------

test('topicHash matches the Kotlin implementation exactly', () => {
  // Golden values verified against domain/Course.kt. If these change, the cache
  // silently stops hitting and every request starts costing money.
  assert.equal(topicHash('How Compilers Work', 'STANDARD'), '57c1c860bc844e43');
  assert.equal(topicHash('big o notation', 'STANDARD'), '3ce29709a3b58d91');
  assert.equal(topicHash('big o notation', 'QUICK'), 'a41d8b82f6fe61a3');
  assert.equal(topicHash('c programming', 'STANDARD'), '0d9970e3a1f5391e');
  assert.equal(topicHash('c++ programming', 'STANDARD'), 'f929f030c512e700');
});

test('c and c++ are different cache entries', () => {
  // Not a near-miss: collapsing these serves C programmers a C++ course.
  assert.notEqual(normaliseTopic('c programming'), normaliseTopic('c++ programming'));
});

test('casing, spacing and punctuation collapse to one key', () => {
  const canonical = topicHash('how compilers work', 'STANDARD');
  for (const v of ['How Compilers Work', '  HOW  COMPILERS   WORK ', 'How compilers work!']) {
    assert.equal(topicHash(v, 'STANDARD'), canonical);
  }
});
