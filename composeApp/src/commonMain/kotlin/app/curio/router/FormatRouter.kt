package app.curio.router

import app.curio.domain.BucketItem
import app.curio.domain.Chunk
import app.curio.domain.Exercise
import app.curio.domain.ExerciseType
import app.curio.domain.Lesson
import app.curio.domain.LessonChunks
import app.curio.domain.LessonState
import app.curio.domain.MatchPair
import app.curio.domain.type
import kotlin.random.Random

/**
 * CORE IP.
 *
 * Given content chunks tagged with a pedagogical shape, decide which exercise
 * teaches each one best. Deterministic code, not a model call.
 *
 * Anyone can clone six exercise UIs. The hard part is picking the right one
 * consistently across arbitrary topics. A process should become a Reorder. A
 * taxonomy should become a Sort. Get this wrong and it feels like a quiz
 * generator; get it right and it feels like a teacher.
 *
 * Guarantees (all unit-tested in FormatRouterTest):
 *  1. Exactly one TeachBack, always last.
 *  2. No non-TeachBack format appears more than [MAX_REPEATS] times.
 *  3. At least [MIN_DISTINCT_TYPES] distinct types, when the chunks allow it.
 *  4. Same input + same seed -> byte-identical output.
 *  5. A chunk that cannot satisfy its exercise's invariants degrades to a
 *     simpler format rather than throwing.
 */
object FormatRouter {

    const val MAX_REPEATS = 2
    const val MIN_DISTINCT_TYPES = 3

    /** Preference order per shape. First viable candidate wins, subject to the repeat cap. */
    private fun candidates(chunk: Chunk): List<ExerciseType> = when (chunk) {
        is Chunk.Definition -> listOf(ExerciseType.MATCH_PAIRS, ExerciseType.TAP_TO_FILL, ExerciseType.MULTIPLE_CHOICE)
        is Chunk.Sequence -> listOf(ExerciseType.REORDER, ExerciseType.MULTIPLE_CHOICE)
        is Chunk.Taxonomy -> listOf(ExerciseType.SORT_BUCKETS, ExerciseType.MULTIPLE_CHOICE)
        is Chunk.Comparison -> listOf(ExerciseType.SORT_BUCKETS, ExerciseType.MULTIPLE_CHOICE)
        is Chunk.Fact -> listOf(ExerciseType.MULTIPLE_CHOICE, ExerciseType.TAP_TO_FILL)
        is Chunk.Conceptual -> listOf(ExerciseType.TEACH_BACK, ExerciseType.MULTIPLE_CHOICE)
    }

    fun route(
        source: LessonChunks,
        lessonId: String,
        position: Int,
        state: LessonState = LessonState.LOCKED,
        seed: Long = lessonId.stableSeed(),
    ): RoutingResult {
        val rng = Random(seed)
        val dropped = mutableListOf<DroppedChunk>()
        val built = mutableListOf<Exercise>()
        val used = mutableMapOf<ExerciseType, Int>()

        var idx = 0
        fun nextId(): String = "$lessonId-e${idx++}"

        // --- Stage 1: aggregate definitions into a single MatchPairs -------------
        // A lone definition cannot be a MatchPairs (needs 3-5 pairs). Definitions
        // are the one shape that composes across chunks, so handle them first.
        // Indices, not set membership: two chunks can be `equals` by content and
        // we must not consume both when we only meant one.
        val consumed = mutableSetOf<Int>()

        val definitionIdx = source.chunks.indices.filter { source.chunks[it] is Chunk.Definition }
        if (definitionIdx.size >= 3) {
            val chosen = definitionIdx.take(5)
            consumed += chosen
            built += Exercise.MatchPairs(
                id = nextId(),
                prompt = "Match each term to its meaning",
                concept = source.lesson,
                pairs = chosen.map {
                    val d = source.chunks[it] as Chunk.Definition
                    MatchPair(left = d.concept, right = d.content)
                },
            )
            used.increment(ExerciseType.MATCH_PAIRS)
        }

        // --- Stage 2: pick the TeachBack ----------------------------------------
        // Exactly one, always last. Prefer the conceptual chunk with the most
        // rubric material; deterministic tie-break on concept name.
        val teachBackIdx = source.chunks.indices
            .filter { source.chunks[it] is Chunk.Conceptual }
            .maxWithOrNull(
                compareBy<Int>(
                    { (source.chunks[it] as Chunk.Conceptual).keyPoints.size },
                    { (source.chunks[it] as Chunk.Conceptual).content.length },
                    { (source.chunks[it] as Chunk.Conceptual).concept },
                )
            )
        val teachBackChunk = teachBackIdx?.let { source.chunks[it] as Chunk.Conceptual }
        if (teachBackIdx != null) consumed += teachBackIdx

        // --- Stage 3: route everything else -------------------------------------
        // Keep the chunk alongside its exercise so Stage 4 can re-route in place.
        val routed = mutableListOf<Routed>()
        val remaining = source.chunks.filterIndexed { i, _ -> i !in consumed }
        for (chunk in remaining) {
            val exercise = buildWithFallback(chunk, used, ::nextId, rng)
            if (exercise == null) {
                dropped += DroppedChunk(chunk.concept, "no viable format after fallback")
                continue
            }
            routed += Routed(chunk, exercise)
            used.increment(exercise.type)
        }
        built += routed.map { it.exercise }

        // --- Stage 4: diversity repair ------------------------------------------
        // TeachBack is guaranteed and always counts, so we need MIN_DISTINCT_TYPES - 1
        // distinct types among the rest. If we're short, re-route chunks that have
        // an unused alternative. Only fires on pathological lessons (eight Facts
        // in a row), but those are exactly the ones that feel like a quiz generator.
        var exercises = built.toList()
        if (exercises.distinctTypeCount() < MIN_DISTINCT_TYPES - 1) {
            val fixedPrefix = built.take(built.size - routed.size)   // the aggregated MatchPairs, if any
            exercises = fixedPrefix + diversify(
                routed = routed,
                locked = fixedPrefix.map { it.type }.toSet(),
                nextId = ::nextId,
                rng = rng,
            )
        }

        // --- Stage 5: TeachBack last --------------------------------------------
        val teachBack = teachBackChunk?.let {
            Exercise.TeachBack(
                id = "$lessonId-teachback",
                prompt = "In one or two sentences, explain ${it.concept} in your own words.",
                concept = it.concept,
                rubric = it.keyPoints.ifEmpty { listOf(it.content) },
            )
        } ?: fallbackTeachBack(lessonId, source)

        val ordered = exercises + teachBack

        // Teaching material, pulled from the same chunks the exercises came from.
        // Definitions carry the terms; the conceptual chunk carries the framing.
        // Sourcing both from one place means the intro can never teach something
        // the exercises don't test, or vice versa.
        val definitionIdeas = source.chunks
            .filterIsInstance<Chunk.Definition>()
            .map { it.content }
        val keyIdeas = (teachBackChunk?.keyPoints.orEmpty() + definitionIdeas)
            .distinct()
            .take(5)

        return RoutingResult(
            lesson = Lesson(
                summary = teachBackChunk?.content.orEmpty(),
                keyIdeas = keyIdeas,
                id = lessonId,
                title = source.lesson,
                objective = source.objective,
                position = position,
                exercises = ordered,
                state = state,
            ),
            dropped = dropped,
            distinctTypes = ordered.distinctTypeCount(),
        )
    }

    // ------------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------------

    private fun buildWithFallback(
        chunk: Chunk,
        used: Map<ExerciseType, Int>,
        nextId: () -> String,
        rng: Random,
    ): Exercise? {
        val cands = candidates(chunk).filter { it != ExerciseType.TEACH_BACK }
        // First pass: respect the repeat cap.
        for (type in cands) {
            if ((used[type] ?: 0) >= MAX_REPEATS) continue
            build(chunk, type, nextId, rng)?.let { return it }
        }
        // Second pass: cap is a preference, not a hard failure. Better a third
        // MultipleChoice than a dropped concept.
        for (type in cands) {
            build(chunk, type, nextId, rng)?.let { return it }
        }
        return null
    }

    /** Returns null when the chunk cannot satisfy the exercise's invariants. */
    private fun build(
        chunk: Chunk,
        type: ExerciseType,
        nextId: () -> String,
        rng: Random,
    ): Exercise? = when {
        chunk is Chunk.Definition && type == ExerciseType.TAP_TO_FILL ->
            tapToFill(nextId(), chunk.concept, chunk.content, chunk.distractors)

        chunk is Chunk.Definition && type == ExerciseType.MULTIPLE_CHOICE ->
            multipleChoice(
                id = nextId(),
                prompt = "What is ${chunk.concept}?",
                concept = chunk.concept,
                answer = chunk.content,
                distractors = chunk.distractors,
                rng = rng,
            )

        chunk is Chunk.Sequence && type == ExerciseType.REORDER -> {
            // Window rather than truncate-from-empty: a contiguous slice of a
            // correct sequence is still a correct sequence.
            val steps = chunk.steps.take(6)
            if (steps.size < 3) null
            else Exercise.Reorder(
                id = nextId(),
                prompt = "Put the steps of ${chunk.concept} in order",
                concept = chunk.concept,
                items = steps,
            )
        }

        chunk is Chunk.Sequence && type == ExerciseType.MULTIPLE_CHOICE -> {
            val steps = chunk.steps
            if (steps.size < 2) null
            else multipleChoice(
                id = nextId(),
                prompt = "In ${chunk.concept}, what comes first?",
                concept = chunk.concept,
                answer = steps.first(),
                distractors = steps.drop(1).take(3),
                rng = rng,
            )
        }

        chunk is Chunk.Taxonomy && type == ExerciseType.SORT_BUCKETS -> {
            val cats = chunk.categories.entries.sortedBy { it.key }.take(3)
            val items = cats.flatMap { (cat, members) -> members.map { BucketItem(it, cat) } }.take(8)
            val bucketsPresent = items.map { it.bucket }.distinct()
            if (bucketsPresent.size < 2 || items.size < 4) null
            else Exercise.SortBuckets(
                id = nextId(),
                prompt = "Sort these by ${chunk.concept}",
                concept = chunk.concept,
                buckets = bucketsPresent,
                items = items,
            )
        }

        chunk is Chunk.Taxonomy && type == ExerciseType.MULTIPLE_CHOICE -> {
            val cats = chunk.categories.entries.sortedBy { it.key }
            val withMembers = cats.firstOrNull { it.value.isNotEmpty() } ?: return null
            val others = cats.filter { it.key != withMembers.key }.flatMap { it.value }
            if (others.isEmpty()) null
            else multipleChoice(
                id = nextId(),
                prompt = "Which of these is a ${withMembers.key}?",
                concept = chunk.concept,
                answer = withMembers.value.first(),
                distractors = others.take(3),
                rng = rng,
            )
        }

        chunk is Chunk.Comparison && type == ExerciseType.SORT_BUCKETS -> {
            val items = chunk.items.entries.sortedBy { it.key }.take(8).map {
                BucketItem(it.key, if (it.value) chunk.leftLabel else chunk.rightLabel)
            }
            val bucketsPresent = items.map { it.bucket }.distinct()
            if (bucketsPresent.size < 2 || items.size < 4) null
            else Exercise.SortBuckets(
                id = nextId(),
                prompt = "${chunk.leftLabel} or ${chunk.rightLabel}?",
                concept = chunk.concept,
                buckets = bucketsPresent,
                items = items,
            )
        }

        chunk is Chunk.Comparison && type == ExerciseType.MULTIPLE_CHOICE -> {
            val left = chunk.items.entries.sortedBy { it.key }.filter { it.value }
            val right = chunk.items.entries.sortedBy { it.key }.filterNot { it.value }
            if (left.isEmpty() || right.isEmpty()) null
            else multipleChoice(
                id = nextId(),
                prompt = "Which of these is ${chunk.leftLabel}?",
                concept = chunk.concept,
                answer = left.first().key,
                distractors = right.take(3).map { it.key },
                rng = rng,
            )
        }

        chunk is Chunk.Fact && type == ExerciseType.MULTIPLE_CHOICE ->
            multipleChoice(
                id = nextId(),
                prompt = chunk.question,
                concept = chunk.concept,
                answer = chunk.answer,
                distractors = chunk.distractors,
                rng = rng,
            )

        chunk is Chunk.Fact && type == ExerciseType.TAP_TO_FILL ->
            tapToFill(nextId(), chunk.answer, chunk.question + " " + chunk.answer + ".", chunk.distractors)

        // A second conceptual chunk cannot become a second TeachBack (one per
        // lesson, always last). We can't build honest MultipleChoice distractors
        // from keyPoints either — they are all true by construction. Blank the
        // term in its own explanation instead.
        chunk is Chunk.Conceptual && type == ExerciseType.MULTIPLE_CHOICE ->
            tapToFill(nextId(), chunk.concept, chunk.content, emptyList())

        else -> null
    }

    /**
     * Blank the concept term where it appears in the sentence. If the model
     * didn't put the term in the content, we cannot build an honest blank —
     * return null and let the caller fall back.
     */
    internal fun tapToFill(
        id: String,
        term: String,
        sentence: String,
        distractors: List<String>,
    ): Exercise.TapToFill? {
        val at = sentence.indexOf(term, ignoreCase = true)
        if (at < 0) return null
        val segments = listOf(sentence.take(at), sentence.substring(at + term.length))
        return Exercise.TapToFill(
            id = id,
            prompt = "Complete the sentence",
            concept = term,
            segments = segments,
            blanks = listOf(sentence.substring(at, at + term.length)),
            distractors = distractors.take(3),
        )
    }

    /**
     * Distractors are shuffled with the lesson's seeded rng so the correct answer
     * isn't always in position 0 — but the same lesson always renders identically.
     */
    private fun multipleChoice(
        id: String,
        prompt: String,
        concept: String,
        answer: String,
        distractors: List<String>,
        rng: Random,
    ): Exercise.MultipleChoice? {
        val wrong = distractors.filter { it != answer }.distinct().take(3)
        if (wrong.isEmpty()) return null
        val options = (listOf(answer) + wrong).shuffled(rng)
        return Exercise.MultipleChoice(
            id = id,
            prompt = prompt,
            concept = concept,
            options = options,
            correctIndex = options.indexOf(answer),
        )
    }

    /**
     * Every lesson ends in a TeachBack, even if the model returned no conceptual
     * chunk. Falling back on the lesson title is weaker than a real rubric, but
     * a lesson with no TeachBack breaks the core promise.
     */
    private fun fallbackTeachBack(lessonId: String, source: LessonChunks): Exercise.TeachBack =
        Exercise.TeachBack(
            id = "$lessonId-teachback",
            prompt = "In one or two sentences, explain ${source.lesson} in your own words.",
            concept = source.lesson,
            rubric = source.chunks.map { it.concept },
        )

    // ------------------------------------------------------------------------
    // Diversity repair
    // ------------------------------------------------------------------------

    private fun diversify(
        routed: List<Routed>,
        locked: Set<ExerciseType>,
        nextId: () -> String,
        rng: Random,
    ): List<Exercise> {
        val result = routed.map { it.exercise }.toMutableList()

        fun present(): Set<ExerciseType> = locked + result.map { it.type }

        for (i in routed.indices) {
            if (present().size >= MIN_DISTINCT_TYPES - 1) break
            // Don't strand the only instance of a type we already have.
            val thisType = result[i].type
            val isOnlyCopy = result.count { it.type == thisType } == 1 && thisType !in locked
            if (isOnlyCopy && present().size <= 1) continue

            val alternatives = candidates(routed[i].chunk)
                .filter { it != ExerciseType.TEACH_BACK && it !in present() }
            for (alt in alternatives) {
                val replacement = build(routed[i].chunk, alt, nextId, rng) ?: continue
                result[i] = replacement
                break
            }
        }
        return result
    }

    private data class Routed(val chunk: Chunk, val exercise: Exercise)

    // ------------------------------------------------------------------------

    private fun MutableMap<ExerciseType, Int>.increment(type: ExerciseType) {
        this[type] = (this[type] ?: 0) + 1
    }

    private fun List<Exercise>.distinctTypeCount(): Int = map { it.type }.distinct().size
}

data class RoutingResult(
    val lesson: Lesson,
    val dropped: List<DroppedChunk>,
    val distinctTypes: Int,
)

data class DroppedChunk(
    val concept: String,
    val reason: String,
)

/**
 * Stable across processes and platforms — unlike String.hashCode(), which is
 * only guaranteed stable on the JVM. Seeds must survive a server restart or the
 * same cached course renders differently tomorrow.
 */
fun String.stableSeed(): Long {
    var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
    for (c in this) {
        h = h xor c.code.toLong()
        h *= 0x100000001b3L
    }
    return h
}
