package app.curio.ui.cue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.curio.ui.theme.CurioTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PARTICLE_COUNT = 14
private const val TAU = 2.0 * PI

/**
 * The whole mascot. One composable, one Canvas, no assets.
 *
 * [state] changes drive a single transient `Animatable` (0f -> 1f) layered on top
 * of two continuous infinite transitions (breath, spin). Keeping transient and
 * continuous motion separate is what stops the orb from ever "snapping" — a
 * reaction plays over the breathing, it does not replace it.
 */
@Composable
fun Cue(
    state: CueState,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val colors = CurioTheme.colors
    val motion = CurioTheme.motion

    // --- continuous ---------------------------------------------------------
    val infinite = rememberInfiniteTransition(label = "cue-continuous")

    val breath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.breathMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breath",
    )

    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    // --- transient ----------------------------------------------------------
    // One shared progress value: 0f at rest, animated to 1f on every state change.
    val progress = remember { Animatable(1f) }

    // How far the orb has broken apart. Only Thinking holds this open.
    val scatter = remember { Animatable(0f) }

    LaunchedEffect(state) {
        progress.snapTo(0f)
        when (state) {
            is CueState.Thinking -> {
                scatter.animateTo(1f, motion.settle)
                progress.snapTo(1f)
            }

            is CueState.Correct -> {
                scatter.snapTo(0f)
                progress.animateTo(1f, motion.bounce)
            }

            is CueState.Incorrect -> {
                scatter.snapTo(0f)
                progress.animateTo(1f, motion.snap)
            }

            is CueState.LessonComplete -> {
                scatter.animateTo(0.7f, motion.bounce)
                progress.animateTo(1f, motion.bounce)
                scatter.animateTo(0f, motion.settle)
            }

            else -> {
                scatter.animateTo(0f, motion.settle)
                progress.animateTo(1f, motion.settle)
            }
        }
    }

    val tint by animateColorAsState(
        targetValue = when (state) {
            is CueState.Correct -> colors.success
            is CueState.Incorrect -> colors.onSurfaceMuted   // cool shift, NOT the error red
            is CueState.LessonComplete -> colors.success
            else -> colors.accent
        },
        animationSpec = tween(motion.feedbackHoldMillis / 2),
        label = "cue-tint",
    )

    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            drawCue(
                state = state,
                tint = tint,
                breath = breath,
                spin = spin,
                progress = progress.value,
                scatter = scatter.value,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

private fun DrawScope.drawCue(
    state: CueState,
    tint: Color,
    breath: Float,
    spin: Float,
    progress: Float,
    scatter: Float,
) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val base = size.minDimension / 2f

    // Breathing: a slow sine, always running underneath everything else.
    val breathe = 1f + 0.05f * sin(breath * TAU).toFloat()

    // Per-state deformation of the core.
    val (scaleX, scaleY) = when (state) {
        is CueState.Correct -> squashStretch(progress)
        is CueState.LessonComplete -> {
            val s = 1f + 0.35f * bell(progress)
            s to s
        }
        else -> 1f to 1f
    }

    // Incorrect: a small horizontal wobble that decays. Never a droop —
    // Cue does not scold.
    val wobble = if (state is CueState.Incorrect) {
        sin(progress * TAU * 3).toFloat() * base * 0.16f * (1f - progress)
    } else {
        0f
    }

    // Listening: leans toward the input, pulses in rhythm with typing.
    val lean = (state as? CueState.Listening)?.let { base * 0.18f * it.intensity } ?: 0f
    val listenPulse = (state as? CueState.Listening)
        ?.let { 1f + 0.08f * it.intensity * sin(breath * TAU * 3).toFloat() } ?: 1f

    val core = centre + Offset(wobble + lean, 0f)
    val coreRadius = base * 0.42f * breathe * listenPulse * (1f - 0.45f * scatter)

    // --- glow ---------------------------------------------------------------
    // Drawn first and wide, so the orb sits in light rather than on top of it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0f)),
            center = core,
            radius = base * 0.98f,
        ),
        radius = base * 0.98f,
        center = core,
    )

    // --- streak rings -------------------------------------------------------
    (state as? CueState.Streak)?.let { streak ->
        repeat(streak.milestones.coerceAtMost(4)) { i ->
            val r = base * (0.58f + i * 0.13f)
            drawCircle(
                color = tint.copy(alpha = 0.45f - i * 0.08f),
                radius = r,
                center = core,
                style = Stroke(width = base * 0.035f),
            )
        }
    }

    // --- particles ----------------------------------------------------------
    // Always present. At rest they hug the core and read as a soft edge; under
    // scatter they become the swarm.
    val burst = when (state) {
        is CueState.Correct, is CueState.LessonComplete -> bell(progress)
        else -> 0f
    }

    if (scatter > 0.01f || burst > 0.01f) {
        repeat(PARTICLE_COUNT) { i ->
            val golden = i * 2.39996f                      // golden-angle: no visible banding
            val angle = golden + spin * TAU.toFloat() * (if (i % 2 == 0) 1f else -0.6f)
            val spread = base * (0.30f + 0.42f * scatter + 0.52f * burst)
            val radial = spread * (0.55f + 0.45f * ((i % 5) / 4f))

            val p = core + Offset(
                cos(angle) * radial,
                sin(angle) * radial * (0.85f + 0.15f * sin(breath * TAU).toFloat()),
            )
            val alpha = (0.20f + 0.55f * scatter + 0.7f * burst * (1f - progress)).coerceIn(0f, 1f)

            drawCircle(
                color = tint.copy(alpha = alpha),
                radius = base * (0.055f - 0.02f * scatter),
                center = p,
            )
        }
    }

    // --- the raccoon --------------------------------------------------------
    // Expression is derived from state, never stored. One source of truth for
    // "what is Cue feeling" — the same `state` that drives the motion above.
    val lidClose = when (state) {
        // Eyes squeeze shut on success, and reopen as the reaction settles.
        is CueState.Correct, is CueState.LessonComplete -> bell(progress).coerceAtMost(1f)
        // Thinking: half-lidded and looking away, the way anyone does while
        // working something out.
        is CueState.Thinking -> 0.35f
        // Idle and Listening blink on the slow irregular rhythm.
        else -> blinkAmount(breath)
    }

    val gaze = when (state) {
        is CueState.Thinking -> -0.9f + 0.5f * sin(breath * TAU * 0.5).toFloat()
        is CueState.Listening -> 0.7f
        is CueState.Incorrect -> sin(progress * TAU * 2).toFloat() * 0.5f
        else -> 0f
    }

    val smile = when (state) {
        is CueState.Correct, is CueState.LessonComplete, is CueState.Streak -> 1f
        // Deliberately not negative. A frown on a wrong answer is a small
        // punishment, repeated hundreds of times per course.
        else -> 0.15f
    }

    drawRaccoon(
        centre = core,
        r = coreRadius * 1.34f,
        tint = tint,
        scaleX = scaleX,
        scaleY = scaleY,
        lidClose = lidClose,
        gaze = gaze,
        smile = smile,
    )
}

/** Volume-preserving squash-stretch: stretch up first, then squash wide, then settle. */
private fun squashStretch(t: Float): Pair<Float, Float> {
    val s = bell(t)
    val stretch = 1f + 0.28f * s
    return (1f / stretch) to stretch
}

/** 0 -> 1 -> 0. The shape of every one-shot reaction in the app. */
private fun bell(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI).toFloat()

