/**
 * PORTED VERBATIM from domain/Course.kt. Do not "improve" it.
 *
 * The cache key is the whole business model: a course on Big-O is identical for
 * every user, so the first person pays for generation and the next thousand are
 * free. If this function disagrees with the Kotlin one by a single bit, the cache
 * NEVER hits, every request costs money, and nothing visibly breaks — you'd only
 * notice on the bill. There's a parity test in test/hash.test.js.
 */

/**
 * `+` and `#` survive on purpose. Stripping them collapses "c++" into "c" and
 * "c#" into "c", which doesn't just miss the cache — it serves the WRONG course.
 */
export function normaliseTopic(raw) {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9+# ]/g, ' ')
    .replace(/ +/g, ' ')
    .trim();
}

const FNV_OFFSET = 0xcbf29ce484222325n;
const FNV_PRIME = 0x100000001b3n;
const MASK64 = 0xffffffffffffffffn;

export function topicHash(topic, depth) {
  const input = normaliseTopic(topic) + ':' + depth.toLowerCase();
  const bytes = new TextEncoder().encode(input);

  let h = FNV_OFFSET;
  for (const b of bytes) {
    h ^= BigInt(b);
    // JS BigInt is arbitrary precision; Kotlin's Long wraps at 64 bits. Mask
    // after every multiply or the two diverge on the second character.
    h = (h * FNV_PRIME) & MASK64;
  }
  return h.toString(16).padStart(16, '0');
}
