package app.curio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.curio.data.sampleCourse
import app.curio.ui.screens.LessonScreen
import app.curio.ui.theme.CurioTheme

/**
 * Aug 3 scope: one hand-written course, one lesson, playable end to end.
 *
 * Home / Generate / Path land Aug 8-9. Navigation is deliberately absent — a nav
 * library on day one is scaffolding for screens that do not exist yet.
 */
@Composable
fun App() {
    CurioTheme {
        val course = remember { sampleCourse() }
        var score by remember { mutableStateOf<Int?>(null) }

        Box(Modifier.fillMaxSize().background(CurioTheme.colors.surface)) {
            val done = score
            if (done == null) {
                LessonScreen(
                    lesson = course.lessons.first(),
                    onComplete = { score = it },
                )
            } else {
                Text(
                    text = "$done correct",
                    style = CurioTheme.type.display,
                    color = CurioTheme.colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
