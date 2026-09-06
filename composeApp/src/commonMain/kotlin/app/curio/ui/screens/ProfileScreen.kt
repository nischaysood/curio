package app.curio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.curio.auth.CurioApi
import app.curio.auth.ProfileBody
import app.curio.billing.Billing
import app.curio.billing.Limits
import app.curio.billing.Tier
import app.curio.billing.Usage
import app.curio.billing.rememberBilling
import app.curio.platform.Haptic
import app.curio.platform.rememberHaptics
import app.curio.ui.components.tappable
import app.curio.ui.cue.Cue
import app.curio.ui.cue.CueState
import app.curio.ui.theme.CurioTheme
import kotlinx.coroutines.launch

/**
 * Account, usage, and the upgrade.
 *
 * Also where both stores expect to find Restore Purchases and a privacy policy
 * link — a missing restore path is one of the most common rejection reasons for
 * a subscription app, and it's simply fair besides: changing phones shouldn't
 * cost you what you already paid for.
 *
 * Everything here works signed out. Someone who skipped registration still gets
 * their usage, the upgrade, and restore; they're shown what an account would
 * add rather than being blocked by its absence.
 */
@Composable
fun ProfileScreen(
    tier: Tier,
    usage: Usage,
    onUpgrade: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    api: CurioApi = remember { CurioApi() },
    billing: Billing = rememberBilling(),
) {
    val colors = CurioTheme.colors
    val space = CurioTheme.space
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<ProfileBody?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Server figures are authoritative — they survive reinstalls, the local
    // counters don't. Missing (offline, signed out) falls back to local rather
    // than showing nothing.
    LaunchedEffect(Unit) { profile = api.profile() }

    val isPremium = tier == Tier.PREMIUM
    val lessonsUsed = profile?.usage?.lessons ?: usage.lessonsCompleted
    val generationsUsed = profile?.usage?.generations ?: usage.generations

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.gutter),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = space.md),
            horizontalArrangement = Arrangement.Start,
        ) {
            BackButton(onTap = onBack)
        }

        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Cue(state = if (isPremium) CueState.Streak(3) else CueState.Idle, size = 68.dp)
        }

        Text(
            text = profile?.email ?: "Not signed in",
            style = CurioTheme.type.title,
            color = colors.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (isPremium) "Premium" else "Free plan",
            style = CurioTheme.type.label,
            color = if (isPremium) colors.accent else colors.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth().padding(top = space.xs),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(space.lg))

        // --- today ------------------------------------------------------------
        Card {
            SectionLabel("Today")
            StatRow(
                label = "Lessons",
                value = if (isPremium) "$lessonsUsed" else "$lessonsUsed of ${Limits.FREE_LESSONS_PER_DAY}",
            )
            StatRow(
                label = "New courses",
                value = if (isPremium) {
                    "$generationsUsed of ${Limits.PREMIUM_GENERATIONS_PER_DAY}"
                } else {
                    "$generationsUsed of ${Limits.FREE_GENERATIONS_PER_DAY}"
                },
            )
            Text(
                text = "Resets at midnight UTC.",
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = space.xs),
            )
        }

        // --- all time ---------------------------------------------------------
        profile?.let { p ->
            Spacer(Modifier.height(space.md))
            Card {
                SectionLabel("All time")
                StatRow(label = "Courses", value = "${p.courses}")
                StatRow(label = "Lessons finished", value = "${p.lessonsCompleted}")
            }
        }

        Spacer(Modifier.height(space.lg))

        // --- upgrade ----------------------------------------------------------
        if (!isPremium) {
            PrimaryButton(
                label = "Go premium",
                enabled = !busy,
                onTap = {
                    haptics.play(Haptic.TAP)
                    onUpgrade()
                },
            )
            Spacer(Modifier.height(space.sm))
        }

        SecondaryButton(
            label = if (busy) "Checking…" else "Restore purchases",
            onTap = {
                if (busy) return@SecondaryButton
                busy = true
                message = null
                scope.launch {
                    val restored = billing.restore()
                    message = if (restored == Tier.PREMIUM) {
                        haptics.play(Haptic.COMPLETE)
                        "Premium restored."
                    } else {
                        "No previous purchase found on this account."
                    }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(space.sm))

        if (profile == null) {
            // Signed out. Framed as something to gain, not a wall — the app works
            // fine without an account and shouldn't imply otherwise.
            SecondaryButton(label = "Sign in to save progress", onTap = onSignIn)
        } else {
            SecondaryButton(
                label = "Sign out",
                onTap = {
                    if (busy) return@SecondaryButton
                    busy = true
                    scope.launch {
                        api.logout()
                        busy = false
                        onSignedOut()
                    }
                },
            )
        }

        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = message.orEmpty(),
                style = CurioTheme.type.label,
                color = colors.onSurfaceMuted,
                modifier = Modifier.alpha(if (message != null) 1f else 0f),
            )
        }

        // --- legal ------------------------------------------------------------
        // Required by both stores for a subscription app, and it has to be
        // reachable from inside the app, not only from the store listing.
        val interaction = remember { MutableInteractionSource() }
        Text(
            text = "Privacy policy",
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CurioTheme.radii.pill))
                .tappable(interactionSource = interaction, onClick = onOpenPrivacy)
                .padding(vertical = space.sm),
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Curio $APP_VERSION",
            style = CurioTheme.type.label,
            color = colors.onSurfaceMuted.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(bottom = space.lg),
            textAlign = TextAlign.Center,
        )
    }
}

/** Kept in step with versionName in build.gradle.kts by hand — one line, checked at release. */
private const val APP_VERSION = "0.1.0"

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(CurioTheme.radii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CurioTheme.colors.surfaceRaised, shape)
            .border(1.dp, CurioTheme.colors.outline, shape)
            .padding(CurioTheme.space.md),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = CurioTheme.type.label,
        color = CurioTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(bottom = CurioTheme.space.sm),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = CurioTheme.space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CurioTheme.type.body, color = CurioTheme.colors.onSurface)
        Text(value, style = CurioTheme.type.tile, color = CurioTheme.colors.onSurface)
    }
}

@Composable
private fun BackButton(onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CurioTheme.radii.pill)
    Box(
        Modifier
            .size(CurioTheme.space.touchTarget)
            .clip(shape)
            .background(CurioTheme.colors.surfaceRaised, shape)
            .tappable(interactionSource = interaction, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text("←", style = CurioTheme.type.tile, color = CurioTheme.colors.onSurfaceMuted)
    }
}
