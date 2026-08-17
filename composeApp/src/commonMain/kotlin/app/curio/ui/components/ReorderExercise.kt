package app.curio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.router.stableSeed
import app.curio.ui.theme.CurioTheme
import kotlin.random.Random

/**
 * Build the sequence by tapping steps in order.
 *
 * NOT drag-and-drop. Dragging on a phone is fiddly, needs a scroll-conflict
 * story, and costs days to get right — and Duolingo's own reorder is tap-based
 * for exactly that reason. Tapping also gives a natural commit point: the
 * exercise ends when the last step is placed. No submit button needed.
 *
 * A wrong tap is not blocked. Blocking would let the learner brute-force the
 * sequence by trying every tile, which teaches ordering by elimination rather
 * than by understanding.
 */
@Composable
fun ReorderExercise(
    exercise: Exercise.Reorder,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    val tray = remember(exercise.id) {
        exercise.items.shuffled(Random(exercise.id.stableSeed()))
    }
    val placed = remember(exercise.id) { mutableStateListOf<String>() }
    var settled by remember(exercise.id) { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.lg)) {
        Text(exercise.prompt, style = CurioTheme.type.prompt, color = colors.onSurface)

        // --- the sequence being built ---------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            exercise.items.indices.forEach { slot ->
                val text = placed.getOrNull(slot)
                val isRight = settled && text == exercise.items[slot]

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    Text(
                        text = "${slot + 1}",
                        style = CurioTheme.type.label,
                        color = colors.onSurfaceMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(20.dp),
                    )
                    SlotRow(
                        text = text,
                        // After settling, show the correct step in any slot they
                        // got wrong. "Wrong order" alone teaches nothing.
                        corrected = if (settled && !isRight) exercise.items[slot] else null,
                        state = when {
                            !settled -> if (text == null) SlotState.EMPTY else SlotState.FILLED
                            isRight -> SlotState.RIGHT
                            else -> SlotState.WRONG
                        },
                        onTap = {
                            // Tapping a placed step returns it and everything after
                            // it — the sequence is only meaningful in order.
                            if (settled || text == null) return@SlotRow
                            while (placed.size > slot) placed.removeAt(placed.size - 1)
                            haptics.play(Haptic.TAP)
                        },
                    )
                }
            }
        }

        // --- the tray -------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            tray.forEach { step ->
                val used = step in placed
                TrayStep(
                    text = step,
                    used = used,
                    enabled = !settled && !used,
                    onTap = {
                        placed += step
                        haptics.play(Haptic.SNAP)
                        if (placed.size == exercise.items.size) {
                            settled = true
                            val correct = placed.toList() == exercise.items
                            haptics.play(if (correct) Haptic.CORRECT else Haptic.INCORRECT)
                            onResult(correct)
                        }
                    },
                )
            }
        }
    }
}

private enum class SlotState { EMPTY, FILLED, RIGHT, WRONG }

@Composable
private fun SlotRow(
    text: String?,
    corrected: String?,
    state: SlotState,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.tile)
    val interaction = remember { MutableInteractionSource() }

    val outline by animateColorAsState(
        targetValue = when (state) {
            SlotState.EMPTY -> colors.outline
            SlotState.FILLED -> colors.accent
            SlotState.RIGHT -> colors.success
            SlotState.WRONG -> colors.error
        },
        label = "slot-outline",
    )
    val background by animateColorAsState(
        targetValue = when (state) {
            SlotState.EMPTY -> colors.surfaceSunken
            SlotState.FILLED -> colors.accentSubtle
            SlotState.RIGHT -> colors.successSubtle
            SlotState.WRONG -> colors.errorSubtle
        },
        label = "slot-bg",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = space.touchTarget)
            .clip(shape)
            .background(background, shape)
            .border(1.5.dp, outline, shape)
            .tappable(interactionSource = interaction, enabled = text != null, onClick = onTap)
            .padding(horizontal = space.md, vertical = space.sm),
    ) {
        Text(
            text = text ?: "",
            style = CurioTheme.type.tile,
            color = colors.onSurface,
        )
        if (corrected != null && corrected != text) {
            Text(
                text = corrected,
                style = CurioTheme.type.label,
                color = colors.success,
            )
        }
    }
}

@Composable
private fun TrayStep(
    text: String,
    used: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion
    val shape = RoundedCornerShape(CurioTheme.radii.tile)
    val interaction = remember { MutableInteractionSource() }

    // Ghosted rather than removed: the tray must not reflow under a finger.
    val alpha by animateFloatAsState(
        targetValue = if (used) 0.16f else 1f,
        animationSpec = motion.snap,
        label = "tray-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (used) 0.97f else 1f,
        animationSpec = motion.snap,
        label = "tray-scale",
    )

    Text(
        text = text,
        style = CurioTheme.type.tile,
        color = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .defaultMinSize(minHeight = space.touchTarget)
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.5.dp, colors.outline, shape)
            .tappable(interactionSource = interaction, enabled = enabled, onClick = onTap)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}
