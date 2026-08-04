package app.curio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/**
 * Curio never uses Material's ripple.
 *
 * Feedback is scale + colour + haptic, tuned in Theme.kt. A ripple would be a
 * second, unowned feedback language layered on top of ours — and it is the
 * clearest tell of a stock Material app.
 *
 * Callers pass their own [interactionSource] so they can read the press state
 * and animate it themselves.
 */
fun Modifier.tappable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
