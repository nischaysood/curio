package app.curio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.router.stableSeed
import app.curio.ui.theme.CurioTheme
import kotlin.random.Random

/**
 * Sentence with blanks, word tiles below, tiles snap in.
 *
 * Tapping a tile fills the leftmost empty blank; tapping a filled blank returns
 * its tile to the tray. The exercise commits itself the moment the last blank is
 * filled — there is no submit button anywhere in Curio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapToFillExercise(
    exercise: Exercise.TapToFill,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    // Seeded by the exercise id so the tray order is stable across recompositions,
    // process death, and every device that opens this cached course.
    val tray = remember(exercise.id) {
        exercise.tiles.shuffled(Random(exercise.id.stableSeed()))
    }

    // Index into [tray] for each blank; null = empty.
    val filled = remember(exercise.id) {
        mutableStateListOf<Int?>().apply { repeat(exercise.blanks.size) { add(null) } }
    }
    var state by remember(exercise.id) { mutableStateOf(AnswerState.OPEN) }

    fun commitIfComplete() {
        if (filled.any { it == null }) return
        val correct = filled.indices.all { i ->
            tray[filled[i]!!].equals(exercise.blanks[i], ignoreCase = true)
        }
        state = if (correct) AnswerState.CORRECT else AnswerState.INCORRECT
        haptics.play(if (correct) Haptic.CORRECT else Haptic.INCORRECT)
        onResult(correct)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.lg)) {
        Text(
            text = exercise.prompt,
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted,
        )

        // --- the sentence ---------------------------------------------------
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            exercise.segments.forEachIndexed { i, segment ->
                // Split segments into words so the sentence wraps like text
                // rather than like a row of boxes.
                segment.trim().split(" ").filter { it.isNotBlank() }.forEach { word ->
                    Text(
                        text = word,
                        style = CurioTheme.type.prompt,
                        color = colors.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                if (i < exercise.blanks.size) {
                    Blank(
                        text = filled[i]?.let { tray[it] },
                        state = state,
                        expected = exercise.blanks[i],
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onTap = {
                            if (state.isSettled) return@Blank
                            if (filled[i] != null) {
                                filled[i] = null
                                haptics.play(Haptic.TAP)
                            }
                        },
                    )
                }
            }
        }

        // --- the tray -------------------------------------------------------
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            tray.forEachIndexed { index, word ->
                val used = filled.contains(index)
                Tile(
                    text = word,
                    used = used,
                    enabled = !state.isSettled && !used,
                    onTap = {
                        val slot = filled.indexOfFirst { it == null }
                        if (slot < 0) return@Tile
                        filled[slot] = index
                        haptics.play(Haptic.SNAP)
                        commitIfComplete()
                    },
                )
            }
        }
    }
}

@Composable
private fun Blank(
    text: String?,
    state: AnswerState,
    expected: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val motion = CurioTheme.motion
    val shape = RoundedCornerShape(CurioTheme.radii.tile)
    val interaction = remember { MutableInteractionSource() }

    val outline by animateColorAsState(
        targetValue = when {
            state == AnswerState.CORRECT -> colors.success
            state == AnswerState.INCORRECT -> colors.error
            text != null -> colors.accent
            else -> colors.outline
        },
        label = "blank-outline",
    )

    // The snap: a filled blank arrives slightly oversized and settles.
    val snap by animateFloatAsState(
        targetValue = if (text != null) 1f else 0.94f,
        animationSpec = motion.snap,
        label = "blank-snap",
    )

    // On a wrong answer, show what it should have been. "Wrong" alone teaches nothing.
    val reveal = state == AnswerState.INCORRECT && !expected.equals(text, ignoreCase = true)

    Text(
        text = if (reveal) expected else (text ?: " ".repeat(6)),
        style = CurioTheme.type.prompt,
        textAlign = TextAlign.Center,
        color = when {
            reveal -> colors.error
            text != null -> colors.onSurface
            else -> Color.Transparent
        },
        modifier = modifier
            .scale(snap)
            .widthIn(min = 72.dp)
            .clip(shape)
            .background(if (text != null) colors.accentSubtle else colors.surfaceSunken, shape)
            .border(1.5.dp, outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(horizontal = CurioTheme.space.sm, vertical = CurioTheme.space.xs),
    )
}

@Composable
private fun Tile(
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

    // A used tile doesn't disappear — it leaves a ghost, so the tray never reflows
    // under the user's finger mid-tap.
    val alpha by animateFloatAsState(
        targetValue = if (used) 0.18f else 1f,
        animationSpec = motion.snap,
        label = "tile-alpha",
    )

    Text(
        text = text,
        style = CurioTheme.type.tile,
        color = colors.onSurface.copy(alpha = alpha),
        modifier = Modifier
            .defaultMinSize(minHeight = space.touchTarget)
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = alpha), shape)
            .border(1.5.dp, colors.outline.copy(alpha = alpha), shape)
            .tappable(interactionSource = interaction, enabled = enabled, onClick = onTap)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}
