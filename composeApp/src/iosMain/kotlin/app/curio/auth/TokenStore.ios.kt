package app.curio.auth

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/**
 * iOS token storage.
 *
 * NSUserDefaults for now, not Keychain. Keychain is the correct home for a
 * credential and this should move there before the App Store build — the
 * cinterop for SecItemAdd/SecItemCopyMatching is fiddly and not worth blocking
 * the Android release on.
 *
 * The practical difference: NSUserDefaults is cleared when the app is deleted
 * (Keychain survives), and it's included in unencrypted backups. For a
 * revocable 90-day session token that's a real but small exposure.
 *
 * TODO(ios): move to Keychain before submitting to App Store review.
 */
private const val KEY_TOKEN = "curio.session_token"
private const val KEY_USER_ID = "curio.session_user_id"

@OptIn(ExperimentalForeignApi::class)
actual fun tokenStore(): TokenStore = IosTokenStore()

private class IosTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): Session? {
        val token = defaults.stringForKey(KEY_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        return Session(token = token, userId = defaults.stringForKey(KEY_USER_ID).orEmpty())
    }

    override fun write(session: Session) {
        defaults.setObject(session.token, KEY_TOKEN)
        defaults.setObject(session.userId, KEY_USER_ID)
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_TOKEN)
        defaults.removeObjectForKey(KEY_USER_ID)
    }
}
