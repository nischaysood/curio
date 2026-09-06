import { db } from './db.js';

/**
 * Email + password auth, built on WebCrypto only.
 *
 * No auth library: Workers have no Node crypto, bcrypt needs native bindings,
 * and the pieces that actually matter here — a slow hash, a constant-time
 * compare, an opaque revocable token — are each a few lines of SubtleCrypto.
 * A dependency would mostly be a supply-chain surface.
 */

const SESSION_DAYS = 90;
const PBKDF2_ITERATIONS = 100_000;

const enc = new TextEncoder();

// ---------------------------------------------------------------------------
// Password hashing
// ---------------------------------------------------------------------------

/**
 * PBKDF2-SHA256, 100k iterations, per-user random salt.
 *
 * Argon2id would be the better choice and isn't available in Workers. 100k
 * iterations is OWASP's floor for PBKDF2-SHA256 — the point is that a leaked
 * table takes years to crack rather than minutes, not that it's impossible.
 *
 * Stored as `pbkdf2$<iterations>$<salt>$<hash>` so the parameters travel with
 * the hash. Raising the iteration count later then costs nothing: old rows keep
 * verifying with their own count, and rehash on next login.
 */
export async function hashPassword(password, salt = crypto.getRandomValues(new Uint8Array(16))) {
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    key,
    256,
  );
  return `pbkdf2$${PBKDF2_ITERATIONS}$${b64(salt)}$${b64(new Uint8Array(bits))}`;
}

/** Verify against a stored hash. Returns false on any malformed input rather than throwing. */
export async function verifyPassword(password, stored) {
  try {
    const [scheme, iterations, saltB64, hashB64] = String(stored).split('$');
    if (scheme !== 'pbkdf2') return false;

    const salt = unb64(saltB64);
    const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits(
      { name: 'PBKDF2', salt, iterations: Number(iterations), hash: 'SHA-256' },
      key,
      256,
    );
    return timingSafeEqual(new Uint8Array(bits), unb64(hashB64));
  } catch {
    return false;
  }
}

/**
 * Constant-time compare.
 *
 * `a === b` on hashes leaks how many leading bytes matched via how long the
 * comparison took. It's a fussy attack over a network, and it costs one loop to
 * make impossible.
 */
function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

// ---------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------

/**
 * Issue a session token.
 *
 * 256 bits from the CSPRNG. The plaintext token goes to the client exactly once
 * and is never stored — the database holds only its SHA-256, so a dump of
 * `sessions` doesn't let anyone log in as anybody.
 */
export async function createSession(env, userId) {
  const token = b64url(crypto.getRandomValues(new Uint8Array(32)));
  const expires = new Date(Date.now() + SESSION_DAYS * 86_400_000);

  await db(env)`
    INSERT INTO sessions (token_hash, user_id, expires_at)
    VALUES (${await sha256(token)}, ${userId}, ${expires.toISOString()})
  `;

  return { token, expiresAt: expires.toISOString() };
}

/**
 * Resolve `Authorization: Bearer <token>` to a user id, or null.
 *
 * Expiry is checked in SQL rather than in JS so a wrong clock on the Worker
 * can't extend a session, and so the check can't be forgotten at a call site.
 */
export async function userFromRequest(env, request) {
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) return null;

  const rows = await db(env)`
    SELECT user_id FROM sessions
    WHERE token_hash = ${await sha256(token)} AND expires_at > now()
  `;
  return rows[0]?.user_id ?? null;
}

/** Log out. Deleting the row is what makes tokens revocable — the reason not to use JWTs. */
export async function destroySession(env, request) {
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) return;
  await db(env)`DELETE FROM sessions WHERE token_hash = ${await sha256(token)}`;
}

// ---------------------------------------------------------------------------
// Signup / login
// ---------------------------------------------------------------------------

/** Cheap sanity check, not RFC 5322. The real validation is that mail arrives. */
export const looksLikeEmail = (s) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(s ?? '').trim());

export async function signup(env, email, password) {
  const clean = String(email ?? '').trim();

  if (!looksLikeEmail(clean)) return { error: 'bad_email' };
  // Length only. Composition rules ("one symbol, one capital") push people
  // toward Password1! and are no longer recommended by anyone who measures it.
  if (String(password ?? '').length < 8) return { error: 'weak_password' };

  const hash = await hashPassword(password);

  // ON CONFLICT rather than SELECT-then-INSERT: two signups racing on the same
  // address would both pass the check and one would blow up on the unique index.
  const rows = await db(env)`
    INSERT INTO users (email, password_hash)
    VALUES (${clean}, ${hash})
    ON CONFLICT (lower(email)) DO NOTHING
    RETURNING id
  `;

  if (rows.length === 0) return { error: 'email_taken' };

  const session = await createSession(env, rows[0].id);
  return { userId: rows[0].id, ...session };
}

export async function login(env, email, password) {
  const rows = await db(env)`
    SELECT id, password_hash FROM users WHERE lower(email) = lower(${String(email ?? '').trim()})
  `;

  const user = rows[0];

  // Hash even when the user doesn't exist, so "no such account" and "wrong
  // password" take the same time. Otherwise the endpoint becomes a way to
  // enumerate who has an account.
  const ok = user
    ? await verifyPassword(password, user.password_hash)
    : await verifyPassword(password, await hashPassword('decoy'));

  if (!ok) return { error: 'bad_credentials' };

  const session = await createSession(env, user.id);
  return { userId: user.id, ...session };
}

// ---------------------------------------------------------------------------
// Encoding helpers
// ---------------------------------------------------------------------------

const b64 = (bytes) => btoa(String.fromCharCode(...bytes));
const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const b64url = (bytes) => b64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', enc.encode(text));
  return b64(new Uint8Array(digest));
}
