package app.curio.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.curio.domain.Lesson
import app.curio.ui.theme.CurioTheme

/**
 * The teaching page. Shown once, before the first exercise.
 *
 * Deliberately short — a paragraph and a few bullets, not an article. The point
 * is to give the learner something to retrieve, not to replace the exercises
 * with reading. If this page ever grows past a screen, the lesson is too big and
 * should be split.
 *
 * Skippable, because someone reviewing a lesson they've already done should not
 * have to read it again.
 */
@Composable
fun LessonIntro(
    lesson: Lesson,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = space.gutter),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Spacer(Modifier.height(space.sm))

            Text(
                text = "LESSON ${lesson.position + 1}",
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = lesson.title,
                style = CurioTheme.type.display,
                color = colors.onSurface,
            )

            if (lesson.objective.isNotBlank()) {
                Text(
                    text = lesson.objective,
                    style = CurioTheme.type.body,
                    color = colors.onSurfaceMuted,
                )
            }

            if (lesson.summary.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CurioTheme.radii.card))
                        .background(colors.accentSubtle, RoundedCornerShape(CurioTheme.radii.card))
                        .padding(space.md),
                ) {
                    Text(
                        text = lesson.summary,
                        style = CurioTheme.type.prompt,
                        color = colors.onSurface,
                    )
                }
            }

            if (lesson.keyIdeas.isNotEmpty()) {
                Text(
                    text = "WORTH REMEMBERING",
                    style = CurioTheme.type.label,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = space.sm),
                )
                lesson.keyIdeas.forEach { idea ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .clip(RoundedCornerShape(CurioTheme.radii.pill))
                                .background(colors.accent),
                        )
                        Text(
                            text = idea,
                            style = CurioTheme.type.body,
                            color = colors.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(space.lg))
        }

        Box(Modifier.fillMaxWidth().padding(vertical = space.md)) {
            PrimaryButton(
                label = "Start · ${lesson.exercises.size} exercises",
                enabled = true,
                onTap = onStart,
            )
        }
    }
}
