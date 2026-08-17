package app.curio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.curio.domain.Exercise
import app.curio.grading.Confidence
import app.curio.grading.Grade
import app.curio.grading.TeachBackGrader
import app.curio.platform.Haptic
import app.curio.platform.Haptics
import app.curio.ui.theme.CurioTheme

/**
 * The differentiator. Retrieval-and-explain, in the learner's own words.
 *
 * NOTE — the one deliberate exception to "no submit buttons": free text has no
 * natural commit point. A sentence isn't finished just because you stopped
 * typing. Every other exercise commits on tap; this one needs an explicit "done"
 * and pretending otherwise would mean grading half-written thoughts.
 *
 * v0 grades on-device via [TeachBackGrader]. Aug 11 swaps that for a Groq call,
 * which is why grading is behind a function boundary rather than inline.
 */
@Composable
fun TeachBackExercise(
    exercise: Exercise.TeachBack,
    haptics: Haptics,
    onResult: OnExerciseResult,
    modifier: Modifier = Modifier,
    onTypingChanged: (Float) -> Unit = {},
    grade: (Exercise.TeachBack, String) -> Grade = TeachBackGrader::grade,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    var answer by remember(exercise.id) { mutableStateOf("") }
    var result by remember(exercise.id) { mutableStateOf<Grade?>(null) }
    val settled = result != null
    val ready = answer.trim().length >= exercise.minChars

    // Cue leans toward the field and pulses with typing. Intensity ramps with
    // length so it responds to effort, not keystrokes.
    LaunchedEffect(answer, settled) {
        onTypingChanged(
            if (settled) 0f else (answer.length / exercise.minChars.toFloat()).coerceIn(0f, 1f),
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(
            text = exercise.prompt,
            style = CurioTheme.type.prompt,
            color = colors.onSurface,
        )

        val shape = RoundedCornerShape(CurioTheme.radii.card)
        BasicTextField(
            value = answer,
            onValueChange = { if (!settled) answer = it },
            enabled = !settled,
            textStyle = CurioTheme.type.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(shape)
                .background(colors.surfaceRaised, shape)
                .border(
                    1.5.dp,
                    when {
                        result?.correct == true -> colors.success
                        settled -> colors.error
                        ready -> colors.accent
                        else -> colors.outline
                    },
                    shape,
                )
                .padding(space.md),
            decorationBox = { field ->
                if (answer.isEmpty()) {
                    Text(
                        text = "Explain it in your own words…",
                        style = CurioTheme.type.body,
                        color = colors.onSurfaceMuted,
                    )
                }
                field()
            },
        )

        if (!settled) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (ready) "Ready" else "${exercise.minChars - answer.trim().length} more characters",
                    style = CurioTheme.type.label,
                    color = if (ready) colors.accent else colors.onSurfaceMuted,
                )
                DoneButton(enabled = ready) {
                    val g = grade(exercise, answer)
                    result = g
                    haptics.play(if (g.correct) Haptic.CORRECT else Haptic.INCORRECT)
                    onResult(g.correct)
                }
            }
        }

        AnimatedVisibility(visible = settled) {
            result?.let { Feedback(it) }
        }
    }
}

@Composable
private fun DoneButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Text(
        text = "Done",
        style = CurioTheme.type.tile,
        color = if (enabled) colors.onAccent else colors.onSurfaceMuted,
        modifier = Modifier
            .clip(shape)
            .background(if (enabled) colors.accent else colors.surfaceSunken, shape)
            .tappable(interactionSource = interaction, enabled = enabled, onClick = onClick)
            .padding(horizontal = space.lg, vertical = space.sm),
    )
}

@Composable
private fun Feedback(grade: Grade) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.card)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (grade.correct) colors.successSubtle else colors.errorSubtle, shape)
            .padding(space.md),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Text(
            text = grade.feedback,
            style = CurioTheme.type.tile,
            color = if (grade.correct) colors.success else colors.error,
        )

        // Always show what was missed, right or wrong. A correct answer that
        // skipped two rubric points is exactly the moment the learner is most
        // receptive to them.
        grade.missed.forEach {
            Text("• $it", style = CurioTheme.type.body, color = colors.onSurface)
        }

        // Honesty about the grader's limits. It can tell you what you didn't
        // mention; it cannot tell you whether what you wrote is true.
        if (grade.confidence == Confidence.LOW) {
            Text(
                text = "Graded on what you recalled, not on wording.",
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
            )
        }
    }
}
