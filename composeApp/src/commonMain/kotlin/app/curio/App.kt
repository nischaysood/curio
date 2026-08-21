package app.curio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.curio.domain.Course
import app.curio.domain.Lesson
import app.curio.domain.LessonState
import app.curio.platform.PlatformBackHandler
import app.curio.ui.screens.GenerateScreen
import app.curio.ui.screens.LessonScreen
import app.curio.ui.screens.PathScreen
import app.curio.ui.theme.CurioTheme

/**
 * Three screens, hand-rolled navigation.
 *
 * No nav library on purpose: a sealed class and one `when` is less code than the
 * dependency's setup, it survives configuration changes the same way, and there
 * is no deep linking to support. Revisit if a fourth screen with real back-stack
 * semantics appears.
 */
private sealed interface Screen {
    data object Generate : Screen
    data class Path(val course: Course, val animate: Boolean) : Screen
    data class InLesson(val course: Course, val lesson: Lesson) : Screen
}

@Composable
fun App() {
    CurioTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Generate) }

        // Read theme values HERE, in composable context. `transitionSpec` is not
        // a @Composable lambda, so CurioTheme.* cannot be touched inside it.
        val turnMillis = CurioTheme.motion.pageTurnMillis
        val turnEasing = CurioTheme.motion.pageTurnEasing

        // System back. Enabled everywhere except Generate — on the first screen
        // back should exit the app, which is what users expect and what Play's
        // reviewers check.
        PlatformBackHandler(enabled = screen !is Screen.Generate) {
            screen = when (val s = screen) {
                is Screen.InLesson -> Screen.Path(s.course, animate = false)
                is Screen.Path -> Screen.Generate
                is Screen.Generate -> s
            }
        }

        // MainActivity calls enableEdgeToEdge(), so without this the app draws
        // UNDER the status bar and the gesture nav bar, and the keyboard covers
        // whatever field you're typing into. safeDrawing handles system bars,
        // display cutouts and the IME in one place — applied at the root so no
        // future screen can forget it.
        Box(
            Modifier
                .fillMaxSize()
                .background(CurioTheme.colors.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val spec = tween<IntOffset>(turnMillis, easing = turnEasing)
                    val forward = initialState is Screen.Generate ||
                        (initialState is Screen.Path && targetState is Screen.InLesson)

                    if (forward) {
                        slideInHorizontally(spec) { it } + fadeIn() togetherWith
                            slideOutHorizontally(spec) { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally(spec) { -it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally(spec) { it } + fadeOut()
                    }
                },
                label = "screen",
            ) { current ->
                when (current) {
                    is Screen.Generate -> GenerateScreen(
                        onCourseReady = { screen = Screen.Path(it, animate = true) },
                    )

                    is Screen.Path -> PathScreen(
                        course = current.course,
                        animateAssembly = current.animate,
                        onLessonSelected = { screen = Screen.InLesson(current.course, it) },
                        onBack = { screen = Screen.Generate },
                    )

                    is Screen.InLesson -> LessonScreen(
                        lesson = current.lesson,
                        onExit = { screen = Screen.Path(current.course, animate = false) },
                        onComplete = { correct ->
                            // Progress lives in memory only until persistence lands.
                            // Returning to a path that forgot what you just did is
                            // the single most demoralising thing a learning app can
                            // do, so this is the next thing to make durable.
                            screen = Screen.Path(
                                course = current.course.completing(current.lesson, correct),
                                animate = false,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Mark a lesson complete and unlock the next one.
 *
 * A lesson counts as complete however it went — getting things wrong is how
 * learning works, and locking someone out of lesson two for missing a question
 * would punish exactly the behaviour we want. Score decides whether it's flagged
 * for review, not whether you may continue.
 */
private fun Course.completing(lesson: Lesson, correctCount: Int): Course {
    val threshold = lesson.exercises.size * 0.6f
    val newState = if (correctCount >= threshold) LessonState.COMPLETE else LessonState.NEEDS_REVIEW

    return copy(
        lessons = lessons.map {
            when {
                it.id == lesson.id -> it.copy(state = newState)
                it.position == lesson.position + 1 && it.state == LessonState.LOCKED ->
                    it.copy(state = LessonState.AVAILABLE)
                else -> it
            }
        },
    )
}
