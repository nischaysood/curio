package app.curio.data

import app.curio.domain.Course
import app.curio.domain.Depth
import app.curio.domain.LessonChunks
import app.curio.domain.LessonState
import app.curio.domain.topicHash
import app.curio.router.FormatRouter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where courses come from.
 *
 * Behind an interface from day one so swapping provider — or swapping the server
 * out entirely — is a config change rather than a refactor.
 */
interface CourseSource {
    suspend fun course(topic: String, depth: Depth): CourseResult
}

sealed interface CourseResult {
    data class Ready(val course: Course, val fromCache: Boolean) : CourseResult

    /** We have nothing for this topic and shouldn't pretend otherwise. */
    data object NotFound : CourseResult

    data class Refused(val reason: String) : CourseResult

    /** Network or server problem. The message is shown to the user, so keep it human. */
    data class Failed(val message: String) : CourseResult
}

/**
 * Bundled courses first, then the network.
 *
 * The order matters. A bundled topic answers instantly and offline; only an
 * unknown topic pays for a round trip. It also means a server outage, a rate
 * limit, or a plane degrades the app rather than breaking it — which is exactly
 * the property that let this ship before the server existed.
 */
class DefaultCourseSource(
    private val remote: RemoteCourseSource?,
) : CourseSource {

    override suspend fun course(topic: String, depth: Depth): CourseResult {
        CourseCatalog.find(topic, depth)?.let {
            return CourseResult.Ready(it, fromCache = true)
        }
        val api = remote ?: return CourseResult.NotFound
        return api.course(topic, depth)
    }
}

/**
 * Talks to the Worker. Knows nothing about Gemini — that lives server-side, so
 * no API key ever ships inside the app.
 */
class RemoteCourseSource(
    private val baseUrl: String,
    private val client: HttpClient = defaultClient(),
) : CourseSource {

    override suspend fun course(topic: String, depth: Depth): CourseResult {
        val response: HttpResponse = try {
            client.post("$baseUrl/generate") {
                contentType(ContentType.Application.Json)
                setBody(GenerateRequest(topic = topic.trim(), depth = depth.name))
            }
        } catch (e: HttpRequestTimeoutException) {
            // Distinct from being offline, and the distinction matters: this one
            // means the server IS working and just took too long. Saying
            // "couldn't reach the internet" here sends you debugging the wrong
            // layer — which is exactly what it did to us.
            return CourseResult.Failed("That took too long. Try a narrower topic.")
        } catch (e: Exception) {
            // Genuinely offline, DNS failure, TLS problem. Never surface the
            // exception text — "UnresolvedAddressException" means nothing here.
            return CourseResult.Failed("Couldn't reach the internet. Try one below.")
        }

        if (!response.status.isSuccess()) {
            return when (response.status.value) {
                422 -> CourseResult.Refused("That's not something Curio can teach.")
                400 -> CourseResult.NotFound
                else -> CourseResult.Failed("Couldn't build that course. Try again in a moment.")
            }
        }

        val payload = try {
            response.body<GenerateResponse>()
        } catch (e: Exception) {
            return CourseResult.Failed("Got a strange answer back. Try again.")
        }

        val course = payload.toCourse(depth)
            ?: return CourseResult.Failed("That course came back empty. Try rewording it.")

        return CourseResult.Ready(course, fromCache = payload.cached)
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(CurioJson.chunks) }
            install(HttpTimeout) {
                // Generation is two staged model calls with throttled fan-out,
                // so a cache miss genuinely takes 30-90 seconds. Timing out at
                // 60 cancels work that was about to succeed — and the server
                // still pays for the tokens. Cache hits return in ~200ms
                // regardless, so this ceiling only affects first-time topics.
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 120_000
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Wire types
// ---------------------------------------------------------------------------

@Serializable
private data class GenerateRequest(val topic: String, val depth: String)

@Serializable
private data class GenerateResponse(
    val topic: String,
    @SerialName("topicHash") val hash: String = "",
    val lessons: List<LessonChunks> = emptyList(),
    val cached: Boolean = false,
) {
    /**
     * Chunks -> exercises happens HERE, on device, using the same router the
     * bundled courses go through. The server never sends exercises, so a course
     * generated today and a course bundled in the APK are structurally identical.
     */
    fun toCourse(depth: Depth): Course? {
        if (lessons.isEmpty()) return null

        val id = hash.ifBlank { topicHash(topic, depth) }
        val routed = lessons.mapIndexed { i, chunks ->
            FormatRouter.route(
                source = chunks,
                lessonId = "$id-l$i",
                position = i,
                state = if (i == 0) LessonState.AVAILABLE else LessonState.LOCKED,
            ).lesson
        }

        return Course(
            id = id,
            topic = topic,
            topicHash = id,
            depth = depth,
            lessons = routed,
            createdAtEpochMs = 0L,
        )
    }
}
