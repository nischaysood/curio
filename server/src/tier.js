/**
 * Which tier is this user entitled to?
 *
 * The obvious shortcut is to let the app send `X-Curio-Tier: PREMIUM` and
 * believe it. That's a header anyone can type, and what it unlocks costs real
 * money per call — so the answer comes from RevenueCat, not from the client.
 *
 * The id used here is the Curio user id, taken from the session token rather
 * than from the request body. The app calls `Purchases.logIn(userId)` after
 * sign-in, which makes the RevenueCat customer and the Curio account the same
 * identity — so this lookup needs nothing the client could forge.
 */

const ENTITLEMENT = 'premium'; // Must match Billing.android.kt and the dashboard.
const CACHE_SECONDS = 300;

/**
 * Resolve a tier. Never throws and never fails open.
 *
 * Every error path returns FREE: if RevenueCat is unreachable, a paying user is
 * briefly rate-limited like a free one (annoying, recoverable, they'll retry)
 * whereas failing open means an outage at RevenueCat becomes an unmetered bill
 * for us. Wrong in the cheap direction.
 */
export async function tierOf(userId, env) {
  if (!userId || !env.REVENUECAT_SECRET_KEY) return 'FREE';

  try {
    const url = `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(userId)}`;
    const res = await fetch(url, {
      headers: { authorization: `Bearer ${env.REVENUECAT_SECRET_KEY}` },
      // RevenueCat's own answer changes rarely; caching it at the edge keeps a
      // third-party call off the hot path of every generation.
      cf: { cacheTtl: CACHE_SECONDS, cacheEverything: true },
    });

    if (!res.ok) {
      console.error(`revenuecat lookup failed: ${res.status}`);
      return 'FREE';
    }

    const body = await res.json();
    const ent = body?.subscriber?.entitlements?.[ENTITLEMENT];
    if (!ent) return 'FREE';

    // `expires_date` is null for lifetime purchases, which are not expired.
    const active = ent.expires_date === null || new Date(ent.expires_date) > new Date();
    return active ? 'PREMIUM' : 'FREE';
  } catch (err) {
    console.error(`revenuecat lookup threw: ${err.message || err}`);
    return 'FREE';
  }
}
