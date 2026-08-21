package app.curio.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE design system. Never hardcode a colour, size, duration or spring after this file.
 *
 * Concept: a cabinet of curiosities — small, precise, collected things. Warm and
 * tactile, not clinical. Closer to a well-made physical object than an ed-tech
 * dashboard. Reference: Arc, Things 3, Bear, Monument Valley. Not ed-tech.
 */

// ---------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------

@Immutable
data class CurioColors(
    /** Page. Warm paper, never pure white. */
    val surface: Color,
    /** Cards, raised things. */
    val surfaceRaised: Color,
    /** Pressed / recessed wells (bucket zones, blanks). */
    val surfaceSunken: Color,
    val outline: Color,

    val onSurface: Color,
    val onSurfaceMuted: Color,

    /** The one warm accent. Cue's resting colour, progress rings, primary actions. */
    val accent: Color,
    val onAccent: Color,
    val accentSubtle: Color,

    val success: Color,
    val onSuccess: Color,
    val successSubtle: Color,

    val error: Color,
    val onError: Color,
    val errorSubtle: Color,

    val isDark: Boolean,
)

private val LightColors = CurioColors(
    surface = Color(0xFFFAF6F0),
    surfaceRaised = Color(0xFFFFFDFA),
    surfaceSunken = Color(0xFFF0E9DF),
    outline = Color(0xFFE2D8CA),

    onSurface = Color(0xFF1F1B16),
    onSurfaceMuted = Color(0xFF7A7066),

    accent = Color(0xFFD97534),
    onAccent = Color(0xFFFFFDFA),
    accentSubtle = Color(0xFFFBEADC),

    success = Color(0xFF2F7D5E),
    onSuccess = Color(0xFFFFFDFA),
    successSubtle = Color(0xFFDFEFE6),

    error = Color(0xFFB33A3A),
    onError = Color(0xFFFFFDFA),
    errorSubtle = Color(0xFFF7E1DE),

    isDark = false,
)

private val DarkColors = CurioColors(
    surface = Color(0xFF16130F),
    surfaceRaised = Color(0xFF211D18),
    surfaceSunken = Color(0xFF0F0D0A),
    outline = Color(0xFF3A332B),

    onSurface = Color(0xFFF2EBE1),
    onSurfaceMuted = Color(0xFF9C9186),

    accent = Color(0xFFF08B45),
    onAccent = Color(0xFF16130F),
    accentSubtle = Color(0xFF3A2618),

    success = Color(0xFF4FB98C),
    onSuccess = Color(0xFF16130F),
    successSubtle = Color(0xFF17322A),

    error = Color(0xFFE8695F),
    onError = Color(0xFF16130F),
    errorSubtle = Color(0xFF3A1F1D),

    isDark = true,
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/**
 * Type carries identity. One display face, one text face.
 *
 * These are still the SYSTEM faces, and that is why the app currently looks
 * generic — the system serif is the same one every unstyled Android app gets.
 * Swapping them is the largest single visual improvement available and it costs
 * about ten minutes:
 *
 *   1. Download two variable fonts (SIL Open Font License, safe to ship):
 *        display — Fraunces or Instrument Serif   fonts.google.com
 *        text    — Inter Tight or Figtree
 *   2. Drop the .ttf files in:
 *        composeApp/src/commonMain/composeResources/font/
 *   3. Replace the two values below with:
 *        FontFamily(Font(Res.font.fraunces_variable))
 *      importing curio.composeapp.generated.resources.Res
 *
 * Everything downstream reads from this file, so nothing else changes.
 */
private val DisplayFace = FontFamily.Serif
private val TextFace = FontFamily.SansSerif

@Immutable
data class CurioTypography(
    val display: TextStyle,
    val title: TextStyle,
    /** Exercise prompts. The most-read style in the app — tune this one first. */
    val prompt: TextStyle,
    val body: TextStyle,
    /** Word tiles, chips, option labels. */
    val tile: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

private val DefaultTypography = CurioTypography(
    // 30/38 rather than 34/40: at 34sp a two-line serif heading crowds whatever
    // sits under it on a 5" screen, and the app's first screen is exactly that
    // case. Looser leading buys more than the extra 4sp of size did.
    display = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    ),
    title = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    prompt = TextStyle(
        fontFamily = TextFace,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 30.sp,
    ),
    body = TextStyle(
        fontFamily = TextFace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    tile = TextStyle(
        fontFamily = TextFace,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    label = TextStyle(
        fontFamily = TextFace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
)

// ---------------------------------------------------------------------------
// Space & shape
// ---------------------------------------------------------------------------

@Immutable
data class CurioSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    /** Horizontal page gutter. Every screen uses this, nothing else. */
    val gutter: Dp = 20.dp,
    /** Minimum tappable dimension. Non-negotiable — there are no submit buttons to fall back on. */
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class CurioRadii(
    val tile: Dp = 12.dp,
    val card: Dp = 20.dp,
    val sheet: Dp = 28.dp,
    val pill: Dp = 999.dp,
)

// ---------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------

/**
 * Animation is the differentiator, so the springs live in the theme and get
 * tuned in one place. Design directly in Compose — you cannot feel a spring
 * curve in a static mock.
 */
@Immutable
data class CurioMotion(
    /** Tiles snapping into blanks, pairs locking. Tight, physical, slightly overshooting. */
    val snap: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    ),
    /** Cue's squash-stretch on a correct answer. */
    val bounce: SpringSpec<Float> = spring(
        dampingRatio = 0.42f,
        stiffness = Spring.StiffnessMediumLow,
    ),
    /** Screen-level and shared-element transitions. Calm, no overshoot. */
    val settle: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    ),
    /** Cue's idle breathing period, ms. */
    val breathMillis: Int = 3200,
    /** One page turn between exercises. Physical, not a fade. */
    val pageTurnMillis: Int = 380,
    /** How long a correct/incorrect colour flash holds before the page turns. */
    val feedbackHoldMillis: Int = 620,
    val pageTurnEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f),
)

// ---------------------------------------------------------------------------
// Wiring
// ---------------------------------------------------------------------------

val LocalCurioColors: ProvidableCompositionLocal<CurioColors> =
    staticCompositionLocalOf { LightColors }
val LocalCurioTypography: ProvidableCompositionLocal<CurioTypography> =
    staticCompositionLocalOf { DefaultTypography }
val LocalCurioSpacing: ProvidableCompositionLocal<CurioSpacing> =
    staticCompositionLocalOf { CurioSpacing() }
val LocalCurioRadii: ProvidableCompositionLocal<CurioRadii> =
    staticCompositionLocalOf { CurioRadii() }
val LocalCurioMotion: ProvidableCompositionLocal<CurioMotion> =
    staticCompositionLocalOf { CurioMotion() }

object CurioTheme {
    val colors: CurioColors
        @Composable get() = LocalCurioColors.current
    val type: CurioTypography
        @Composable get() = LocalCurioTypography.current
    val space: CurioSpacing
        @Composable get() = LocalCurioSpacing.current
    val radii: CurioRadii
        @Composable get() = LocalCurioRadii.current
    val motion: CurioMotion
        @Composable get() = LocalCurioMotion.current
}

@Composable
fun CurioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalCurioColors provides colors,
        LocalCurioTypography provides DefaultTypography,
        LocalCurioSpacing provides CurioSpacing(),
        LocalCurioRadii provides CurioRadii(),
        LocalCurioMotion provides CurioMotion(),
    ) {
        // Material3 is present only so its components inherit sane colours.
        // Curio's own composables read CurioTheme, never MaterialTheme.
        MaterialTheme(
            colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    background = colors.surface,
                    onBackground = colors.onSurface,
                    surface = colors.surfaceRaised,
                    onSurface = colors.onSurface,
                    error = colors.error,
                )
            } else {
                lightColorScheme(
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    background = colors.surface,
                    onBackground = colors.onSurface,
                    surface = colors.surfaceRaised,
                    onSurface = colors.onSurface,
                    error = colors.error,
                )
            },
            typography = Typography(),
            content = content,
        )
    }
}
