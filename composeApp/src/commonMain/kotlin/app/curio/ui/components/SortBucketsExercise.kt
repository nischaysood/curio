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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import kotlin.random.Random

/**
 * Tap a chip, tap a bucket. Commits when the last chip is assigned.
 *
 * Tap-to-assign rather than drag for the same reasons as Reorder: dragging into
 * small zones on a phone is imprecise, and precision failures read as *your* bug,
 * not as the learner's mistake.
 *
 * A chip already in a bucket can be tapped to pull it back out, so the whole
 * board stays revisable until the last chip lands.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortBucketsExercise(
    exercise: Exercise.SortBuckets,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    val chips = remember(exercise.id) {
        exercise.items.map { it.text }.shuffled(Random(exercise.id.stableSeed()))
    }
    /** chip text -> bucket it's been placed in. */
    val assigned = remember(exercise.id) { mutableStateMapOf<String, String>() }
    var selected by remember(exercise.id) { mutableStateOf<String?>(null) }
    var settled by remember(exercise.id) { mutableStateOf(false) }

    fun correctBucketFor(chip: String): String? =
        exercise.items.firstOrNull { it.text == chip }?.bucket

    fun commitIfComplete() {
        if (assigned.size < chips.size) return
        settled = true
        val allRight = chips.all { assigned[it] == correctBucketFor(it) }
        haptics.play(if (allRight) Haptic.CORRECT else Haptic.INCORRECT)
        onResult(allRight)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(exercise.prompt, style = CurioTheme.type.prompt, color = colors.onSurface)

        // --- unassigned chips -----------------------------------------------
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            chips.filter { it !in assigned }.forEach { chip ->
                Chip(
                    text = chip,
                    selected = selected == chip,
                    state = ChipState.LOOSE,
                    enabled = !settled,
                    onTap = {
                        haptics.play(Haptic.TAP)
                        selected = if (selected == chip) null else chip
                    },
                )
            }
        }

        // --- buckets ---------------------------------------------------------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            exercise.buckets.forEach { bucket ->
                Bucket(
                    label = bucket,
                    armed = selected != null && !settled,
                    modifier = Modifier.weight(1f),
                    onTap = {
                        val chip = selected ?: return@Bucket
                        assigned[chip] = bucket
                        selected = null
                        haptics.play(Haptic.SNAP)
                        commitIfComplete()
                    },
                ) {
                    assigned.entries
                        .filter { it.value == bucket }
                        .map { it.key }
                        .sorted()
                        .forEach { chip ->
                            Chip(
                                text = chip,
                                selected = false,
                                state = when {
                                    !settled -> ChipState.PLACED
                                    assigned[chip] == correctBucketFor(chip) -> ChipState.RIGHT
                                    else -> ChipState.WRONG
                                },
                                enabled = !settled,
                                onTap = {
                                    assigned.remove(chip)
                                    haptics.play(Haptic.TAP)
                                },
                            )
                        }
                }
            }
        }

        if (settled) {
            // Name the ones that moved. A red chip tells you it's wrong; it
            // doesn't tell you where it belonged.
            chips.filter { assigned[it] != correctBucketFor(it) }.forEach { chip ->
                Text(
                    text = "$chip → ${correctBucketFor(chip)}",
                    style = CurioTheme.type.label,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

private enum class ChipState { LOOSE, PLACED, RIGHT, WRONG }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bucket(
    label: String,
    armed: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.card)
    val interaction = remember { MutableInteractionSource() }

    // When a chip is selected, every bucket lifts slightly — showing where it can
    // go without an arrow or a tooltip.
    val outline by animateColorAsState(
        targetValue = if (armed) colors.accent else colors.outline,
        label = "bucket-outline",
    )

    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(colors.surfaceSunken, shape)
            .border(1.5.dp, outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(space.sm),
        verticalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        Text(
            text = label,
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            content()
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    state: ChipState,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val motion = CurioTheme.motion
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { MutableInteractionSource() }

    val lift by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = motion.snap,
        label = "chip-lift",
    )
    val background by animateColorAsState(
        targetValue = when {
            selected -> colors.accent
            state == ChipState.RIGHT -> colors.successSubtle
            state == ChipState.WRONG -> colors.errorSubtle
            else -> colors.surfaceRaised
        },
        label = "chip-bg",
    )
    val outline by animateColorAsState(
        targetValue = when (state) {
            ChipState.RIGHT -> colors.success
            ChipState.WRONG -> colors.error
            else -> if (selected) colors.accent else colors.outline
        },
        label = "chip-outline",
    )

    Text(
        text = text,
        style = CurioTheme.type.tile,
        color = if (selected) colors.onAccent else colors.onSurface,
        modifier = Modifier
            .scale(lift)
            .alpha(if (enabled) 1f else 0.9f)
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.5.dp, outline, shape)
            .tappable(interactionSource = interaction, enabled = enabled, onClick = onTap)
            .padding(horizontal = space.sm, vertical = space.xs),
    )
}
