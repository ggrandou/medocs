package net.grandou.rappels.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette of T'as pensé à ?, drawn from the icon (IconColors): blue (primary), top and side of the box (containers), amber (accent).
 * The "background / text" pairs keep a contrast of at least 4.5:1 (checked by PaletteTest).
 * The `surfaceContainer*` roles are defined one by one: otherwise Material derives them from its default purple palette
 * and cards get a foreign tint.
 */
/** The five icon colors: single source of the graphic charter (see AGENTS.md). */
object IconColors {
    val Blue = Color(0xFF1F6F8B)     // icon background
    val Amber = Color(0xFFFFB300)    // box text, badge ring
    val White = Color(0xFFFFFFFF)    // front face, bubble
    val Top = Color(0xFFD9E8EE)   // top of the box
    val Side = Color(0xFFB9D2DC)  // side of the box
}

/** Background and text colors of a status. */
class Tone(val container: Color, val content: Color)

/**
 * Semantic status tones, outside the icon's five colors (documented exception of the charter, see AGENTS.md):
 * green = done, yellow = postponed, orange = late, red = skipped, missed or out of stock.
 * The status is always also written and paired with an icon: the color is never alone.
 */
class StatusTones(val done: Tone, val snoozed: Tone, val late: Tone, val danger: Tone)

object Palette {
    val statusLight = StatusTones(
        done = Tone(Color(0xFFCDEBD3), Color(0xFF00210C)),
        snoozed = Tone(Color(0xFFFFF3A0), Color(0xFF221B00)),
        late = Tone(Color(0xFFFFCF99), Color(0xFF2E1500)),
        danger = Tone(Color(0xFFFFB8B0), Color(0xFF410002)),
    )
    val statusDark = StatusTones(
        done = Tone(Color(0xFF1E4D2B), Color(0xFFCDEBD3)),
        snoozed = Tone(Color(0xFF5E4B00), Color(0xFFFFF3A0)),
        late = Tone(Color(0xFF703B00), Color(0xFFFFE0C2)),
        danger = Tone(Color(0xFF93000A), Color(0xFFFFDAD6)),
    )

    val light: ColorScheme = lightColorScheme(
        primary = IconColors.Blue, onPrimary = IconColors.White,
        primaryContainer = IconColors.Top, onPrimaryContainer = Color(0xFF0C2F3D),
        secondary = Color(0xFF4E616D), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = IconColors.Side, onSecondaryContainer = Color(0xFF0C2F3D),
        tertiary = Color(0xFF825500), onTertiary = IconColors.White,
        tertiaryContainer = IconColors.Amber, onTertiaryContainer = Color(0xFF1A1C1E),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBFCFF), onBackground = Color(0xFF191C1E),
        surface = Color(0xFFFBFCFF), onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFDCE3E9), onSurfaceVariant = Color(0xFF40484D),
        outline = Color(0xFF70787D), outlineVariant = Color(0xFFC0C7CD),
        inverseSurface = Color(0xFF2E3133), inverseOnSurface = Color(0xFFF0F1F3), inversePrimary = Color(0xFF7FC3DD),
        surfaceTint = IconColors.Blue,
        surfaceDim = Color(0xFFD8DADD), surfaceBright = Color(0xFFFBFCFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F8),
        surfaceContainer = Color(0xFFECEEF2), surfaceContainerHigh = Color(0xFFE6E8EC), surfaceContainerHighest = Color(0xFFE0E3E7),
    )

    /** Dark: surfaces = icon blue, heavily darkened (same hue, ~200°), accents = lightened blue and the icon's amber. */
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF7FC3DD), onPrimary = Color(0xFF0C2F3D),
        primaryContainer = IconColors.Blue, onPrimaryContainer = IconColors.White,
        secondary = Color(0xFFB5C9D7), onSecondary = Color(0xFF1F333E),
        secondaryContainer = Color(0xFF2F4753), onSecondaryContainer = IconColors.Side,
        tertiary = IconColors.Amber, onTertiary = Color(0xFF1A1C1E),
        tertiaryContainer = Color(0xFF633F00), onTertiaryContainer = Color(0xFFFFDDB3),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF141D21), onBackground = Color(0xFFE1E6E9),
        surface = Color(0xFF141D21), onSurface = Color(0xFFE1E6E9),
        surfaceVariant = Color(0xFF3A4850), onSurfaceVariant = Color(0xFFBFC8CE),
        outline = Color(0xFF899298), outlineVariant = Color(0xFF3A4850),
        inverseSurface = Color(0xFFE1E6E9), inverseOnSurface = Color(0xFF2B3338), inversePrimary = IconColors.Blue,
        surfaceTint = Color(0xFF7FC3DD),
        surfaceDim = Color(0xFF0F171B), surfaceBright = Color(0xFF323C40),
        surfaceContainerLowest = Color(0xFF0E1519), surfaceContainerLow = Color(0xFF192226),
        surfaceContainer = Color(0xFF1D272B), surfaceContainerHigh = Color(0xFF283236), surfaceContainerHighest = Color(0xFF333D41),
    )
}
