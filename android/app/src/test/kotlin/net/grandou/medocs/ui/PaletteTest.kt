package net.grandou.medocs.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Les contrastes de lecture de la palette, calculés comme le fait WCAG 2 (4,5:1 pour le texte, 3:1 pour les contours). */
class PaletteTest {
    private fun lineaire(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color): Double = 0.2126 * lineaire(c.red) + 0.7152 * lineaire(c.green) + 0.0722 * lineaire(c.blue)
    private fun contraste(a: Color, b: Color): Double {
        val (clair, fonce) = luminance(a).let { la -> luminance(b).let { lb -> if (la >= lb) la to lb else lb to la } }
        return (clair + 0.05) / (fonce + 0.05)
    }

    private fun verifier(nom: String, c: ColorScheme) {
        val textes = listOf(
            "onPrimary/primary" to (c.onPrimary to c.primary),
            "onPrimaryContainer/primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
            "onSecondary/secondary" to (c.onSecondary to c.secondary),
            "onSecondaryContainer/secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
            "onTertiary/tertiary" to (c.onTertiary to c.tertiary),
            "onTertiaryContainer/tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
            "onError/error" to (c.onError to c.error),
            "onErrorContainer/errorContainer" to (c.onErrorContainer to c.errorContainer),
            "onBackground/background" to (c.onBackground to c.background),
            "onSurface/surface" to (c.onSurface to c.surface),
            "onSurface/surfaceContainerLow" to (c.onSurface to c.surfaceContainerLow),
            "onSurface/surfaceContainer" to (c.onSurface to c.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (c.onSurface to c.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to (c.onSurface to c.surfaceContainerHighest),
            "onSurfaceVariant/surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
            "onSurfaceVariant/surface" to (c.onSurfaceVariant to c.surface),
            "onSurfaceVariant/surfaceContainerHighest" to (c.onSurfaceVariant to c.surfaceContainerHighest),
            "inverseOnSurface/inverseSurface" to (c.inverseOnSurface to c.inverseSurface),
            // Texte coloré posé sur les fonds courants (boutons contour, liens, indicateurs)
            "primary/surface" to (c.primary to c.surface),
            "primary/surfaceContainerHighest" to (c.primary to c.surfaceContainerHighest),
            "error/surface" to (c.error to c.surface),
            "error/surfaceContainerHighest" to (c.error to c.surfaceContainerHighest),
        )
        val manques = textes.filter { (_, p) -> contraste(p.first, p.second) < 4.5 }.map { (n, p) -> "$nom $n = %.2f".format(contraste(p.first, p.second)) }
        assertTrue("Contrastes insuffisants (< 4,5) :\n" + manques.joinToString("\n"), manques.isEmpty())
        // Contours de champs et de boutons : 3:1 contre le fond
        assertTrue("$nom outline/surface = %.2f".format(contraste(c.outline, c.surface)), contraste(c.outline, c.surface) >= 3.0)
    }

    @Test fun contrastesDuThemeClair() = verifier("clair", Palette.claire)
    @Test fun contrastesDuThemeSombre() = verifier("sombre", Palette.sombre)

    @Test fun lesDeuxThemesSontBienDistincts() {
        assertTrue(luminance(Palette.claire.background) > 0.8)
        assertTrue(luminance(Palette.sombre.background) < 0.05)
    }

    @Test fun leFondDeLaFenetreCorrespondAuFondDeLaPalette() {
        // res/values/colors.xml (#FBFCFF) et values-night/colors.xml (#191C1E) : pas de flash au lancement
        assertTrue(Palette.claire.background == Color(0xFFFBFCFF))
        assertTrue(Palette.sombre.background == Color(0xFF191C1E))
    }

    @Test fun leCalculDeContrasteEstJuste() {
        // Valeurs de référence WCAG : noir sur blanc = 21 ; #767676 sur blanc ≈ 4,54 ; deux couleurs identiques = 1
        assertTrue(kotlin.math.abs(contraste(Color.Black, Color.White) - 21.0) < 0.01)
        assertTrue(kotlin.math.abs(contraste(Color(0xFF767676), Color.White) - 4.54) < 0.02)
        assertTrue(contraste(Color.White, Color.White) == 1.0)
        assertTrue("un gris clair sur blanc doit être rejeté", contraste(Color(0xFFAAAAAA), Color.White) < 4.5)
    }
}
