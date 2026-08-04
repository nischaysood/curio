package app.curio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.ui.theme.CurioTheme

/**
 * Tap an option -> instant colour + haptic. No submit button, no confirmation step.
 *
 * The delay between tap and feedback is the single most important number in this
 * composable: it must be zero. Colour and haptic fire in the same frame as the tap;
 * the page turn is what waits.
 */
@Composable
fun MultipleChoiceExercise(
    exercise: Exercise.MultipleChoice,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    var chosen by remember(exercise.id) { mutableStateOf<Int?>(null) }
    val state = when (chosen) {
        null -> AnswerState.OPEN
        exercise.correctIndex -> AnswerState.CORRECT
        else -> AnswerState.INCORRECT
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Text(
            text = exercise.prompt,
            style = CurioTheme.type.prompt,
            color = colors.onSurface,
            modifier = Modifier.padding(bottom = space.md),
        )

        exercise.options.forEachIndexed { index, option ->
            OptionRow(
                text = option,
                // After a wrong answer we reveal the right one. Leaving the user
                // with only "wrong" teaches nothing.
                highlight = when {
                    !state.isSettled -> OptionHighlight.NONE
                    index == exercise.correctIndex -> OptionHighlight.CORRECT
                    index == chosen -> OptionHighlight.WRONG
                    else -> OptionHighlight.DIMMED
                },
                enabled = !state.isSettled,
                onTap = {
                    chosen = index
                    val correct = index == exercise.correctIndex
                    haptics.play(if (correct) Haptic.CORRECT else Haptic.INCORRECT)
                    onResult(correct)
                },
            )
        }
    }
}

private enum class OptionHighlight { NONE, CORRECT, WRONG, DIMMED }

@Composable
private fun OptionRow(
    text: String,
    highlight: OptionHighlight,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val background by animateColorAsState(
        targetValue = when (highlight) {
            OptionHighlight.NONE -> colors.surfaceRaised
            OptionHighlight.CORRECT -> colors.successSubtle
            OptionHighlight.WRONG -> colors.errorSubtle
            OptionHighlight.DIMMED -> colors.surfaceRaised
        },
        label = "option-bg",
    )

    val outline by animateColorAsState(
        targetValue = when (highlight) {
            OptionHighlight.NONE -> colors.outline
            OptionHighlight.CORRECT -> colors.success
            OptionHighlight.WRONG -> colors.error
            OptionHighlight.DIMMED -> colors.outline
        },
        label = "option-outline",
    )

    // Presses feel physical, not like a web button.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = motion.snap,
        label = "option-press",
    )

    val dim = if (highlight == OptionHighlight.DIMMED) 0.45f else 1f
    val shape = RoundedCornerShape(CurioTheme.radii.tile)

    Text(
        text = text,
        style = CurioTheme.type.tile,
        color = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .scale(press)
            .graphicsLayer { alpha = dim }
            .clip(shape)
            .background(background, shape)
            .border(1.5.dp, outline, shape)
            .defaultMinSize(minHeight = space.touchTarget)
            .tappable(interactionSource = interaction, enabled = enabled, onClick = onTap)
            .padding(horizontal = space.md, vertical = space.md),
    )
}
