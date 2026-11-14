package net.grandou.medocs.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette de Mes Médocs, tirée de l'icône : bleu-vert (primaire) et ambre (accent).
 * Les paires « fond / texte » respectent un contraste d'au moins 4,5:1 (vérifié par PaletteTest).
 * Les rôles `surfaceContainer*` sont définis un à un : sinon Material les déduit de sa palette violette par défaut
 * et les cartes prennent une teinte étrangère.
 */
object Palette {
    val claire: ColorScheme = lightColorScheme(
        primary = Color(0xFF00668A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC3E8FF), onPrimaryContainer = Color(0xFF001E2D),
        secondary = Color(0xFF4E616D), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD1E5F4), onSecondaryContainer = Color(0xFF091E28),
        tertiary = Color(0xFF825500), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDDB3), onTertiaryContainer = Color(0xFF291800),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBFCFF), onBackground = Color(0xFF191C1E),
        surface = Color(0xFFFBFCFF), onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFDCE3E9), onSurfaceVariant = Color(0xFF40484D),
        outline = Color(0xFF70787D), outlineVariant = Color(0xFFC0C7CD),
        inverseSurface = Color(0xFF2E3133), inverseOnSurface = Color(0xFFF0F1F3), inversePrimary = Color(0xFF7FD0FF),
        surfaceTint = Color(0xFF00668A),
        surfaceDim = Color(0xFFD8DADD), surfaceBright = Color(0xFFFBFCFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F8),
        surfaceContainer = Color(0xFFECEEF2), surfaceContainerHigh = Color(0xFFE6E8EC), surfaceContainerHighest = Color(0xFFE0E3E7),
    )

    val sombre: ColorScheme = darkColorScheme(
        primary = Color(0xFF7FD0FF), onPrimary = Color(0xFF00344A),
        primaryContainer = Color(0xFF004C69), onPrimaryContainer = Color(0xFFC3E8FF),
        secondary = Color(0xFFB5C9D7), onSecondary = Color(0xFF1F333E),
        secondaryContainer = Color(0xFF364955), onSecondaryContainer = Color(0xFFD1E5F4),
        tertiary = Color(0xFFFFB95C), onTertiary = Color(0xFF452B00),
        tertiaryContainer = Color(0xFF633F00), onTertiaryContainer = Color(0xFFFFDDB3),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF191C1E), onBackground = Color(0xFFE1E2E5),
        surface = Color(0xFF191C1E), onSurface = Color(0xFFE1E2E5),
        surfaceVariant = Color(0xFF40484D), onSurfaceVariant = Color(0xFFC0C7CD),
        outline = Color(0xFF8A9297), outlineVariant = Color(0xFF40484D),
        inverseSurface = Color(0xFFE1E2E5), inverseOnSurface = Color(0xFF2E3133), inversePrimary = Color(0xFF00668A),
        surfaceTint = Color(0xFF7FD0FF),
        surfaceDim = Color(0xFF111416), surfaceBright = Color(0xFF373A3C),
        surfaceContainerLowest = Color(0xFF0C0F10), surfaceContainerLow = Color(0xFF1D2022),
        surfaceContainer = Color(0xFF212426), surfaceContainerHigh = Color(0xFF2B2E30), surfaceContainerHighest = Color(0xFF363A3B),
    )
}
