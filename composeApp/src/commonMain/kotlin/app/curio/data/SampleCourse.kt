package app.curio.data

import app.curio.domain.Course
import app.curio.domain.Depth
import app.curio.domain.LessonChunks
import app.curio.domain.LessonState
import app.curio.domain.topicHash
import app.curio.router.FormatRouter

/**
 * The Aug 3 deliverable: a hand-written course, playable, with no server.
 *
 * Written as *chunks*, not exercises — deliberately. This is byte-for-byte the
 * shape /generate will return, so day one exercises the real pipeline
 * (JSON -> chunks -> router -> exercises -> player) rather than a fake that has
 * to be thrown away when the API lands. On Aug 8 the only thing that changes is
 * where the string comes from.
 *
 * Held in Kotlin rather than composeResources so it loads synchronously. Move it
 * to a resource file once the loading path is async anyway.
 */
private val COMPILERS_LESSON_1 = """
{
  "lesson": "Lexical Analysis",
  "objective": "Turn a stream of characters into a stream of tokens.",
  "chunks": [
    {
      "shape": "definition",
      "concept": "token",
      "content": "A token is the smallest meaningful unit a compiler works with.",
      "distractors": [
        "A token is a single character of source code.",
        "A token is one line of a source file."
      ]
    },
    {
      "shape": "definition",
      "concept": "lexeme",
      "content": "A lexeme is the exact run of characters in the source that produced a token.",
      "distractors": [
        "A lexeme is the type assigned to a variable.",
        "A lexeme is a compiler error message."
      ]
    },
    {
      "shape": "definition",
      "concept": "lexer",
      "content": "A lexer is the component that scans characters and emits tokens.",
      "distractors": [
        "A lexer is the component that generates machine code.",
        "A lexer is the component that allocates memory at run time."
      ]
    },
    {
      "shape": "sequence",
      "concept": "tokenizing a line of source",
      "steps": [
        "Read the next character",
        "Decide which token class it could start",
        "Keep consuming while the characters still fit that class",
        "Emit the finished token",
        "Discard any whitespace before starting again"
      ]
    },
    {
      "shape": "taxonomy",
      "concept": "token types",
      "categories": {
        "keyword": ["if", "while", "return"],
        "literal": ["42", "'a'", "3.14"],
        "operator": ["+", "==", "<<"]
      }
    },
    {
      "shape": "comparison",
      "concept": "what the lexer catches vs what the parser catches",
      "leftLabel": "Lexer",
      "rightLabel": "Parser",
      "items": {
        "An unterminated string literal": true,
        "An illegal character like a stray backtick": true,
        "A missing closing brace": false,
        "A function called with too few arguments": false
      }
    },
    {
      "shape": "fact",
      "concept": "whitespace handling",
      "question": "What does a typical lexer do with whitespace?",
      "answer": "Discards it, unless the language is indentation-sensitive",
      "distractors": [
        "Emits one token per space character",
        "Replaces it with a semicolon",
        "Passes it through to the code generator"
      ]
    },
    {
      "shape": "concept",
      "concept": "lexical analysis",
      "content": "Lexical analysis converts a character stream into a token stream, throwing away detail the parser does not need.",
      "keyPoints": [
        "Input is raw characters, output is tokens",
        "It discards whitespace and comments",
        "It catches errors about characters, not about structure",
        "It runs before parsing and makes the parser's job much simpler"
      ]
    }
  ]
}
""".trimIndent()

/**
 * Parse -> route -> playable course. No network, no database, no server.
 */
fun sampleCourse(): Course {
    val chunks = CurioJson.chunks.decodeFromString<LessonChunks>(COMPILERS_LESSON_1)

    val routed = FormatRouter.route(
        source = chunks,
        lessonId = "sample-l1",
        position = 0,
        state = LessonState.AVAILABLE,
    )

    val topic = "How Compilers Work"
    return Course(
        id = "sample-course",
        topic = topic,
        topicHash = topicHash(topic, Depth.STANDARD),
        depth = Depth.STANDARD,
        lessons = listOf(routed.lesson),
        createdAtEpochMs = 0L,
    )
}
