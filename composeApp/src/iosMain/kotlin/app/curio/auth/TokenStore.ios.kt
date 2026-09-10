package app.curio.auth

import app.curio.platform.IosBridge

/**
 * iOS token storage — Keychain, via the Swift bridge.
 *
 * The Keychain is the right home for a session token: encrypted at rest, gated
 * by the device passcode, and excluded from unencrypted backups. NSUserDefaults,
 * which this used to be, is none of those things.
 *
 * The Security framework is reachable from Kotlin/Native, but only through
 * hand-assembled CFDictionaries of pointer types — verbose, easy to get subtly
 * wrong, and hard to read six months later. Thirty lines of Swift does the same
 * job legibly, so that's where it lives. See [IosBridge].
 *
 * Falls back to in-memory when Swift hasn't registered a store — which happens
 * in tests and previews. Degrades to "log in again", never to a crash.
 */
private const val KEY_TOKEN = "session_token"
private const val KEY_USER_ID = "session_user_id"

private val fallback = InMemoryTokenStore()

actual fun tokenStore(): TokenStore =
    IosBridge.secureStore?.let(::KeychainTokenStore) ?: fallback

private class KeychainTokenStore(
    private val store: app.curio.platform.SecureStore,
) : TokenStore {

    override fun read(): Session? {
        val token = store.get(KEY_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        return Session(token = token, userId = store.get(KEY_USER_ID).orEmpty())
    }

    override fun write(session: Session) {
        store.set(KEY_TOKEN, session.token)
        store.set(KEY_USER_ID, session.userId)
    }

    override fun clear() {
        store.remove(KEY_TOKEN)
        store.remove(KEY_USER_ID)
    }
}
