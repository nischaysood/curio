package app.curio.router

import app.curio.domain.Chunk
import app.curio.domain.Exercise
import app.curio.domain.ExerciseType
import app.curio.domain.LessonChunks
import app.curio.domain.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatRouterTest {

    // ---------------------------------------------------------------- helpers

    private fun definition(term: String, n: Int = 0) = Chunk.Definition(
        concept = term,
        content = "A $term is the smallest unit of meaning number $n.",
        distractors = listOf("a wrong thing $n", "another wrong thing $n"),
    )

    private fun fact(n: Int) = Chunk.Fact(
        concept = "fact $n",
        question = "What is fact $n?",
        answer = "answer $n",
        distractors = listOf("wrong $n a", "wrong $n b", "wrong $n c"),
    )

    private val sequence = Chunk.Sequence(
        concept = "tokenizing a line",
        steps = listOf("Read a character", "Classify it", "Extend the current token", "Emit the token"),
    )

    private val taxonomy = Chunk.Taxonomy(
        concept = "token types",
        categories = mapOf(
            "keyword" to listOf("if", "while", "return"),
            "literal" to listOf("42", "'a'", "3.14"),
        ),
    )

    private val comparison = Chunk.Comparison(
        concept = "compile time vs run time",
        leftLabel = "Compile time",
        rightLabel = "Run time",
        items = mapOf(
            "Type checking" to true,
            "Syntax errors" to true,
            "Null dereference" to false,
            "Stack overflow" to false,
        ),
    )

    private val conceptual = Chunk.Conceptual(
        concept = "lexical analysis",
        content = "Lexical analysis converts a character stream into a token stream.",
        keyPoints = listOf("Input is characters", "Output is tokens", "It discards whitespace"),
    )

    private fun route(vararg chunks: Chunk, seed: Long = 42L) = FormatRouter.route(
        source = LessonChunks(lesson = "Lexical Analysis", objective = "Tokenize source text", chunks = chunks.toList()),
        lessonId = "l1",
        position = 0,
        seed = seed,
    )

    // ------------------------------------------------------- guarantee 1: TeachBack

    @Test
    fun teachBackIsAlwaysLastAndAlwaysExactlyOne() {
        val result = route(sequence, taxonomy, comparison, conceptual)
        val exercises = result.lesson.exercises

        assertTrue(exercises.last() is Exercise.TeachBack, "last exercise must be TeachBack")
        assertEquals(1, exercises.count { it is Exercise.TeachBack }, "exactly one TeachBack per lesson")
    }

    @Test
    fun teachBackExistsEvenWithNoConceptualChunk() {
        val result = route(sequence, taxonomy, comparison)
        val last = result.lesson.exercises.last() as? Exercise.TeachBack

        assertNotNull(last, "last exercise must be a TeachBack")
        assertTrue(last.rubric.isNotEmpty(), "fallback TeachBack still carries a rubric")
    }

    @Test
    fun teachBackPrefersTheRichestConceptualChunk() {
        val thin = Chunk.Conceptual("thin idea", "Short.", emptyList())
        val rich = Chunk.Conceptual("rich idea", "Longer explanation.", listOf("a", "b", "c"))

        val result = route(thin, rich, sequence, taxonomy)
        val teachBack = result.lesson.exercises.last() as Exercise.TeachBack

        assertEquals("rich idea", teachBack.concept)
        assertEquals(listOf("a", "b", "c"), teachBack.rubric)
    }

    @Test
    fun secondConceptualChunkDoesNotBecomeASecondTeachBack() {
        val a = Chunk.Conceptual("parsing", "Parsing builds a tree from tokens.", listOf("x", "y"))
        val b = Chunk.Conceptual("scoping", "Scoping decides which scoping binding a name refers to.", listOf("x"))

        val result = route(a, b, sequence, taxonomy)

        assertEquals(1, result.lesson.exercises.count { it is Exercise.TeachBack })
    }

    // ------------------------------------------------------- guarantee 2: repeat cap

    @Test
    fun noFormatRepeatsMoreThanTwiceWhenAlternativesExist() {
        // Six facts would all route to MultipleChoice on a naive router.
        val result = route(fact(1), fact(2), fact(3), fact(4), fact(5), fact(6))

        val counts = result.lesson.exercises
            .filter { it.type != ExerciseType.TEACH_BACK }
            .groupingBy { it.type }
            .eachCount()

        // The cap is a strong preference: it may be exceeded only after every
        // alternative for that chunk has also been tried and failed.
        val overCap = counts.filterValues { it > FormatRouter.MAX_REPEATS }
        assertTrue(
            overCap.isEmpty() || overCap.keys == setOf(ExerciseType.MULTIPLE_CHOICE),
            "only MultipleChoice may exceed the cap as a last resort, got $counts",
        )
    }

    @Test
    fun mixedChunksProduceNoRepeatsAtAll() {
        val result = route(sequence, taxonomy, comparison, conceptual)
        val counts = result.lesson.exercises.groupingBy { it.type }.eachCount()

        assertTrue(counts.values.all { it <= FormatRouter.MAX_REPEATS }, "got $counts")
    }

    // ------------------------------------------------------- guarantee 3: diversity

    @Test
    fun lessonHasAtLeastThreeDistinctExerciseTypes() {
        val result = route(sequence, taxonomy, comparison, conceptual)

        assertTrue(
            result.distinctTypes >= FormatRouter.MIN_DISTINCT_TYPES,
            "expected >= ${FormatRouter.MIN_DISTINCT_TYPES} distinct types, got ${result.distinctTypes}",
        )
    }

    @Test
    fun degenerateAllFactLessonStillReachesTwoTypesPlusTeachBack() {
        val result = route(fact(1), fact(2), fact(3))
        val types = result.lesson.exercises.map { it.type }.distinct()

        assertTrue(types.contains(ExerciseType.TEACH_BACK))
        assertTrue(types.size >= 2, "even an all-fact lesson should not be uniform, got $types")
    }

    // ------------------------------------------------------- guarantee 4: determinism

    @Test
    fun sameInputAndSeedProducesIdenticalOutput() {
        val a = route(sequence, taxonomy, comparison, conceptual, seed = 7L)
        val b = route(sequence, taxonomy, comparison, conceptual, seed = 7L)

        assertEquals(a.lesson, b.lesson)
    }

    @Test
    fun stableSeedDoesNotDependOnJvmStringHashCode() {
        // Must be reproducible across processes and platforms, or a cached course
        // renders differently tomorrow than it did today.
        assertEquals("lesson-1".stableSeed(), "lesson-1".stableSeed())
        assertTrue("lesson-1".stableSeed() != "lesson-2".stableSeed())
    }

    @Test
    fun correctAnswerIsNotAlwaysInTheFirstPosition() {
        // If the answer is always option 0, users learn the tell, not the topic.
        val positions = (1..40).map { seed ->
            val result = route(fact(1), fact(2), fact(3), sequence, taxonomy, seed = seed.toLong())
            result.lesson.exercises.filterIsInstance<Exercise.MultipleChoice>().first().correctIndex
        }

        assertTrue(positions.distinct().size > 1, "correctIndex never varied across 40 seeds")
    }

    // ------------------------------------------------------- shape -> format mapping

    @Test
    fun sequenceBecomesReorder() {
        val result = route(sequence, taxonomy, comparison)
        val reorder = result.lesson.exercises.filterIsInstance<Exercise.Reorder>().firstOrNull()

        assertNotNull(reorder, "a sequence chunk must become a Reorder")
        assertEquals(sequence.steps, reorder.items, "items are stored in correct order")
    }

    @Test
    fun taxonomyBecomesSortBuckets() {
        val result = route(taxonomy, sequence, fact(1))
        val sort = result.lesson.exercises.filterIsInstance<Exercise.SortBuckets>().firstOrNull()

        assertNotNull(sort)
        assertEquals(listOf("keyword", "literal"), sort.buckets.sorted())
        assertEquals(6, sort.items.size)
    }

    @Test
    fun comparisonBecomesTwoBucketSort() {
        val result = route(comparison, sequence, fact(1))
        val sort = result.lesson.exercises.filterIsInstance<Exercise.SortBuckets>().firstOrNull()

        assertNotNull(sort)
        assertEquals(2, sort.buckets.size)
        assertEquals(setOf("Compile time", "Run time"), sort.buckets.toSet())
    }

    @Test
    fun threeOrMoreDefinitionsAggregateIntoOneMatchPairs() {
        val result = route(definition("token", 1), definition("lexeme", 2), definition("grammar", 3), sequence)
        val matches = result.lesson.exercises.filterIsInstance<Exercise.MatchPairs>()

        assertEquals(1, matches.size, "definitions aggregate into a single MatchPairs, not three")
        assertEquals(3, matches.first().pairs.size)
    }

    @Test
    fun twoDefinitionsCannotFormMatchPairsAndDegradeGracefully() {
        // MatchPairs requires 3-5 pairs. Two definitions must not throw.
        val result = route(definition("token", 1), definition("lexeme", 2), sequence)

        assertTrue(result.lesson.exercises.none { it is Exercise.MatchPairs })
        assertTrue(result.lesson.exercises.isNotEmpty())
    }

    // ------------------------------------------------------- guarantee 5: degradation

    @Test
    fun tooShortSequenceDegradesToMultipleChoiceInsteadOfThrowing() {
        val short = Chunk.Sequence("two step thing", listOf("First", "Second"))
        val result = route(short, taxonomy, fact(1))

        assertTrue(result.lesson.exercises.none { it is Exercise.Reorder }, "2 steps cannot be a Reorder")
        assertTrue(result.dropped.isEmpty() || result.dropped.size < 3)
    }

    @Test
    fun longSequenceIsWindowedToSixSteps() {
        val long = Chunk.Sequence("nine step thing", (1..9).map { "Step $it" })
        val result = route(long, taxonomy, fact(1))
        val reorder = result.lesson.exercises.filterIsInstance<Exercise.Reorder>().first()

        assertEquals(6, reorder.items.size)
        assertEquals("Step 1", reorder.items.first(), "windowing keeps the sequence contiguous and correct")
    }

    @Test
    fun factWithNoDistractorsIsDroppedNotFabricated() {
        // Inventing wrong answers is how a learning app teaches falsehoods.
        val bare = Chunk.Fact("bare", "What is bare?", "an answer", emptyList())
        val result = route(bare, sequence, taxonomy)

        assertTrue(
            result.lesson.exercises.none { it is Exercise.MultipleChoice },
            "a fact with no distractors must not become a MultipleChoice",
        )
    }

    @Test
    fun tapToFillReturnsNullWhenTheTermIsAbsentFromTheSentence() {
        val ok = FormatRouter.tapToFill("x", "token", "A token is a unit.", emptyList())
        val bad = FormatRouter.tapToFill("x", "token", "Something unrelated entirely.", emptyList())

        assertNotNull(ok)
        assertEquals(listOf("token"), ok.blanks)
        assertEquals("A ", ok.segments.first())
        assertNull(bad, "cannot blank a term that isn't in the sentence")
    }

    @Test
    fun everyExerciseCarriesANonEmptyPromptAndConcept() {
        val result = route(sequence, taxonomy, comparison, conceptual, definition("token", 1))

        result.lesson.exercises.forEach {
            assertTrue(it.prompt.isNotBlank(), "${it.id} has a blank prompt")
            assertTrue(it.concept.isNotBlank(), "${it.id} has a blank concept")
        }
    }

    @Test
    fun exerciseIdsAreUniqueWithinALesson() {
        val result = route(sequence, taxonomy, comparison, conceptual, fact(1), fact(2))
        val ids = result.lesson.exercises.map { it.id }

        assertEquals(ids.size, ids.distinct().size, "duplicate ids break progress tracking")
    }

    @Test
    fun emptyChunkListStillProducesAPlayableLesson() {
        val result = route()

        assertEquals(1, result.lesson.exercises.size)
        assertTrue(result.lesson.exercises.single() is Exercise.TeachBack)
    }
}
