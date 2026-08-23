import { test } from 'node:test';
import assert from 'node:assert/strict';
import { toJsonSchema, parseModelJson, providerName } from '../src/provider.js';
import { CHUNKS_SCHEMA, OUTLINE_SCHEMA } from '../src/schema.js';

/**
 * Two new failure surfaces came with the provider swap: schema translation
 * (Gemini's uppercase types vs JSON Schema's lowercase) and parsing output that
 * structured mode was supposed to guarantee. Both fail silently and expensively
 * — the user waits a minute and gets nothing — so both are tested.
 */

// --- schema translation ----------------------------------------------------

test('type names are lowercased for JSON Schema', () => {
  const out = toJsonSchema(OUTLINE_SCHEMA);
  assert.equal(out.type, 'object');
  assert.equal(out.properties.lessons.type, 'array');
  assert.equal(out.properties.lessons.items.type, 'object');
  assert.equal(out.properties.lessons.items.properties.title.type, 'string');
});

test('strict mode, when asked for, marks every property required at every level', () => {
  // Not what we send — see the next test — but the translation must be correct
  // for the day a provider demands it.
  const out = toJsonSchema(CHUNKS_SCHEMA, { strict: true });

  assert.equal(out.additionalProperties, false);
  assert.deepEqual(out.required.sort(), ['chunks', 'lesson', 'objective']);

  const chunk = out.properties.chunks.items;
  assert.equal(chunk.additionalProperties, false);
  assert.equal(chunk.required.length, Object.keys(chunk.properties).length);
});

test('by default a chunk keeps only its genuinely required fields', () => {
  // The chunk schema is a union: a `definition` has no use for `steps`, and a
  // `sequence` has no use for `distractors`. Forcing all fourteen properties
  // onto every chunk makes the model pad with empty junk and degrades the
  // content — which is exactly what broke the first Groq run.
  const chunk = toJsonSchema(CHUNKS_SCHEMA).properties.chunks.items;

  assert.deepEqual(chunk.required, ['shape', 'concept']);
  assert.equal(chunk.additionalProperties, undefined);
  // The properties are all still DESCRIBED, so the model knows they exist.
  assert.ok(Object.keys(chunk.properties).length > 10);
});

test('enums survive translation', () => {
  // The shape enum is what makes an invalid pedagogical shape impossible.
  // Losing it in translation would silently remove the guarantee.
  const chunk = toJsonSchema(CHUNKS_SCHEMA).properties.chunks.items;
  assert.deepEqual(chunk.properties.shape.enum, [
    'definition', 'sequence', 'taxonomy', 'comparison', 'fact', 'concept',
  ]);
});

test('translation does not mutate the original schema', () => {
  // The Gemini path uses these objects untranslated. Mutating in place would
  // break whichever provider ran second.
  const before = JSON.stringify(CHUNKS_SCHEMA);
  toJsonSchema(CHUNKS_SCHEMA);
  assert.equal(JSON.stringify(CHUNKS_SCHEMA), before);
});

// --- parsing ---------------------------------------------------------------

test('clean JSON parses', () => {
  assert.deepEqual(parseModelJson('{"a":1}'), { a: 1 });
});

test('markdown fences are stripped', () => {
  assert.deepEqual(parseModelJson('```json\n{"a":1}\n```'), { a: 1 });
  assert.deepEqual(parseModelJson('```\n{"a":1}\n```'), { a: 1 });
});

test('prose around the object is discarded', () => {
  assert.deepEqual(
    parseModelJson('Sure! Here is the lesson:\n{"a":1}\nHope that helps.'),
    { a: 1 },
  );
});

test('nested braces and braces inside strings do not confuse the extractor', () => {
  // A regex-based extractor gets this wrong, which is why it counts depth and
  // tracks string state instead.
  const text = 'preamble {"a":{"b":[1,2]},"c":"a } brace in a string"} trailing';
  assert.deepEqual(parseModelJson(text), { a: { b: [1, 2] }, c: 'a } brace in a string' });
});

test('escaped quotes inside strings do not end the string early', () => {
  const text = '{"a":"he said \\"hi\\" }","b":2}';
  assert.deepEqual(parseModelJson(text), { a: 'he said "hi" }', b: 2 });
});

test('unparseable output throws rather than returning something wrong', () => {
  // Returning a half-parsed object would put fabricated content in front of a
  // learner. Failing is the correct outcome.
  assert.throws(() => parseModelJson('I cannot help with that.'));
  assert.throws(() => parseModelJson(''));
  assert.throws(() => parseModelJson(null));
  assert.throws(() => parseModelJson('{"unterminated": '));
});

// --- provider selection ----------------------------------------------------

test('provider is chosen by key, and PROVIDER overrides', () => {
  assert.equal(providerName({ GROQ_API_KEY: 'x' }), 'groq');
  assert.equal(providerName({ GEMINI_API_KEY: 'x' }), 'gemini');
  assert.equal(providerName({ GROQ_API_KEY: 'x', GEMINI_API_KEY: 'y' }), 'groq');
  assert.equal(providerName({ PROVIDER: 'gemini', GROQ_API_KEY: 'x' }), 'gemini');
  assert.throws(() => providerName({}));
});
