package app.curio.data

import kotlinx.serialization.json.Json

object CurioJson {

    /**
     * For content coming off the wire from /generate.
     *
     * The discriminator is "shape" because that is the pedagogical tag the model
     * is asked to produce, and it is the field Gemini's schema-constrained output
     * enum-locks. An invalid shape therefore cannot reach the parser.
     *
     * [ignoreUnknownKeys] is deliberate: the server may add fields ahead of a
     * client release, and an old build must not crash on a new cached course.
     */
    val chunks: Json = Json {
        classDiscriminator = "shape"
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** For courses persisted locally, after routing. */
    val local: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
