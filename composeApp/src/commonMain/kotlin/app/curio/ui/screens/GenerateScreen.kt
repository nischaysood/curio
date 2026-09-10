package app.curio.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.curio.data.CourseCatalog
import app.curio.data.CourseResult
import app.curio.auth.CourseSummary
import app.curio.auth.CurioApi
import app.curio.data.CourseSource
import app.curio.data.CurioConfig
import app.curio.domain.Course
import app.curio.domain.Depth
import app.curio.platform.Haptic
import app.curio.platform.rememberHaptics
import app.curio.ui.components.tappable
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.launch

/**
 * The front door. Type a topic, pick a depth, watch a course get built.
 *
 * This screen carries the entire product promise, so it does exactly one thing.
 * No nav bar, no account, no settings — a question, a field, and a button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateScreen(
    onCourseReady: (Course) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    source: CourseSource = remember { CurioConfig.courseSource() },
    api: CurioApi = remember { CurioApi() },
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val haptics = rememberHaptics()

    var topic by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf(Depth.STANDARD) }
    var error by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<CourseSummary>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Loaded once per visit to this screen. Empty when signed out or offline,
    // in which case the section simply isn't drawn — no spinner, no error for
    // something the user didn't ask for.
    LaunchedEffect(Unit) { history = api.courses() }

    val ready = topic.trim().length >= 3 && !building

    fun submit() {
        if (building) return
        if (topic.trim().length < 3) {
            error = "Type something you want to learn"
            return
        }
        error = null
        building = true
        haptics.play(Haptic.TAP)

        scope.launch {
            when (val result = source.course(topic, depth)) {
                is CourseResult.Ready -> {
                    haptics.play(Haptic.CORRECT)
                    onCourseReady(result.course)
                }
                // Honest failures, every one of them. Serving an unrelated course
                // because we couldn't build the right one would be worse than
                // admitting the library is small.
                is CourseResult.NotFound -> {
                    error = "No course for that yet. Try one below."
                    haptics.play(Haptic.INCORRECT)
                }
                is CourseResult.Refused -> {
                    error = result.reason
                    haptics.play(Haptic.INCORRECT)
                }
                is CourseResult.Failed -> {
                    error = result.message
                    haptics.play(Haptic.INCORRECT)
                }
            }
            building = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            // Scrollable because a keyboard eats half a small screen, and the
            // suggestion chips must stay reachable while the field has focus.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter)
            .padding(bottom = space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Profile sits top-right, over the Cue's row rather than above it, so the
        // first screen keeps its single-focus feel — one field, one question —
        // instead of growing a toolbar.
        Row(
            Modifier.fillMaxWidth().padding(top = space.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            ProfileButton(onTap = onOpenProfile)
        }

        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            // Cue fragments into particles while the course is being built. This
            // is the "no spinners anywhere" rule — the wait is shown by the
            // mascot doing something, not by a progress indicator.
            Cue(
                state = if (building) CueState.Thinking else CueState.Idle,
                size = 88.dp,
            )
        }

        Text(
            text = "What do you\nwant to learn?",
            style = CurioTheme.type.display,
            color = colors.onSurface,
            modifier = Modifier.fillMaxWidth().padding(bottom = space.xl),
        )

        TopicField(
            value = topic,
            onValueChange = {
                topic = it
                error = null
            },
            onSubmit = ::submit,
        )

        FlowRow(
            Modifier.fillMaxWidth().padding(top = space.md),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Depth.entries.forEach { option ->
                DepthChip(
                    depth = option,
                    selected = depth == option,
                    onTap = {
                        depth = option
                        haptics.play(Haptic.TAP)
                    },
                )
            }
        }

        // Reserve the row whether or not there's an error, so the button never
        // jumps under a finger that's already moving toward it. Fading rather
        // than AnimatedVisibility keeps the height fixed either way.
        val errorAlpha by animateFloatAsState(
            targetValue = if (error != null) 1f else 0f,
            label = "error-alpha",
        )
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = error.orEmpty(),
                style = CurioTheme.type.label,
                color = colors.error,
                modifier = Modifier.alpha(errorAlpha),
            )
        }

        PrimaryButton(
            label = if (building) "Building…" else "Build my course",
            enabled = ready,
            onTap = ::submit,
        )

        // --- history ----------------------------------------------------------
        // The payoff for having an account. Without this, signing up changes
        // nothing the user can see, which makes it feel like a data grab rather
        // than a feature.
        if (history.isNotEmpty()) {
            Text(
                text = "CONTINUE",
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.fillMaxWidth().padding(top = space.xl, bottom = space.sm),
            )

            history.forEach { summary ->
                CourseRow(
                    topic = summary.topic,
                    completed = summary.completed,
                    onTap = {
                        // Re-open through the normal generation path: the topic
                        // hash is already in KV, so this is a cache hit and
                        // returns in milliseconds rather than a regeneration.
                        topic = summary.topic
                        depth = runCatching { Depth.valueOf(summary.depth) }.getOrDefault(depth)
                        haptics.play(Haptic.TAP)
                        submit()
                    },
                )
                Spacer(Modifier.height(space.sm))
            }
        }

        Text(
            text = if (history.isEmpty()) "OR START WITH" else "OR TRY",
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth().padding(top = space.xl, bottom = space.sm),
        )

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            CourseCatalog.suggestions.forEach { suggestion ->
                SuggestionChip(suggestion) {
                    topic = suggestion
                    error = null
                    haptics.play(Haptic.TAP)
                }
            }
        }
    }
}

/**
 * One course in the history list.
 *
 * Shows the topic and how many lessons are done, and nothing else. A progress
 * bar was tempting here and wrong: the row's job is to get you back into the
 * course in one tap, and decoration competes with that.
 */
@Composable
private fun CourseRow(topic: String, completed: Int, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.card)
    val interaction = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = topic,
            style = CurioTheme.type.tile,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = space.sm),
        )
        Text(
            text = if (completed == 0) "Not started" else "$completed done",
            style = CurioTheme.type.label,
            color = if (completed == 0) colors.onSurfaceMuted else colors.accent,
        )
    }
}

/**
 * Entry to the profile.
 *
 * A raccoon head rather than a gear or an avatar: the mascot is already the
 * app's most recognisable element, and reusing it means one fewer icon to draw
 * and no ambiguity about what's behind it.
 */
@Composable
private fun ProfileButton(onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CurioTheme.radii.pill)

    Box(
        Modifier
            .size(CurioTheme.space.touchTarget)
            .clip(shape)
            .background(CurioTheme.colors.surfaceRaised, shape)
            .border(1.dp, CurioTheme.colors.outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Cue(state = CueState.Idle, size = 26.dp)
    }
}

@Composable
private fun TopicField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.card)

    val outline by animateColorAsState(
        targetValue = if (value.isNotBlank()) colors.accent else colors.outline,
        label = "topic-outline",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = CurioTheme.type.prompt.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.5.dp, outline, shape)
            .padding(horizontal = CurioTheme.space.md, vertical = CurioTheme.space.md),
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(
                    text = "How compilers work",
                    style = CurioTheme.type.prompt,
                    color = colors.onSurfaceMuted,
                )
            }
            field()
        },
    )
}

@Composable
private fun DepthChip(depth: Depth, selected: Boolean, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { MutableInteractionSource() }

    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentSubtle else colors.surfaceRaised,
        label = "depth-bg",
    )
    val line by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.outline,
        label = "depth-line",
    )

    Text(
        text = depth.name.lowercase().replaceFirstChar { it.uppercase() },
        style = CurioTheme.type.label,
        color = if (selected) colors.accent else colors.onSurfaceMuted,
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, line, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(horizontal = CurioTheme.space.md, vertical = CurioTheme.space.sm),
    )
}

@Composable
private fun SuggestionChip(text: String, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { MutableInteractionSource() }

    Text(
        text = text,
        style = CurioTheme.type.tile,
        color = colors.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.5.dp, colors.outline, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(horizontal = CurioTheme.space.md, vertical = CurioTheme.space.sm),
    )
}

@Composable
internal fun PrimaryButton(label: String, enabled: Boolean, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    val interaction = remember { MutableInteractionSource() }

    // Not-yet state is an OUTLINE, not a grey fill. A filled grey button reads as
    // broken; an outline reads as waiting for you. It stays tappable either way so
    // it can say what's missing instead of going dead under a finger.
    val bg by animateColorAsState(
        targetValue = if (enabled) colors.accent else colors.surface,
        label = "cta-bg",
    )
    val line by animateColorAsState(
        targetValue = if (enabled) colors.accent else colors.outline,
        label = "cta-line",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, line, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(vertical = CurioTheme.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CurioTheme.type.tile,
            color = if (enabled) colors.onAccent else colors.onSurfaceMuted,
        )
    }
}
