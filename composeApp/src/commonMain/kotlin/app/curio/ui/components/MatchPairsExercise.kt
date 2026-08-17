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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.router.stableSeed
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Tap left, tap right. A correct pair locks and flies off; a wrong one flashes
 * and clears. No submit button — the exercise ends when the board is empty.
 *
 * Scoring: correct only if the whole board is cleared with no mismatches. Match
 * pairs is trivially brute-forceable, so counting a fumbled board as correct
 * would make it free marks.
 */
@Composable
fun MatchPairsExercise(
    exercise: Exercise.MatchPairs,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    val rng = remember(exercise.id) { Random(exercise.id.stableSeed()) }
    val lefts = remember(exercise.id) { exercise.pairs.map { it.left }.shuffled(rng) }
    val rights = remember(exercise.id) { exercise.pairs.map { it.right }.shuffled(rng) }

    val matched = remember(exercise.id) { mutableStateListOf<String>() }
    var selectedLeft by remember(exercise.id) { mutableStateOf<String?>(null) }
    var selectedRight by remember(exercise.id) { mutableStateOf<String?>(null) }
    var wrongFlash by remember(exercise.id) { mutableStateOf(false) }
    var mistakes by remember(exercise.id) { mutableIntStateOf(0) }
    var reported by remember(exercise.id) { mutableStateOf(false) }

    // Resolve a full selection: lock it, or flash and clear.
    LaunchedEffect(selectedLeft, selectedRight) {
        val l = selectedLeft
        val r = selectedRight
        if (l == null || r == null) return@LaunchedEffect

        val isPair = exercise.pairs.any { it.left == l && it.right == r }
        if (isPair) {
            haptics.play(Haptic.LOCK)
            matched += l
            matched += r
        } else {
            haptics.play(Haptic.INCORRECT)
            mistakes++
            wrongFlash = true
            delay(420)          // let the learner see WHICH pairing was wrong
            wrongFlash = false
        }
        selectedLeft = null
        selectedRight = null
    }

    LaunchedEffect(matched.size) {
        if (reported || matched.size < exercise.pairs.size * 2) return@LaunchedEffect
        reported = true
        delay(240)              // let the last pair finish flying off
        onResult(mistakes == 0)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(exercise.prompt, style = CurioTheme.type.prompt, color = colors.onSurface)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                lefts.forEach { left ->
                    PairCard(
                        text = left,
                        gone = left in matched,
                        selected = selectedLeft == left,
                        wrong = wrongFlash && selectedLeft == left,
                        onTap = {
                            haptics.play(Haptic.TAP)
                            selectedLeft = if (selectedLeft == left) null else left
                        },
                    )
                }
            }

            Column(
                Modifier.weight(1.3f),   // definitions are longer than terms
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                rights.forEach { right ->
                    PairCard(
                        text = right,
                        gone = right in matched,
                        selected = selectedRight == right,
                        wrong = wrongFlash && selectedRight == right,
                        onTap = {
                            haptics.play(Haptic.TAP)
                            selectedRight = if (selectedRight == right) null else right
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PairCard(
    text: String,
    gone: Boolean,
    selected: Boolean,
    wrong: Boolean,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion
    val shape = RoundedCornerShape(CurioTheme.radii.tile)
    val interaction = remember { MutableInteractionSource() }

    // "Flies off": scales down and fades rather than collapsing the layout, so the
    // remaining cards never jump under the learner's finger mid-tap.
    val scale by animateFloatAsState(
        targetValue = if (gone) 0.85f else 1f,
        animationSpec = motion.snap,
        label = "pair-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (gone) 0f else 1f,
        animationSpec = motion.snap,
        label = "pair-alpha",
    )
    val outline by animateColorAsState(
        targetValue = when {
            wrong -> colors.error
            selected -> colors.accent
            else -> colors.outline
        },
        label = "pair-outline",
    )
    val background by animateColorAsState(
        targetValue = when {
            wrong -> colors.errorSubtle
            selected -> colors.accentSubtle
            else -> colors.surfaceRaised
        },
        label = "pair-bg",
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
            .background(background, shape)
            .border(1.5.dp, outline, shape)
            .tappable(interactionSource = interaction, enabled = !gone, onClick = onTap)
            .padding(horizontal = space.sm, vertical = space.sm),
    )
}
