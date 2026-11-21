package net.grandou.rappels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Decorative drawings for empty states and tab headers. */
enum class Drawing {
    /** A free day: calendar with a sun. */
    FREE_DAY,

    /** Everything done: big check mark and confetti. */
    ALL_DONE,

    /** Nothing tracked yet: the app's box with its check bubble. */
    EMPTY_BOX,

    /** "Plus" tab: the app icon. */
    APP_ICON,
}

/**
 * Drawn with theme colors only (the five icon colors and their shades), so it follows light and dark mode.
 * Silent for TalkBack: the text next to it says it all.
 */
@Composable
fun Illustration(drawing: Drawing, height: Dp = 150.dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(height).fillMaxWidth()) {
            val s = size.height
            val center = Offset(size.width / 2, s / 2)
            drawCircle(c.primaryContainer, radius = s * 0.46f, center = center)
            // little sparkles on both sides
            drawCircle(c.tertiaryContainer, s * 0.07f, Offset(center.x - s * 0.62f, center.y - s * 0.22f))
            drawCircle(c.secondaryContainer, s * 0.05f, Offset(center.x + s * 0.64f, center.y + s * 0.2f))
            drawCircle(c.tertiaryContainer, s * 0.04f, Offset(center.x + s * 0.55f, center.y - s * 0.34f))
            when (drawing) {
                Drawing.FREE_DAY -> freeDay(center, s, c.surface, c.primary, c.tertiaryContainer, c.onPrimaryContainer)
                Drawing.ALL_DONE -> allDone(center, s, c.primary, c.onPrimary, c.tertiaryContainer, c.secondary)
                Drawing.EMPTY_BOX -> emptyBox(center, s, c.surface, c.primary, c.tertiaryContainer, c.onPrimaryContainer, c.onPrimary)
                Drawing.APP_ICON -> appIcon(center, s)
            }
        }
    }
}

private fun DrawScope.check(center: Offset, s: Float, color: Color, width: Float) {
    drawLine(color, Offset(center.x - s * 0.09f, center.y + s * 0.01f), Offset(center.x - s * 0.02f, center.y + s * 0.08f), width, StrokeCap.Round)
    drawLine(color, Offset(center.x - s * 0.02f, center.y + s * 0.08f), Offset(center.x + s * 0.1f, center.y - s * 0.07f), width, StrokeCap.Round)
}

private fun DrawScope.freeDay(center: Offset, s: Float, face: Color, header: Color, sun: Color, ring: Color) {
    val w = s * 0.5f
    val h = s * 0.46f
    val x = center.x - w / 2
    val y = center.y - h / 2
    drawRoundRect(face, Offset(x, y), Size(w, h), CornerRadius(s * 0.05f))
    drawRoundRect(header, Offset(x, y), Size(w, h * 0.26f), CornerRadius(s * 0.05f))
    for (i in 0..1) {
        drawRoundRect(ring, Offset(x + w * (0.25f + i * 0.5f) - s * 0.015f, y - s * 0.04f), Size(s * 0.03f, s * 0.09f), CornerRadius(s * 0.015f))
    }
    val c = Offset(center.x, y + h * 0.64f)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        drawLine(
            sun, Offset(c.x + s * 0.075f * cos(a).toFloat(), c.y + s * 0.075f * sin(a).toFloat()),
            Offset(c.x + s * 0.105f * cos(a).toFloat(), c.y + s * 0.105f * sin(a).toFloat()), s * 0.016f, StrokeCap.Round,
        )
    }
    drawCircle(sun, s * 0.055f, c)
}

private fun DrawScope.allDone(center: Offset, s: Float, disc: Color, mark: Color, a: Color, b: Color) {
    drawCircle(disc, s * 0.2f, center)
    check(center, s, mark, s * 0.045f)
    val tints = listOf(a, b, disc, a, b, disc)
    for (i in 0 until 6) {
        val ang = Math.toRadians(-90.0 + i * 60.0 + 20.0)
        val d = s * 0.3f
        val p = Offset(center.x + d * cos(ang).toFloat(), center.y + d * sin(ang).toFloat())
        rotate(i * 35f, p) { drawRoundRect(tints[i], Offset(p.x - s * 0.02f, p.y - s * 0.035f), Size(s * 0.04f, s * 0.07f), CornerRadius(s * 0.01f)) }
    }
}

/** The icon's box, simplified: front face, top, two text lines and the check bubble. */
private fun DrawScope.emptyBox(center: Offset, s: Float, face: Color, top: Color, amber: Color, text: Color, mark: Color) {
    val w = s * 0.5f
    val h = s * 0.32f
    val x = center.x - w / 2
    val y = center.y - h / 2 + s * 0.02f
    drawRoundRect(face, Offset(x, y), Size(w, h), CornerRadius(s * 0.03f))
    drawRoundRect(top, Offset(x, y), Size(w, h * 0.26f), CornerRadius(s * 0.03f))
    drawRoundRect(amber, Offset(x + w * 0.1f, y + h * 0.48f), Size(w * 0.5f, s * 0.026f), CornerRadius(s * 0.013f))
    drawRoundRect(text.copy(alpha = 0.35f), Offset(x + w * 0.1f, y + h * 0.72f), Size(w * 0.36f, s * 0.018f), CornerRadius(s * 0.009f))
    val p = Offset(x + w, y + h)
    drawCircle(amber, s * 0.125f, p)
    drawCircle(top, s * 0.1f, p)
    check(Offset(p.x + s * 0.005f, p.y), s * 0.8f, mark, s * 0.03f)
}

/** The launcher icon (geometry of AGENTS.md, 108 viewport) on a blue disc, without the 85 % safe-zone scaling. */
private fun DrawScope.appIcon(center: Offset, s: Float) {
    drawCircle(IconColors.Blue, radius = s * 0.46f, center = center)
    val u = s * 0.0075f
    // The drawing spans x 21..87, y 28..83: center it on the disc.
    fun p(x: Float, y: Float) = Offset(center.x + (x - 54f) * u, center.y + (y - 55.5f) * u)
    fun poly(color: Color, vararg pts: Pair<Float, Float>) {
        val path = Path().apply {
            pts.forEachIndexed { i, (x, y) -> p(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        drawPath(path, color)
    }
    drawRect(IconColors.White, p(21f, 34f), Size(50 * u, 32 * u))
    poly(IconColors.Top, 21f to 34f, 27f to 28f, 77f to 28f, 71f to 34f)
    poly(IconColors.Side, 71f to 34f, 77f to 28f, 77f to 60f, 71f to 66f)
    drawRect(IconColors.Amber, p(27f, 40f), Size(26 * u, 6 * u))
    drawRect(IconColors.Amber, p(27f, 51f), Size(22 * u, 3 * u))
    drawCircle(IconColors.Amber, 17 * u, p(70f, 66f))
    drawCircle(IconColors.White, 13.5f * u, p(70f, 66f))
    val a = p(63.5f, 66f)
    val b = p(68.5f, 71f)
    val d = p(77f, 61f)
    drawLine(IconColors.Blue, a, b, 3.4f * u, StrokeCap.Round)
    drawLine(IconColors.Blue, b, d, 3.4f * u, StrokeCap.Round)
}
