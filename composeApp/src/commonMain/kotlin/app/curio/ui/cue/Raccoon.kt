package app.curio.ui.cue

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cue, drawn as a raccoon.
 *
 * Still no assets — one Canvas, same as the orb it replaces. That matters more
 * than it sounds: a vector mascot scales to any size, tints itself from the
 * theme, animates per-frame, and adds nothing to the APK. A PNG would need five
 * densities and still look wrong in dark mode.
 *
 * Everything here is proportional to [r], the head radius, so the whole face
 * scales from one number. Hard-coded pixel offsets are how a mascot ends up
 * looking correct at 72dp and broken at 120dp.
 */

/** Raccoon palette. Fixed rather than themed — a raccoon is grey in every colour scheme. */
private val FUR = Color(0xFF8D8B93)
private val FUR_LIGHT = Color(0xFFC9C6CE)
private val MASK = Color(0xFF32303A)
private val MUZZLE = Color(0xFFF3F0EA)
private val NOSE = Color(0xFF2A2830)

internal fun DrawScope.drawRaccoon(
    centre: Offset,
    r: Float,
    tint: Color,
    scaleX: Float,
    scaleY: Float,
    /** 0f = eyes open, 1f = fully closed. Drives both blinking and the happy squint. */
    lidClose: Float,
    /** Horizontal gaze offset in units of r. Negative looks left. */
    gaze: Float,
    /** 0f = neutral mouth, 1f = full smile. */
    smile: Float,
) {
    // Squash-stretch is applied by scaling the geometry rather than the canvas,
    // so the stroke widths below don't get squashed along with the shapes.
    val rx = r * scaleX
    val ry = r * scaleY

    // --- ears ---------------------------------------------------------------
    // Drawn first so the head overlaps their bases and they read as attached
    // rather than stuck on.
    listOf(-1f, 1f).forEach { side ->
        val earCentre = centre + Offset(side * rx * 0.72f, -ry * 0.74f)
        drawCircle(color = FUR, radius = r * 0.30f, center = earCentre)
        drawCircle(
            color = tint.copy(alpha = 0.55f),
            radius = r * 0.16f,
            center = earCentre + Offset(0f, r * 0.04f),
        )
    }

    // --- head ---------------------------------------------------------------
    drawOval(
        color = FUR,
        topLeft = centre - Offset(rx, ry * 0.94f),
        size = Size(rx * 2f, ry * 1.88f),
    )

    // Lighter forehead blaze — the vertical stripe that reads instantly as
    // "raccoon" even at 24dp, where the mask is only a few pixels tall.
    drawOval(
        color = FUR_LIGHT,
        topLeft = centre - Offset(rx * 0.20f, ry * 0.92f),
        size = Size(rx * 0.40f, ry * 0.85f),
    )

    // --- mask ---------------------------------------------------------------
    // Two lobes rather than one band. A single rectangle across the eyes looks
    // like a blindfold; separated lobes look like fur.
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = MASK,
            topLeft = centre + Offset(side * rx * 0.62f - rx * 0.46f, -ry * 0.36f),
            size = Size(rx * 0.92f, ry * 0.62f),
        )
    }

    // --- eyes ---------------------------------------------------------------
    listOf(-1f, 1f).forEach { side ->
        val eye = centre + Offset(side * rx * 0.40f + gaze * rx * 0.16f, -ry * 0.06f)
        val eyeR = r * 0.155f

        if (lidClose > 0.85f) {
            // Closed: an upward arc, not a flat line. A flat line reads as
            // asleep or dead; the arc reads as pleased.
            drawArc(
                color = MUZZLE,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = eye - Offset(eyeR, eyeR),
                size = Size(eyeR * 2f, eyeR * 2f),
                style = Stroke(width = r * 0.06f),
            )
        } else {
            drawCircle(color = MUZZLE, radius = eyeR, center = eye)
            drawCircle(
                color = NOSE,
                radius = eyeR * (0.62f - 0.5f * lidClose).coerceAtLeast(0.08f),
                center = eye + Offset(gaze * eyeR * 0.35f, 0f),
            )
            // Catchlight. One tiny white dot is the difference between "eye" and
            // "hole", and it costs one draw call.
            drawCircle(
                color = Color.White.copy(alpha = 0.9f - lidClose),
                radius = eyeR * 0.22f,
                center = eye + Offset(-eyeR * 0.22f, -eyeR * 0.28f),
            )
        }
    }

    // --- muzzle -------------------------------------------------------------
    val muzzleTop = centre + Offset(0f, ry * 0.22f)
    drawOval(
        color = MUZZLE,
        topLeft = muzzleTop - Offset(rx * 0.44f, 0f),
        size = Size(rx * 0.88f, ry * 0.56f),
    )

    // --- nose ---------------------------------------------------------------
    val noseCentre = muzzleTop + Offset(0f, ry * 0.14f)
    val noseW = rx * 0.20f
    val nosePath = Path().apply {
        moveTo(noseCentre.x - noseW, noseCentre.y - noseW * 0.55f)
        lineTo(noseCentre.x + noseW, noseCentre.y - noseW * 0.55f)
        // Rounded chin on the triangle so it reads soft rather than sharp.
        quadraticBezierTo(
            noseCentre.x, noseCentre.y + noseW * 1.15f,
            noseCentre.x - noseW, noseCentre.y - noseW * 0.55f,
        )
        close()
    }
    drawPath(nosePath, color = NOSE)

    // --- mouth --------------------------------------------------------------
    // Two arcs meeting under the nose. At smile = 0 they're nearly flat, so the
    // neutral face is calm rather than sad — Cue never scolds, and a downturned
    // mouth on a wrong answer is exactly that.
    val mouthY = noseCentre.y + ry * 0.16f
    val sweep = 40f + 70f * smile
    listOf(-1f, 1f).forEach { side ->
        val arcR = rx * 0.20f
        drawArc(
            color = NOSE.copy(alpha = 0.75f),
            startAngle = if (side < 0) 0f else 180f - sweep,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(
                noseCentre.x + side * arcR - arcR,
                mouthY - arcR,
            ),
            size = Size(arcR * 2f, arcR * 2f),
            style = Stroke(width = r * 0.05f),
        )
    }

    // --- whiskers -----------------------------------------------------------
    listOf(-1f, 1f).forEach { side ->
        repeat(2) { i ->
            val y = mouthY - ry * 0.02f + i * ry * 0.11f
            val start = Offset(noseCentre.x + side * rx * 0.34f, y)
            drawLine(
                color = FUR_LIGHT.copy(alpha = 0.85f),
                start = start,
                end = start + Offset(side * rx * 0.42f, -ry * 0.06f + i * ry * 0.08f),
                strokeWidth = r * 0.028f,
            )
        }
    }
}

/**
 * Blink timing.
 *
 * Real blinks are irregular; a metronome reads as a machine. Two offset sines
 * with incommensurate periods give a pattern that never visibly repeats, for the
 * price of two sin() calls and no extra animation state.
 */
internal fun blinkAmount(t: Float): Float {
    val a = sin(t * 2.0 * PI * 0.9).toFloat()
    val b = sin(t * 2.0 * PI * 0.37 + 1.3).toFloat()
    val combined = (a * 0.6f + b * 0.4f)
    // Only the very top of the wave counts, so eyes are open the vast majority
    // of the time and the blink itself is fast.
    return ((combined - 0.86f) / 0.14f).coerceIn(0f, 1f)
}
