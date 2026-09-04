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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import app.curio.billing.Billing
import app.curio.billing.Period
import app.curio.billing.Product
import app.curio.billing.PurchaseResult
import app.curio.billing.Tier
import app.curio.billing.rememberBilling
import app.curio.platform.Haptic
import app.curio.platform.rememberHaptics
import app.curio.ui.components.tappable
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.launch

/**
 * Shown after lesson 3 — value felt before the ask.
 *
 * Deliberately not a hard wall: [onDismiss] always works and nothing here is
 * urgent, countdown-timered or guilt-shaped. Someone who says no should be able
 * to keep using the free tier without being nagged, because a paywall that
 * resents you is one people uninstall rather than pay.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onPurchased: () -> Unit,
    modifier: Modifier = Modifier,
    billing: Billing = rememberBilling(),
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        products = billing.offerings()
        // Default to annual when it exists — it's the better deal for the user
        // and the better retention for us, so nobody has to be tricked into it.
        selected = products.firstOrNull { it.period == Period.ANNUAL }?.id ?: products.firstOrNull()?.id
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = space.md),
            horizontalArrangement = Arrangement.End,
        ) {
            DismissButton(onTap = onDismiss)
        }

        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Cue(state = CueState.Streak(milestones = 3), size = 80.dp)
        }

        Text(
            text = "Keep going",
            style = CurioTheme.type.display,
            color = colors.onSurface,
        )
        Text(
            text = "You've finished three lessons today. Premium removes the daily limit.",
            style = CurioTheme.type.body,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = space.sm, bottom = space.lg),
        )

        listOf(
            "Unlimited lessons every day",
            "As many courses as you want",
            "Deep mode — longer, denser courses",
            "Download courses and learn offline",
            "Full review history",
        ).forEach { benefit ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = space.sm),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(CurioTheme.radii.pill))
                        .background(colors.accent),
                )
                Text(benefit, style = CurioTheme.type.body, color = colors.onSurface)
            }
        }

        Spacer(Modifier.height(space.lg))

        if (products.isEmpty()) {
            // Store not reachable, or products not configured yet. Say so plainly
            // rather than showing an empty box with a dead button under it.
            Text(
                text = "Plans aren't loading right now. You can keep using Curio free.",
                style = CurioTheme.type.body,
                color = colors.onSurfaceMuted,
            )
        } else {
            products.forEach { product ->
                PlanRow(
                    product = product,
                    selected = selected == product.id,
                    onTap = {
                        selected = product.id
                        haptics.play(Haptic.TAP)
                    },
                )
                Spacer(Modifier.height(space.sm))
            }
        }

        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = message.orEmpty(),
                style = CurioTheme.type.label,
                color = colors.error,
                modifier = Modifier.alpha(if (message != null) 1f else 0f),
            )
        }

        PrimaryButton(
            label = if (busy) "One moment…" else "Go premium",
            enabled = selected != null && !busy,
            onTap = {
                val id = selected ?: return@PrimaryButton
                if (busy) return@PrimaryButton
                busy = true
                message = null
                scope.launch {
                    when (val result = billing.purchase(id)) {
                        is PurchaseResult.Success -> {
                            haptics.play(Haptic.COMPLETE)
                            onPurchased()
                        }
                        // Backing out is a normal choice. No error, no nagging.
                        is PurchaseResult.Cancelled -> Unit
                        is PurchaseResult.Failed -> {
                            message = result.message
                            haptics.play(Haptic.INCORRECT)
                        }
                    }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(space.sm))

        // Both stores require a visible restore path, and a missing one is a
        // common rejection. It's also just fair: paying twice for the same thing
        // because you changed phones is not okay.
        SecondaryButton(
            label = "Restore purchases",
            onTap = {
                if (busy) return@SecondaryButton
                busy = true
                scope.launch {
                    val tier = billing.restore()
                    if (tier == Tier.PREMIUM) {
                        haptics.play(Haptic.COMPLETE)
                        onPurchased()
                    } else {
                        message = "No previous purchase found on this account."
                    }
                    busy = false
                }
            },
        )

        Text(
            text = "Cancel anytime in the Play Store. Curio stays free without it.",
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(vertical = space.md),
        )
    }
}

@Composable
private fun PlanRow(product: Product, selected: Boolean, onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val shape = RoundedCornerShape(CurioTheme.radii.card)
    val interaction = remember { MutableInteractionSource() }

    val line by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.outline,
        label = "plan-line",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentSubtle else colors.surfaceRaised,
        label = "plan-bg",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.5.dp, line, shape)
            .tappable(interactionSource = interaction, onClick = onTap)
            .padding(space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = if (product.period == Period.ANNUAL) "Annual" else "Monthly",
                style = CurioTheme.type.tile,
                color = colors.onSurface,
            )
            product.savings?.let {
                Text(it, style = CurioTheme.type.label, color = colors.accent)
            }
        }
        // The store's own localised string. Never reformat a price.
        Text(product.price, style = CurioTheme.type.tile, color = colors.onSurface)
    }
}

@Composable
private fun DismissButton(onTap: () -> Unit) {
    val colors = CurioTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CurioTheme.radii.pill)

    Box(
        Modifier
            .size(CurioTheme.space.touchTarget)
            .clip(shape)
            .background(colors.surfaceRaised, shape)
            .tappable(interactionSource = interaction, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", style = CurioTheme.type.tile, color = colors.onSurfaceMuted)
    }
}
