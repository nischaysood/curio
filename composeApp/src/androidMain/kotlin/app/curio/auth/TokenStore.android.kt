package app.curio.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * Android token storage.
 *
 * Private SharedPreferences, not EncryptedSharedPreferences. On a non-rooted
 * device, `MODE_PRIVATE` files are already unreadable by other apps — the
 * encrypted variant mainly protects against physical extraction from a rooted
 * or unlocked device, and it's a deprecated library carrying a Tink dependency.
 * For a 90-day session token that can be revoked server-side, that trade isn't
 * worth the dependency. Revisit if Curio ever stores anything more sensitive.
 */
private const val PREFS = "curio.auth"
private const val KEY_TOKEN = "session_token"
private const val KEY_USER_ID = "session_user_id"

/**
 * Application context, set once from MainActivity.
 *
 * A static Context is normally a leak, which is why this holds the APPLICATION
 * context — it lives as long as the process either way, so there's nothing to
 * leak. Never assign an Activity here.
 */
@SuppressLint("StaticFieldLeak")
internal var appContext: Context? = null

actual fun tokenStore(): TokenStore {
    val context = appContext
    // No context means we're in a preview or a unit test. An in-memory store
    // degrades to "you have to log in again" rather than crashing.
    return if (context == null) InMemoryTokenStore() else AndroidTokenStore(context)
}

private class AndroidTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): Session? {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        // A token without a user id predates this field. Treat it as a valid
        // session with an unknown id rather than forcing a re-login — the id is
        // only needed for RevenueCat identity, and it refreshes on next sign-in.
        return Session(token = token, userId = prefs.getString(KEY_USER_ID, "").orEmpty())
    }

    // commit(), not apply(): this is called right after a successful login, and
    // if the process dies in the next few milliseconds the user has an account
    // they can't get back into without logging in again. Blocking for one small
    // write is cheaper than that confusion.
    override fun write(session: Session) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).commit()
    }
}
