package app.curio.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.curio.auth.AuthOutcome
import app.curio.auth.CurioApi
import app.curio.platform.Haptic
import app.curio.platform.rememberHaptics
import app.curio.ui.components.tappable
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.launch

/**
 * Sign up or log in.
 *
 * Reachable, never mandatory. Curio generates and teaches perfectly well signed
 * out — an account buys you history across devices and nothing else. Forcing
 * registration before anyone has seen the product is the single most reliable
 * way to lose them, so [onSkip] is always available and never styled as a
 * lesser choice.
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    api: CurioApi = remember { CurioApi() },
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var isNewAccount by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Local validation only gates the button — the server validates properly.
    // This exists so the button looks dead before you tap it rather than after.
    val canSubmit = email.contains('@') && password.length >= 8 && !busy

    fun submit() {
        if (!canSubmit) return
        busy = true
        error = null
        scope.launch {
            val result = if (isNewAccount) api.signup(email, password) else api.login(email, password)
            when (result) {
                is AuthOutcome.Success -> {
                    haptics.play(Haptic.COMPLETE)
                    onAuthenticated()
                }
                is AuthOutcome.Failed -> {
                    error = result.message
                    haptics.play(Haptic.INCORRECT)
                }
            }
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Cue(state = CueState.Idle, size = 72.dp)
        }

        Text(
            text = if (isNewAccount) "Save your progress" else "Welcome back",
            style = CurioTheme.type.display,
            color = colors.onSurface,
        )
        Text(
            text = if (isNewAccount) {
                "An account keeps your courses when you change phones."
            } else {
                "Log in to pick up where you left off."
            },
            style = CurioTheme.type.body,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = space.sm, bottom = space.lg),
        )

        AuthField(
            value = email,
            onValueChange = { email = it.trim(); error = null },
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onSubmit = {},
        )

        Spacer(Modifier.height(space.sm))

        AuthField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = "At least 8 characters",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go,
            masked = true,
            onSubmit = { submit() },
        )

        // Fixed-height slot. Without it the buttons jump down when an error
        // appears, and a moving target is how you tap the wrong thing.
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = error.orEmpty(),
                style = CurioTheme.type.label,
                color = colors.error,
                modifier = Modifier.alpha(if (error != null) 1f else 0f),
            )
        }

        PrimaryButton(
            label = when {
                busy -> "One moment…"
                isNewAccount -> "Create account"
                else -> "Log in"
            },
            enabled = canSubmit,
            onTap = { submit() },
        )

        Spacer(Modifier.height(space.sm))

        SecondaryButton(
            label = if (isNewAccount) "I already have an account" else "Create one instead",
            onTap = {
                isNewAccount = !isNewAccount
                error = null
                haptics.play(Haptic.TAP)
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = space.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = "Keep learning without an account",
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(CurioTheme.radii.pill))
                    .tappable(interactionSource = interaction, onClick = onSkip)
                    .padding(horizontal = space.md, vertical = space.sm),
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
    masked: Boolean = false,
) {
    val colors = CurioTheme.colors
    val shape = RoundedCornerShape(CurioTheme.radii.card)

    val outline by animateColorAsState(
        targetValue = if (value.isNotBlank()) colors.accent else colors.outline,
        label = "auth-outline",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = CurioTheme.type.body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .border(1.5.dp, outline, shape)
            .padding(horizontal = CurioTheme.space.md, vertical = CurioTheme.space.md),
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(placeholder, style = CurioTheme.type.body, color = colors.onSurfaceMuted)
            }
            field()
        },
    )
}
