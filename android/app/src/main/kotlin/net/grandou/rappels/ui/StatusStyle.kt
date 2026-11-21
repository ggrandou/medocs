package net.grandou.rappels.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import net.grandou.rappels.engine.StockLevel

@Composable
private fun statusTones(): StatusTones =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Palette.statusDark else Palette.statusLight

/** Tone of a successful result (green) and of a destructive or failed one (red): always paired with an icon and words. */
@Composable
fun successTone(): Tone = statusTones().done

@Composable
fun dangerTone(): Tone = statusTones().danger

/** Card tone of an occurrence of today: green done, yellow postponed, orange late, red skipped or missed. */
@Composable
fun entryTone(state: EntryState): Tone {
    val t = statusTones()
    val c = MaterialTheme.colorScheme
    return when (state) {
        EntryState.UPCOMING -> Tone(c.surfaceContainerLow, c.onSurface)
        EntryState.DUE -> t.late
        EntryState.SNOOZED -> t.snoozed
        EntryState.DONE -> t.done
        EntryState.SKIPPED, EntryState.MISSED -> t.danger
    }
}

/** Card tone of an item by stock: neutral when fine, amber when running low, red when out. */
@Composable
fun stockTone(level: StockLevel): Tone {
    val c = MaterialTheme.colorScheme
    return when (level) {
        StockLevel.LOW -> Tone(c.tertiaryContainer, c.onTertiaryContainer)
        StockLevel.OUT -> statusTones().danger
        StockLevel.OK, StockLevel.NOT_TRACKED -> Tone(c.surfaceContainerLow, c.onSurface)
    }
}

/** A status in words, with its icon, in the card's text color: the color of the card is never the only signal. */
@Composable
fun StatusLine(text: String, @DrawableRes icon: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(18.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** "Stock bas" or "Épuisé" for the low and out levels; nothing otherwise. */
@Composable
fun StockStatus(level: StockLevel, color: Color, modifier: Modifier = Modifier) {
    when (level) {
        StockLevel.LOW -> StatusLine("Stock bas", R.drawable.ic_warning, color, modifier)
        StockLevel.OUT -> StatusLine("Épuisé", R.drawable.ic_warning, color, modifier)
        else -> Unit
    }
}
