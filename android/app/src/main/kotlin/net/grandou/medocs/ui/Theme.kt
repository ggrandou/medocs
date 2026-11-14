package net.grandou.medocs.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Vrai : sur Android 12+, les couleurs viennent du fond d'écran de l'utilisateur (Material You) ; faux : la palette de
 * l'application (ui/Palette.kt) partout. Faux par défaut : l'identité visuelle suit l'icône et reste la même sur tous les téléphones.
 */
const val COULEURS_DYNAMIQUES = false

/** Suit le mode clair ou sombre de l'appareil ; [sombre] ne sert qu'aux tests et aux captures d'écran. */
@Composable
fun AppTheme(sombre: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val couleurs = when {
        COULEURS_DYNAMIQUES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (sombre) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        sombre -> Palette.sombre
        else -> Palette.claire
    }
    MaterialTheme(colorScheme = couleurs, content = content)
}
