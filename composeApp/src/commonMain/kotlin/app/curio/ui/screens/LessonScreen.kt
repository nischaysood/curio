package app.curio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.domain.Lesson
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.platform.rememberHaptics
import app.curio.ui.components.MultipleChoiceExercise
import app.curio.ui.components.TapToFillExercise
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.delay

/**
 * The exercise player. Progress bar, Cue reactions, physical page turns.
 *
 * Sequencing on every answer:
 *   tap -> colour + haptic (same frame) -> Cue reacts -> hold -> page turns.
 * The hold is what makes the feedback land. Removing it makes the app feel fast
 * and teach nothing.
 */
@Composable
fun LessonScreen(
    lesson: Lesson,
    onComplete: (correctCount: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion
    val haptics = rememberHaptics()

    var index by remember(lesson.id) { mutableIntStateOf(0) }
    var correctCount by remember(lesson.id) { mutableIntStateOf(0) }
    var cue by remember(lesson.id) { mutableStateOf<CueState>(CueState.Idle) }
    var advancing by remember(lesson.id) { mutableStateOf(false) }

    val finished = index >= lesson.exercises.size

    // One place that owns "an answer happened". Every exercise type funnels here,
    // so adding type seven changes nothing about pacing, Cue, or progress.
    fun onAnswer(correct: Boolean) {
        if (advancing) return
        advancing = true
        if (correct) correctCount++
        cue = if (correct) CueState.Correct else CueState.Incorrect
    }

    LaunchedEffect(advancing) {
        if (!advancing) return@LaunchedEffect
        delay(motion.feedbackHoldMillis.toLong())
        cue = CueState.Idle
        index++
        advancing = false
    }

    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        cue = CueState.LessonComplete
        haptics.play(Haptic.COMPLETE)
        delay(900)
        onComplete(correctCount)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = space.gutter),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            ProgressBar(
                progress = index.toFloat() / lesson.exercises.size.coerceAtLeast(1),
                modifier = Modifier.weight(1f),
            )
            Cue(state = cue, size = 44.dp)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (finished) {
                Text(
                    text = "$correctCount / ${lesson.exercises.size}",
                    style = CurioTheme.type.display,
                    color = colors.onSurface,
                )
            } else {
                // A page turn, not a fade. The outgoing exercise leaves to the
                // left and the incoming one arrives from the right — the same
                // direction the progress bar moves.
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        val spec = tween<IntOffset>(motion.pageTurnMillis, easing = motion.pageTurnEasing)
                        slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
                    },
                    label = "page-turn",
                ) { i ->
                    val exercise = lesson.exercises.getOrNull(i)
                    if (exercise != null) {
                        ExerciseHost(
                            exercise = exercise,
                            onResult = ::onAnswer,
                            haptics = haptics,
                            onListening = { intensity ->
                                cue = if (intensity > 0f) CueState.Listening(intensity) else CueState.Idle
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one `when` over Exercise. Adding a seventh type is one composable plus one
 * branch here — and the compiler will point at this file until you add it.
 */
@Composable
private fun ExerciseHost(
    exercise: Exercise,
    haptics: Haptics,
    onResult: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onListening: (Float) -> Unit,
) {
    when (exercise) {
        is Exercise.MultipleChoice -> MultipleChoiceExercise(exercise, haptics) { onResult(it) }
        is Exercise.TapToFill -> TapToFillExercise(exercise, haptics) { onResult(it) }

        // TODO Aug 10-12: Reorder, MatchPairs, SortBuckets, TeachBack.
        // Placeholders keep the player playable end-to-end while the remaining
        // four types land, rather than blocking the whole screen on them.
        else -> NotYetBuilt(exercise, onResult)
    }
}

@Composable
private fun NotYetBuilt(exercise: Exercise, onResult: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CurioTheme.space.md)) {
        Text(
            text = exercise.prompt,
            style = CurioTheme.type.prompt,
            color = CurioTheme.colors.onSurface,
        )
        Text(
            text = "This exercise type is not built yet — tap to continue.",
            style = CurioTheme.type.label,
            color = CurioTheme.colors.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth().padding(top = CurioTheme.space.lg),
        )
    }
    LaunchedEffect(exercise.id) {
        delay(1200)
        onResult(true)
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = CurioTheme.colors
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = CurioTheme.motion.settle,
        label = "lesson-progress",
    )

    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(CurioTheme.radii.pill))
            .background(colors.surfaceSunken),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(colors.accent),
        )
    }
}
