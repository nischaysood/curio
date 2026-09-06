import { neon } from '@neondatabase/serverless';

/**
 * Neon over HTTP.
 *
 * The `neon()` driver speaks Postgres over HTTPS rather than TCP, which is the
 * only thing that works inside a Worker — there are no long-lived sockets at the
 * edge, and a connection pool has nothing to pool when every request may land in
 * a different city.
 *
 * One round trip per query, no transactions across queries. Where that matters
 * (signup, usage counting) the work is pushed into a single statement instead.
 */

/** Cached per isolate. Constructing the client is cheap but not free. */
let cached = null;
let cachedUrl = null;

export function db(env) {
  if (!env.DATABASE_URL) {
    // Same principle as a missing model key: say which name is missing, never
    // hint at the value.
    throw new Error('DATABASE_URL is not set. Run: wrangler secret put DATABASE_URL');
  }
  if (cached && cachedUrl === env.DATABASE_URL) return cached;
  cached = neon(env.DATABASE_URL);
  cachedUrl = env.DATABASE_URL;
  return cached;
}

/**
 * Whether accounts are available at all.
 *
 * No DATABASE_URL means the Worker still generates courses — it just can't
 * remember them. Generation is the product; sync is an enhancement. A missing
 * database should not take down the thing people actually came for.
 */
export const hasDb = (env) => Boolean(env.DATABASE_URL);
