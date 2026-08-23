/**
 * The only place in Curio that knows which model vendor we're using.
 *
 * Everything upstream calls `generate(env, { prompt, schema, temperature })` and
 * gets back a parsed object matching `schema`. Swapping vendor is a secret and
 * an env var, never a refactor — which is what made moving off Gemini a
 * two-hour job instead of a two-day one.
 *
 * MODEL NAMES ROT. Both vendors retire models with no warning and return a 404
 * naming the replacement. Both are overridable by env var precisely so that
 * fixing it is `wrangler secret put`, not a code change and redeploy.
 */

const DEFAULTS = {
  gemini: 'gemini-3.6-flash',
  // Groq deprecates aggressively — every Llama chat model was gone by Aug 2026.
  // If this 404s, list what's actually live with:
  //   curl https://api.groq.com/openai/v1/models -H "Authorization: Bearer $KEY"
  // then set GROQ_MODEL rather than editing this file.
  //
  // gpt-oss-120b over the smaller sibling because course content is the one
  // thing worth spending quality on, and over qwen3.6 because a reasoning model
  // burns tokens thinking when all we need is well-formed content. Drop to
  // openai/gpt-oss-20b if the tokens-per-minute limit starts biting.
  groq: 'openai/gpt-oss-120b',
};

export function providerName(env) {
  if (env.PROVIDER) return env.PROVIDER;
  // Whichever key exists. Groq first: it's the free one, so a project with both
  // keys shouldn't quietly spend money.
  if (env.GROQ_API_KEY) return 'groq';
  if (env.GEMINI_API_KEY) return 'gemini';
  throw new Error('no provider key configured');
}

export async function generate(env, opts) {
  const name = providerName(env);
  return name === 'groq' ? groq(env, opts) : gemini(env, opts);
}

// ---------------------------------------------------------------------------
// Schema translation
// ---------------------------------------------------------------------------

/**
 * Gemini uses uppercase type names; JSON Schema (and therefore Groq, OpenAI and
 * everyone else) uses lowercase. Rather than maintain two copies of every
 * schema — which would drift the first time someone edited one — translate.
 *
 * `strict: true` additionally requires every property to be listed in `required`
 * and `additionalProperties: false`, so optional fields must be nullable
 * instead. That's why this walks the whole tree rather than lowercasing strings.
 */
export function toJsonSchema(node, opts = { strict: false }) {
  if (Array.isArray(node)) return node.map((n) => toJsonSchema(n, opts));
  if (!node || typeof node !== 'object') return node;

  const out = {};
  for (const [key, value] of Object.entries(node)) {
    if (key === 'type' && typeof value === 'string') {
      out.type = value.toLowerCase();
    } else if (key === 'properties') {
      out.properties = Object.fromEntries(
        Object.entries(value).map(([k, v]) => [k, toJsonSchema(v, opts)]),
      );
    } else if (key === 'items') {
      out.items = toJsonSchema(value, opts);
    } else {
      out[key] = value;
    }
  }

  if (opts.strict && out.type === 'object' && out.properties) {
    // Only under strict, and we don't use strict — see the note at the call
    // site. Kept because a future provider may require it, and because the
    // translation is the interesting part regardless.
    out.required = Object.keys(out.properties);
    out.additionalProperties = false;
  }
  return out;
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

/**
 * Structured output should make this unnecessary. It is here because "should"
 * has failed us twice already today, and a course lost to a stray ``` fence is
 * a course the user waited a minute for and didn't get.
 */
export function parseModelJson(text) {
  if (typeof text !== 'string' || !text.trim()) throw new Error('empty model response');

  try {
    return JSON.parse(text);
  } catch {
    // Fall through to repair.
  }

  // Markdown fences: ```json { ... } ```
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (fenced) {
    try {
      return JSON.parse(fenced[1]);
    } catch {
      // Keep going.
    }
  }

  // Prose before or after the object: find the outermost {...} by brace depth.
  // Not a regex — nested braces inside strings make that unreliable.
  const start = text.indexOf('{');
  if (start >= 0) {
    let depth = 0;
    let inString = false;
    let escaped = false;
    for (let i = start; i < text.length; i++) {
      const c = text[i];
      if (escaped) { escaped = false; continue; }
      if (c === '\\') { escaped = true; continue; }
      if (c === '"') { inString = !inString; continue; }
      if (inString) continue;
      if (c === '{') depth++;
      else if (c === '}' && --depth === 0) {
        return JSON.parse(text.slice(start, i + 1));
      }
    }
  }

  throw new Error(`could not parse model output: ${text.slice(0, 200)}`);
}

// ---------------------------------------------------------------------------
// Vendors
// ---------------------------------------------------------------------------

async function gemini(env, { prompt, schema, temperature }) {
  const model = env.GEMINI_MODEL || DEFAULTS.gemini;

  const res = await withRetry(() =>
    fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            temperature,
            responseMimeType: 'application/json',
            responseSchema: schema,
          },
        }),
      },
    ),
  `gemini(${model})`);

  const body = await res.json();
  return parseModelJson(body?.candidates?.[0]?.content?.parts?.[0]?.text);
}

async function groq(env, { prompt, schema, temperature }) {
  const model = env.GROQ_MODEL || DEFAULTS.groq;

  const res = await withRetry(() =>
    fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        authorization: `Bearer ${env.GROQ_API_KEY}`,
      },
      body: JSON.stringify({
        model,
        temperature,
        messages: [{ role: 'user', content: prompt }],
        // strict: false, deliberately.
        //
        // Strict mode requires EVERY property on EVERY object. Our chunk schema
        // is effectively a union — a `definition` carries `content` and
        // `distractors`, a `sequence` carries `steps`, and neither has any use
        // for the other's fields. Under strict the model must emit all fourteen
        // properties on every chunk, so it pads with empty junk and the content
        // gets worse. Non-strict still steers the shape; validate.js remains the
        // actual guarantee, and it drops anything malformed rather than
        // repairing it.
        response_format: {
          type: 'json_schema',
          json_schema: { name: 'curio', strict: false, schema: toJsonSchema(schema, { strict: false }) },
        },
      }),
    }),
  `groq(${model})`);

  const body = await res.json();
  return parseModelJson(body?.choices?.[0]?.message?.content);
}

// ---------------------------------------------------------------------------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Shared retry. Distinguishes burst limits (worth waiting out) from hard quota
 * and auth failures (never clearing, so failing fast is kinder than a minute of
 * silence followed by the same error).
 */
async function withRetry(send, label) {
  let res;
  for (let attempt = 0; attempt < 4; attempt++) {
    res = await send();
    if (res.ok) return res;

    if (res.status !== 429 && res.status < 500) break;

    if (res.status === 429) {
      const body = (await res.clone().text()).toLowerCase();
      const hardQuota = body.includes('per day') || body.includes('daily') ||
        body.includes('billing') || body.includes('quota exceeded for quota metric');
      if (hardQuota) break;
    }

    if (attempt < 3) {
      const retryAfter = Number(res.headers.get('retry-after'));
      await sleep(
        Number.isFinite(retryAfter) && retryAfter > 0
          ? Math.min(retryAfter * 1000, 45000)
          : [4000, 15000, 30000][attempt],
      );
    }
  }

  // 900 chars because the quota METRIC name is the part that tells you whether
  // to wait or to go fix your billing, and it sits near the end of the message.
  throw new Error(`${label} ${res.status}: ${(await res.text()).slice(0, 900)}`);
}
