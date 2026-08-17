package app.curio.grading

import app.curio.domain.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeachBackGraderTest {

    private val lexing = Exercise.TeachBack(
        id = "t1",
        prompt = "In one or two sentences, explain lexical analysis in your own words.",
        concept = "lexical analysis",
        rubric = listOf(
            "Input is raw characters, output is tokens",
            "It discards whitespace and comments",
            "It catches errors about characters, not about structure",
            "It runs before parsing and makes the parser's job much simpler",
        ),
    )

    @Test
    fun aGoodAnswerInTheLearnersOwnWordsPasses() {
        // Deliberately shares almost no exact phrasing with the rubric.
        val grade = TeachBackGrader.grade(
            lexing,
            "It reads the raw character stream and emits tokens, throwing away " +
                "whitespace and comments before the parser ever runs.",
        )

        assertTrue(grade.correct, "coverage was ${grade.coverage}")
        assertTrue(grade.coverage >= 0.5f)
    }

    @Test
    fun anEmptyOrTooShortAnswerFails() {
        assertFalse(TeachBackGrader.grade(lexing, "").correct)
        assertFalse(TeachBackGrader.grade(lexing, "idk").correct)
        assertFalse(TeachBackGrader.grade(lexing, "it does the thing").correct)
    }

    @Test
    fun keyboardMashingDoesNotPass() {
        val grade = TeachBackGrader.grade(lexing, "asdkjhasd kjahsdkjh asdkjhaskjdh askjdhakjsdh")

        assertFalse(grade.correct)
        assertEquals(0f, grade.coverage)
    }

    @Test
    fun paddingLengthWithFillerDoesNotPass() {
        // The minChars floor must not be gameable by typing anything long enough.
        val grade = TeachBackGrader.grade(
            lexing,
            "I think that this is a thing which is very much a thing that they do when it happens.",
        )

        assertFalse(grade.correct, "coverage was ${grade.coverage}")
    }

    @Test
    fun pluralsAndVerbEndingsStillMatch() {
        // "token" vs "tokens", "discard" vs "discards", "run" vs "running".
        val singular = TeachBackGrader.grade(
            lexing,
            "The lexer turns a character into a token and discards whitespace and comments.",
        )
        val plural = TeachBackGrader.grade(
            lexing,
            "The lexer turns characters into tokens and is discarding whitespace and comments.",
        )

        assertEquals(singular.correct, plural.correct)
    }

    @Test
    fun partialRecallPassesButWithLowConfidence() {
        val grade = TeachBackGrader.grade(
            lexing,
            "The lexer turns a character into a token and discards whitespace and comments.",
        )

        assertTrue(grade.correct)
        assertEquals(Confidence.LOW, grade.confidence)
        assertTrue(grade.missed.isNotEmpty(), "a partial answer should name what was missed")
    }

    @Test
    fun strongAnswersReportHighConfidence() {
        val grade = TeachBackGrader.grade(
            lexing,
            "Lexical analysis reads raw characters and outputs tokens. It discards " +
                "whitespace and comments, catches errors about illegal characters " +
                "rather than structure, and runs before parsing so the parser is simpler.",
        )

        assertTrue(grade.correct)
        assertEquals(Confidence.HIGH, grade.confidence)
        assertTrue(grade.missed.isEmpty(), "missed = ${grade.missed}")
    }

    @Test
    fun missedPointsAreReportedSoTheUiCanTeach() {
        // "Wrong" on its own teaches nothing. The UI needs the gap, not just a verdict.
        val grade = TeachBackGrader.grade(
            lexing,
            "It turns characters into tokens and that is basically the whole job of it.",
        )

        assertTrue(grade.correct, "recalling the core idea concisely is a pass")
        assertTrue(grade.missed.isNotEmpty())
        assertTrue(grade.missed.all { it in lexing.rubric })
    }

    @Test
    fun concisionIsNotPunished() {
        // The grader cannot tell "brief" from "incomplete", so it must not pretend
        // to. One rubric point recalled in the learner's own words is a pass, with
        // the confidence lowered to say so.
        val grade = TeachBackGrader.grade(
            lexing,
            "It takes the raw characters in the file and turns them into tokens for later.",
        )

        assertTrue(grade.correct)
        assertEquals(Confidence.LOW, grade.confidence)
    }

    @Test
    fun anEmptyRubricRewardsTheAttempt() {
        // A missing rubric is our content pipeline's bug. Never punish the learner for it.
        val noRubric = lexing.copy(rubric = emptyList())
        val grade = TeachBackGrader.grade(noRubric, "Some genuine attempt at an explanation here.")

        assertTrue(grade.correct)
    }

    @Test
    fun gradingIsCaseAndPunctuationInsensitive() {
        val a = TeachBackGrader.grade(lexing, "it reads characters and emits tokens, discarding whitespace")
        val b = TeachBackGrader.grade(lexing, "IT READS CHARACTERS AND EMITS TOKENS -- DISCARDING WHITESPACE!!")

        assertEquals(a.correct, b.correct)
        assertEquals(a.coverage, b.coverage)
    }
}
