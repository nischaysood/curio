package app.curio.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.curio.domain.Course
import app.curio.domain.Lesson
import app.curio.domain.LessonState
import app.curio.platform.Haptic
import app.curio.platform.rememberHaptics
import app.curio.ui.components.tappable
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The signature moment.
 *
 * Every other AI learning tool shows a spinner for 20-40 seconds. Curio animates
 * the course BUILDING ITSELF — Cue fragments and swirls, nodes drop in one by one
 * carrying real lesson titles, the connectors draw between them, the structure
 * settles.
 *
 * The critical rule: nodes carry REAL titles from the first frame. A skeleton
 * that fills in later is just a spinner wearing a costume. The reason this works
 * is that the outline is genuinely ready before the content is — so the animation
 * is showing you true information, not stalling.
 *
 * Tell judges exactly this: "generate a course and watch the path build."
 */
@Composable
fun PathScreen(
    course: Course,
    onLessonSelected: (Lesson) -> Unit,
    onBack: () -> Unit,
    /** False when returning from a lesson — the path is already built. */
    animateAssembly: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val haptics = rememberHaptics()

    // One Animatable per node, driven in sequence. Deliberately not a single
    // shared progress value: staggered independent springs are what make it read
    // as assembly rather than as a list fading in.
    val reveal = remember(course.id) {
        course.lessons.map { Animatable(if (animateAssembly) 0f else 1f) }
    }
    val cueState = remember(course.id) { androidx.compose.runtime.mutableStateOf<CueState>(CueState.Idle) }

    LaunchedEffect(course.id, animateAssembly) {
        if (!animateAssembly) return@LaunchedEffect
        cueState.value = CueState.Thinking
        delay(220)
        reveal.forEachIndexed { i, anim ->
            haptics.play(Haptic.SNAP)
            // launch, NOT await: the next node starts falling before this one
            // settles, and that overlap is what reads as assembly. Awaiting each
            // in turn would look like a queue being processed.
            launch { anim.animateTo(1f, tween(durationMillis = 420)) }
            delay(if (i == 0) 260L else 150L)
        }
        delay(400)
        cueState.value = CueState.Idle
        haptics.play(Haptic.LOCK)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = space.gutter),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = space.lg, bottom = space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(course.topic, style = CurioTheme.type.title, color = colors.onSurface)
                Text(
                    text = "${course.lessons.size} LESSONS · ${(course.progress * 100).toInt()}% DONE",
                    style = CurioTheme.type.label,
                    color = colors.onSurfaceMuted,
                )
            }
            Cue(state = cueState.value, size = 44.dp)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
            course.lessons.forEachIndexed { i, lesson ->
                val progress = reveal[i].value

                if (i > 0) Connector(progress = progress)

                PathNode(
                    lesson = lesson,
                    progress = progress,
                    onTap = {
                        if (lesson.state == LessonState.LOCKED) {
                            haptics.play(Haptic.INCORRECT)
                        } else {
                            haptics.play(Haptic.TAP)
                            onLessonSelected(lesson)
                        }
                    },
                )
            }
            Spacer(Modifier.height(space.xl))
        }

        Box(Modifier.fillMaxWidth().padding(bottom = space.lg)) {
            SecondaryButton(label = "New topic", onTap = onBack)
        }
    }
}

/** The line between nodes. Draws itself as the node below arrives. */
@Composable
private fun Connector(progress: Float) {
    val colors = CurioTheme.colors
    Box(
        Modifier
            .padding(start = 21.dp)
            .width(2.dp)
            .height(20.dp)
            .graphicsLayer {
                // Scales from the top so it reads as drawing downward into the
                // node, rather than growing out of nothing in both directions.
                scaleY = progress
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .background(colors.outline),
    )
}

@Composable
private fun PathNode(lesson: Lesson, progress: Float, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.card)
    val interaction = remember { MutableInteractionSource() }

    val (dotFill, dotLine) = when (lesson.state) {
        LessonState.COMPLETE -> colors.success to colors.success
        LessonState.AVAILABLE -> colors.accent to colors.accent
        LessonState.NEEDS_REVIEW -> colors.surfaceRaised to colors.accent
        LessonState.LOCKED -> colors.surfaceRaised to colors.outline
    }
    val locked = lesson.state == LessonState.LOCKED

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                // Drops in from above and settles. 18dp is small on purpose —
                // big travel reads as a slide transition, not as assembly.
                translationY = (1f - progress) * -18.dp.toPx()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .scale(progress)
                .clip(RoundedCornerShape(CurioTheme.radii.pill))
                .background(dotFill)
                .border(2.dp, dotLine, RoundedCornerShape(CurioTheme.radii.pill)),
        )

        Column(
            Modifier
                .weight(1f)
                .alpha(if (locked) 0.5f else 1f)
                .clip(shape)
                .background(colors.surfaceRaised, shape)
                .border(1.5.dp, if (locked) colors.outline else dotLine, shape)
                .tappable(interactionSource = interaction, onClick = onTap)
                .padding(space.md),
        ) {
            Text(lesson.title, style = CurioTheme.type.tile, color = colors.onSurface)
            Text(
                text = when (lesson.state) {
                    LessonState.COMPLETE -> "COMPLETE"
                    LessonState.NEEDS_REVIEW -> "REVIEW DUE"
                    LessonState.LOCKED -> "LOCKED"
                    LessonState.AVAILABLE -> "${lesson.exercises.size} EXERCISES · ${lesson.estimatedMinutes} MIN"
                },
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
internal fun SecondaryButton(label: String, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.5.dp, colors.outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(vertical = CurioTheme.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CurioTheme.type.tile, color = colors.onSurface)
    }
}
