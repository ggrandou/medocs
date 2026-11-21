package net.grandou.rappels.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG AA contrasts (4.5:1) of the charter's "background / text" pairs, in light and dark. */
class PaletteTest {
    private fun lum(c: Color): Double {
        fun f(x: Float) = x.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * f(c.red) + 0.7152 * f(c.green) + 0.0722 * f(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (x, y) = lum(a).let { la -> lum(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (x + 0.05) / (y + 0.05)
    }

    private fun pairs(c: ColorScheme) = mapOf(
        "primary" to (c.primary to c.onPrimary),
        "primaryContainer" to (c.primaryContainer to c.onPrimaryContainer),
        "secondary" to (c.secondary to c.onSecondary),
        "secondaryContainer" to (c.secondaryContainer to c.onSecondaryContainer),
        "tertiary" to (c.tertiary to c.onTertiary),
        "tertiaryContainer" to (c.tertiaryContainer to c.onTertiaryContainer),
        "error" to (c.error to c.onError),
        "errorContainer" to (c.errorContainer to c.onErrorContainer),
        "background" to (c.background to c.onBackground),
        "surface" to (c.surface to c.onSurface),
        "surfaceVariant" to (c.surfaceVariant to c.onSurfaceVariant),
        "inverseSurface" to (c.inverseSurface to c.inverseOnSurface),
        "surfaceContainerLow" to (c.surfaceContainerLow to c.onSurface),
        "surfaceContainerHighest" to (c.surfaceContainerHighest to c.onSurface),
        "surfaceContainerHighest/variant" to (c.surfaceContainerHighest to c.onSurfaceVariant),
        "surfaceContainerLow/variant" to (c.surfaceContainerLow to c.onSurfaceVariant),
        "primary on background" to (c.background to c.primary),
        "accent on background" to (c.background to c.tertiary),
    )

    private fun verify(name: String, c: ColorScheme) = pairs(c).forEach { (role, p) ->
        val ratio = contrast(p.first, p.second)
        assertTrue("$name / $role : contrast $ratio < 4.5", ratio >= 4.5)
    }

    @Test fun light() = verify("light", Palette.light)
    @Test fun dark() = verify("dark", Palette.dark)

    @Test fun textOnIconColors() {
        // The icon blue carries white; amber only carries dark text
        assertTrue(contrast(IconColors.Blue, IconColors.White) >= 4.5)
        assertTrue(contrast(IconColors.Amber, Color(0xFF1A1C1E)) >= 4.5)
    }

    @Test fun statusTones() {
        listOf("light" to Palette.statusLight, "dark" to Palette.statusDark).forEach { (mode, t) ->
            mapOf("done" to t.done, "snoozed" to t.snoozed, "late" to t.late, "danger" to t.danger).forEach { (name, tone) ->
                val ratio = contrast(tone.container, tone.content)
                assertTrue("$mode / $name : contrast $ratio < 7", ratio >= 7.0)
            }
        }
    }
}
