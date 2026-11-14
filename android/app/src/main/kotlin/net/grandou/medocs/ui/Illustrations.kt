package net.grandou.medocs.ui

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
import androidx.compose.ui.unit.dp

/** Dessins décoratifs des pages de prise en main : tracés en Canvas avec les couleurs du thème (clair et sombre). Muets pour TalkBack. */
enum class Dessin { PILULE, BOITE, CONSEIL, BOUCLIER, CLOCHE }

@Composable
fun IllustrationPage(dessin: Dessin) {
    val c = MaterialTheme.colorScheme
    val fond = c.primaryContainer; val trait = c.onPrimaryContainer
    val accent = c.tertiary; val doux = c.tertiaryContainer; val vif = c.primary; val sec = c.secondaryContainer
    Box(Modifier.fillMaxWidth().height(150.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(150.dp).fillMaxWidth()) {
            val s = size.height
            val centre = Offset(size.width / 2, s / 2)
            drawCircle(fond, radius = s * 0.46f, center = centre)
            // petites étincelles de part et d'autre
            drawCircle(doux, s * 0.07f, Offset(centre.x - s * 0.62f, centre.y - s * 0.22f))
            drawCircle(sec, s * 0.05f, Offset(centre.x + s * 0.64f, centre.y + s * 0.2f))
            drawCircle(doux, s * 0.04f, Offset(centre.x + s * 0.55f, centre.y - s * 0.34f))
            when (dessin) {
                Dessin.PILULE -> pilule(centre, s, vif, accent, trait)
                Dessin.BOITE -> boite(centre, s, c.surface, vif, accent, trait)
                Dessin.CONSEIL -> ampoule(centre, s, accent, c.surface, trait)
                Dessin.BOUCLIER -> bouclier(centre, s, vif, c.onPrimary)
                Dessin.CLOCHE -> cloche(centre, s, accent, vif, trait)
            }
        }
    }
}

private fun DrawScope.pilule(centre: Offset, s: Float, a: Color, b: Color, trait: Color) {
    val l = s * 0.62f; val h = s * 0.28f
    rotate(-40f, centre) {
        val x = centre.x - l / 2; val y = centre.y - h / 2
        drawRoundRect(a, Offset(x, y), Size(l / 2 + h / 2, h), CornerRadius(h / 2))
        drawRoundRect(b, Offset(centre.x - h / 2, y), Size(l / 2 + h / 2, h), CornerRadius(h / 2))
        drawLine(trait.copy(alpha = 0.35f), Offset(centre.x, y), Offset(centre.x, y + h), strokeWidth = s * 0.012f)
    }
}

private fun DrawScope.boite(centre: Offset, s: Float, face: Color, a: Color, b: Color, trait: Color) {
    val w = s * 0.5f; val h = s * 0.4f
    val x = centre.x - w / 2; val y = centre.y - h / 2 + s * 0.04f
    drawRoundRect(face, Offset(x, y), Size(w, h), CornerRadius(s * 0.04f))
    drawRoundRect(a, Offset(x, y), Size(w, h * 0.28f), CornerRadius(s * 0.04f))
    // code-barres
    var bx = x + w * 0.14f
    val barres = listOf(1f, 0.5f, 1.4f, 0.6f, 1f, 0.5f, 1.2f, 0.7f, 1f)
    barres.forEach { e ->
        drawRect(trait, Offset(bx, y + h * 0.42f), Size(s * 0.012f * e, h * 0.4f)); bx += s * 0.012f * e + s * 0.02f
    }
    // coche
    val p = Offset(x + w, y + h)
    drawCircle(b, s * 0.11f, p)
    drawLine(Color.White, Offset(p.x - s * 0.05f, p.y), Offset(p.x - s * 0.01f, p.y + s * 0.04f), s * 0.03f, StrokeCap.Round)
    drawLine(Color.White, Offset(p.x - s * 0.01f, p.y + s * 0.04f), Offset(p.x + s * 0.06f, p.y - s * 0.04f), s * 0.03f, StrokeCap.Round)
}

private fun DrawScope.ampoule(centre: Offset, s: Float, a: Color, culot: Color, trait: Color) {
    val r = s * 0.17f; val ct = Offset(centre.x, centre.y - s * 0.06f)
    for (i in 0..4) { // rayons
        val ang = Math.toRadians(-160.0 + i * 35.0)
        val d1 = r * 1.35f; val d2 = r * 1.75f
        drawLine(a, Offset(ct.x + (d1 * Math.cos(ang)).toFloat(), ct.y + (d1 * Math.sin(ang)).toFloat()),
            Offset(ct.x + (d2 * Math.cos(ang)).toFloat(), ct.y + (d2 * Math.sin(ang)).toFloat()), s * 0.025f, StrokeCap.Round)
    }
    drawCircle(a, r, ct)
    drawRoundRect(trait, Offset(centre.x - r * 0.55f, ct.y + r * 0.85f), Size(r * 1.1f, s * 0.12f), CornerRadius(s * 0.02f))
    drawLine(culot, Offset(centre.x - r * 0.4f, ct.y + r * 1.25f), Offset(centre.x + r * 0.4f, ct.y + r * 1.25f), s * 0.014f)
}

private fun DrawScope.bouclier(centre: Offset, s: Float, a: Color, coche: Color) {
    val w = s * 0.34f; val h = s * 0.42f
    val x = centre.x - w / 2; val y = centre.y - h / 2
    val p = Path().apply {
        moveTo(centre.x, y); lineTo(x + w, y + h * 0.15f); lineTo(x + w, y + h * 0.5f)
        quadraticTo(x + w, y + h * 0.85f, centre.x, y + h); quadraticTo(x, y + h * 0.85f, x, y + h * 0.5f); lineTo(x, y + h * 0.15f); close()
    }
    drawPath(p, a)
    val m = Offset(centre.x, centre.y + h * 0.02f)
    drawLine(coche, Offset(m.x - w * 0.22f, m.y), Offset(m.x - w * 0.05f, m.y + h * 0.14f), s * 0.035f, StrokeCap.Round)
    drawLine(coche, Offset(m.x - w * 0.05f, m.y + h * 0.14f), Offset(m.x + w * 0.24f, m.y - h * 0.14f), s * 0.035f, StrokeCap.Round)
}

private fun DrawScope.cloche(centre: Offset, s: Float, a: Color, point: Color, trait: Color) {
    val w = s * 0.36f; val h = s * 0.34f
    val x = centre.x - w / 2; val y = centre.y - h / 2 - s * 0.02f
    val p = Path().apply {
        moveTo(x, y + h); quadraticTo(x + w * 0.1f, y + h * 0.75f, x + w * 0.14f, y + h * 0.45f)
        cubicTo(x + w * 0.2f, y - h * 0.1f, x + w * 0.8f, y - h * 0.1f, x + w * 0.86f, y + h * 0.45f)
        quadraticTo(x + w * 0.9f, y + h * 0.75f, x + w, y + h); close()
    }
    drawPath(p, a)
    drawCircle(a, s * 0.035f, Offset(centre.x, y + h + s * 0.045f))
    drawCircle(trait, s * 0.02f, Offset(centre.x, y - h * 0.04f))
    // pastille de notification
    drawCircle(point, s * 0.07f, Offset(x + w * 0.92f, y + h * 0.12f))
}
