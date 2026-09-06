package app.curio.platform

import android.content.Intent
import android.net.Uri
import app.curio.auth.appContext

actual fun openUrl(url: String) {
    val context = appContext ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                // Launching from the application context rather than an Activity
                // requires its own task. Without this the intent throws.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
