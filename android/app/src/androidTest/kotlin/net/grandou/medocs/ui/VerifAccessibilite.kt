package net.grandou.medocs.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.Density

private fun <T> prop(n: SemanticsNode, k: SemanticsPropertyKey<T>): T? = if (n.config.contains(k)) n.config[k] else null

/**
 * Contrôles d'accessibilité sur l'arbre sémantique (celui que lit TalkBack) :
 * tout élément cliquable affiché a une zone tactile d'au moins 48 x 48 dp et un libellé (texte ou description).
 * (Les vérifications automatiques d'Android intégrées à Compose ne détectaient rien ici : voir CLAUDE.md.)
 */
fun ComposeTestRule.problemesDAccessibilite(densite: Density): List<String> {
    val problemes = mutableListOf<String>()
    onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { n ->
        val b = n.touchBoundsInRoot
        if (b.width <= 0f || b.height <= 0f) return@forEach // pas affiché (hors de l'écran)
        val l = b.width / densite.density
        val h = b.height / densite.density
        val libelle = (prop(n, SemanticsProperties.Text)?.joinToString { it.text }
            ?: prop(n, SemanticsProperties.ContentDescription)?.joinToString()).orEmpty()
        if (l < 47.5f || h < 47.5f) problemes += "zone tactile ${l.toInt()}x${h.toInt()} dp < 48 : « $libelle »"
        if (libelle.isBlank()) problemes += "élément cliquable sans libellé à ${b.left.toInt()},${b.top.toInt()}"
    }
    return problemes
}
