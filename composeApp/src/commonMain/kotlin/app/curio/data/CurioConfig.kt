package app.curio.data

/**
 * Build-time configuration.
 *
 * [API_BASE_URL] is blank until the Worker is deployed. Blank means the app runs
 * bundled-courses-only — fully functional, just a smaller library. That's the
 * point: generation is additive, never load-bearing, so a server that isn't
 * ready can't stop the app shipping.
 *
 * After `npx wrangler deploy`, paste the workers.dev URL here.
 * No API key belongs in this file, or anywhere else in the app — the Worker
 * holds the Gemini key so it can't be extracted from the APK.
 */
object CurioConfig {

    const val API_BASE_URL: String = "https://curio-api.nischaysood.workers.dev"

    val remote: RemoteCourseSource?
        get() = if (API_BASE_URL.isBlank()) null else RemoteCourseSource(API_BASE_URL)

    fun courseSource(): CourseSource = DefaultCourseSource(remote)
}
