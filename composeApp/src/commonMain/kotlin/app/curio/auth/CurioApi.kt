package app.curio.auth

import app.curio.data.CurioConfig
import app.curio.data.CurioJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

/**
 * The account half of the API: signup, login, history, progress, usage.
 *
 * Separate from [app.curio.data.RemoteCourseSource] on purpose. Generation works
 * signed out and always has; these calls all require a token. Keeping them apart
 * means the anonymous path can't accidentally grow an auth dependency.
 */
class CurioApi(
    private val baseUrl: String = CurioConfig.API_BASE_URL,
    private val tokens: TokenStore = tokenStore(),
    private val client: HttpClient = defaultClient(),
) {

    val isSignedIn: Boolean get() = tokens.read() != null

    /** The signed-in Curio user id, or null. Used to identify the RevenueCat customer. */
    val userId: String? get() = tokens.read()?.userId?.takeIf { it.isNotBlank() }

    // -----------------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------------

    suspend fun signup(email: String, password: String): AuthOutcome =
        authenticate("/auth/signup", email, password)

    suspend fun login(email: String, password: String): AuthOutcome =
        authenticate("/auth/login", email, password)

    private suspend fun authenticate(path: String, email: String, password: String): AuthOutcome {
        if (baseUrl.isBlank()) return AuthOutcome.Failed("Accounts aren't available right now.")

        val response: HttpResponse = try {
            client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(Credentials(email.trim(), password))
            }
        } catch (e: Exception) {
            return AuthOutcome.Failed("Couldn't reach Curio. Check your connection.")
        }

        if (!response.status.isSuccess()) {
            // The server's error codes are for machines. These are for people,
            // and each one says what to actually do next.
            val code = runCatching { response.body<ErrorBody>().error }.getOrNull()
            return AuthOutcome.Failed(
                when (code) {
                    "email_taken" -> "That email already has an account. Try logging in."
                    "weak_password" -> "Use at least 8 characters."
                    "bad_email" -> "That doesn't look like an email address."
                    "bad_credentials" -> "Email or password isn't right."
                    "accounts_unavailable" -> "Accounts are temporarily unavailable."
                    else -> "That didn't work. Try again in a moment."
                },
            )
        }

        val body = runCatching { response.body<AuthBody>() }.getOrNull()
            ?: return AuthOutcome.Failed("Got a strange answer back. Try again.")

        tokens.write(Session(token = body.token, userId = body.userId))
        return AuthOutcome.Success
    }

    /**
     * Log out.
     *
     * The local token is cleared even if the network call fails. From the user's
     * point of view they asked to be logged out, and leaving them logged in
     * because a request timed out is the wrong answer — the server-side session
     * expires on its own.
     */
    suspend fun logout() {
        val token = tokens.read()?.token
        tokens.clear()
        if (token == null || baseUrl.isBlank()) return
        runCatching {
            client.post("$baseUrl/auth/logout") { header("Authorization", "Bearer $token") }
        }
    }

    // -----------------------------------------------------------------------
    // Sync
    // -----------------------------------------------------------------------

    /**
     * Record a completed lesson.
     *
     * Fire-and-forget from the caller's perspective: the UI has already advanced
     * and blocking the path screen on a round trip would make finishing a lesson
     * feel slow. A dropped call costs one lesson of history, which is recoverable
     * and not worth a spinner.
     */
    suspend fun recordProgress(topicHash: String, lessonId: String, correct: Int, total: Int) {
        val token = tokens.read()?.token ?: return
        runCatching {
            client.post("$baseUrl/me/progress") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(ProgressBody(topicHash, lessonId, correct, total))
            }
        }
    }

    /**
     * Courses this user has started, newest first.
     *
     * Empty when signed out or unreachable — the home screen simply doesn't show
     * the section, rather than showing an error for something the user didn't
     * ask for.
     */
    suspend fun courses(): List<CourseSummary> {
        val token = tokens.read()?.token ?: return emptyList()
        return runCatching {
            client.get("$baseUrl/me/courses") {
                header("Authorization", "Bearer $token")
            }.body<CoursesBody>().courses
        }.getOrElse { emptyList() }
    }

    /**
     * Everything the profile screen shows, or null when signed out.
     *
     * Null is also what an unreachable server returns. The screen treats both
     * the same — it shows what it knows locally (tier, this session's usage) and
     * omits the rest, rather than blocking on a spinner or showing an error for
     * a screen the user opened to tap one button.
     */
    suspend fun profile(): ProfileBody? {
        val token = tokens.read()?.token ?: return null
        return runCatching {
            client.get("$baseUrl/me/profile") {
                header("Authorization", "Bearer $token")
            }.body<ProfileBody>()
        }.getOrNull()
    }

    /** Today's counters, or null when signed out or unreachable. */
    suspend fun usage(): UsageBody? {
        val token = tokens.read()?.token ?: return null
        return runCatching {
            client.get("$baseUrl/me/usage") {
                header("Authorization", "Bearer $token")
            }.body<UsageBody>()
        }.getOrNull()
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(CurioJson.chunks) }
            install(HttpTimeout) {
                // Short, unlike generation. These are small queries against
                // Postgres — if one takes 15 seconds something is wrong, and
                // waiting two minutes to find that out helps nobody.
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    /** Always safe to show verbatim — no server internals, no exception text. */
    data class Failed(val message: String) : AuthOutcome
}

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

@Serializable
private data class Credentials(val email: String, val password: String)

@Serializable
private data class AuthBody(
    val token: String,
    /** Postgres BIGSERIAL arrives as a JSON string, not a number. */
    val userId: String = "",
    val expiresAt: String = "",
)

@Serializable
private data class ErrorBody(val error: String = "")

@Serializable
private data class ProgressBody(
    val topicHash: String,
    val lessonId: String,
    val correct: Int,
    val total: Int,
)

@Serializable
data class UsageBody(val lessons: Int = 0, val generations: Int = 0)

@Serializable
private data class CoursesBody(val courses: List<CourseSummary> = emptyList())

/**
 * A course in the user's history, as the server sees it.
 *
 * Only what the home screen needs to draw a row. The full course — lessons,
 * chunks, exercises — is fetched through the normal `/generate` path when the
 * user taps it, which hits the KV cache and returns in milliseconds. Sending
 * every course's full content just to list titles would be a slow screen.
 */
@Serializable
data class CourseSummary(
    val topic: String = "",
    val topicHash: String = "",
    val depth: String = "STANDARD",
    /** lessonId -> state, for the ones that have been attempted. */
    val progress: Map<String, String> = emptyMap(),
) {
    val completed: Int get() = progress.values.count { it == "COMPLETE" }
}

@Serializable
data class ProfileBody(
    val email: String = "",
    /** ISO-8601 from Postgres. Parsed for display, never for logic. */
    val memberSince: String = "",
    val courses: Int = 0,
    val lessonsCompleted: Int = 0,
    val usage: UsageBody = UsageBody(),
)
