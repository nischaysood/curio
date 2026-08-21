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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.curio.ui.components.MatchPairsExercise
import app.curio.ui.components.MultipleChoiceExercise
import app.curio.ui.components.ReorderExercise
import app.curio.ui.components.SortBucketsExercise
import app.curio.ui.components.TapToFillExercise
import app.curio.ui.components.TeachBackExercise
import app.curio.ui.components.tappable
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
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion
    val haptics = rememberHaptics()

    // Teaching first. You cannot retrieve what you were never shown, so the
    // intro is not optional chrome — it's what makes the exercises answerable.
    var showIntro by remember(lesson.id) {
        mutableStateOf(lesson.summary.isNotBlank() || lesson.keyIdeas.isNotEmpty())
    }
    var index by remember(lesson.id) { mutableIntStateOf(0) }
    var correctCount by remember(lesson.id) { mutableIntStateOf(0) }
    var cue by remember(lesson.id) { mutableStateOf<CueState>(CueState.Idle) }
    var advancing by remember(lesson.id) { mutableStateOf(false) }

    val finished = !showIntro && index >= lesson.exercises.size

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
            // Visible way out. The system back gesture also works on Android, but
            // a gesture that not every platform has can't be the only exit.
            ExitButton(onTap = onExit)
            ProgressBar(
                progress = index.toFloat() / lesson.exercises.size.coerceAtLeast(1),
                modifier = Modifier.weight(1f),
            )
            Cue(state = cue, size = 44.dp)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (showIntro) {
                LessonIntro(lesson = lesson, onStart = { showIntro = false })
            } else if (finished) {
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
                        // Every exercise scrolls. Reorder and SortBuckets are
                        // taller than a small phone viewport, and an exercise you
                        // physically cannot finish is worse than a missing one.
                        // Fresh scroll state per exercise so a new page starts
                        // at the top rather than inheriting the last one's offset.
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = space.md),
                        ) {
                            ExerciseHost(
                                exercise = exercise,
                                onResult = ::onAnswer,
                                haptics = haptics,
                                onListening = { intensity ->
                                    cue = if (intensity > 0f) CueState.Listening(intensity) else CueState.Idle
                                },
                            )
                            // Breathing room so the last tile isn't flush against
                            // the bottom edge, which reads as clipped content.
                            Spacer(Modifier.height(space.xxl))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The one `when` over Exercise, and it is exhaustive — no `else` branch on
 * purpose. Adding a seventh type is one composable plus one branch here, and
 * until you add it the compiler will refuse to build and point at this file.
 * That is the whole benefit of the sealed interface; an `else` would throw it away.
 */
@Composable
private fun ExerciseHost(
    exercise: Exercise,
    haptics: Haptics,
    onResult: (Boolean) -> Unit,
    onListening: (Float) -> Unit,
) {
    // Named `onResult =` on purpose, NOT a trailing lambda: `modifier` is the last
    // parameter on every exercise composable (Compose convention), so a trailing
    // lambda would bind to the wrong one and fail to compile.
    when (exercise) {
        is Exercise.MultipleChoice ->
            MultipleChoiceExercise(exercise, haptics, onResult = { onResult(it) })
        is Exercise.TapToFill ->
            TapToFillExercise(exercise, haptics, onResult = { onResult(it) })
        is Exercise.MatchPairs ->
            MatchPairsExercise(exercise, haptics, onResult = { onResult(it) })
        is Exercise.Reorder ->
            ReorderExercise(exercise, haptics, onResult = { onResult(it) })
        is Exercise.SortBuckets ->
            SortBucketsExercise(exercise, haptics, onResult = { onResult(it) })
        is Exercise.TeachBack -> TeachBackExercise(
            exercise = exercise,
            haptics = haptics,
            onResult = { onResult(it) },
            onTypingChanged = onListening,
        )
    }
}

/**
 * Leaving mid-lesson is normal, not failure — people get interrupted. No
 * "are you sure?" dialog, no guilt. Progress within the lesson is lost for now;
 * persisting it is a later job.
 */
@Composable
private fun ExitButton(onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CurioTheme.radii.pill)

    Box(
        Modifier
            .size(CurioTheme.space.touchTarget)
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .tappable(interactionSource = interaction, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✕",
            style = CurioTheme.type.tile,
            color = colors.onSurfaceMuted,
        )
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
