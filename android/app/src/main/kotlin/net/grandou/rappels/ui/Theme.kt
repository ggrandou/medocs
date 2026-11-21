package net.grandou.rappels.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.grandou.rappels.ThemeMode

/** True if the requested mode gives a dark theme ("system" mode follows the device). */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** App palette (no dynamic colors: the identity follows the icon on every phone). */
@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Palette.dark else Palette.light, content = content)
}
