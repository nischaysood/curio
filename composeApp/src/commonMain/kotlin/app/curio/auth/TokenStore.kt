package app.curio.auth

/**
 * Where the session token lives between launches.
 *
 * This is the only genuinely persistent thing in the app now that the database
 * is server-side. Losing it isn't dangerous — it just means logging in again —
 * but leaking it means someone else reads your account, so it goes in
 * platform-private storage rather than a plain file.
 *
 * Deliberately synchronous. It's read once at startup before anything renders,
 * and a suspend function there buys nothing but a loading state.
 */
/**
 * A signed-in session.
 *
 * [userId] travels with the token because RevenueCat needs it: purchases are
 * bound to this id, which makes the Curio account and the RevenueCat customer
 * the same person. Without it a subscription belongs to a phone.
 */
data class Session(val token: String, val userId: String)

interface TokenStore {
    fun read(): Session?
    fun write(session: Session)
    fun clear()
}

/**
 * Platform-backed store: Android private SharedPreferences, iOS Keychain.
 */
expect fun tokenStore(): TokenStore

/** For tests and previews, where no platform storage exists. */
class InMemoryTokenStore(private var session: Session? = null) : TokenStore {
    override fun read(): Session? = session
    override fun write(session: Session) { this.session = session }
    override fun clear() { session = null }
}
