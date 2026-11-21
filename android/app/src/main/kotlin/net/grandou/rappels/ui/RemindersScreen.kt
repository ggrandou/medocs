package net.grandou.rappels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import net.grandou.rappels.engine.StockLevel
import java.time.LocalDate

/**
 * "Rappels" tab: everything the user tracks, whatever it is. Items running low come first, in amber;
 * each card shows the rhythm, the next reminder and the stock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(lines: List<ItemLine>?, onAdd: () -> Unit, onOpen: (ItemLine) -> Unit) {
    val listState = rememberLazyListState()
    val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24 } }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Rappels") }) },
        floatingActionButton = {
            if (!lines.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd, expanded = expanded,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Ajouter") },
                )
            }
        },
    ) { padding ->
        when {
            lines == null -> Box(Modifier.fillMaxSize().padding(padding))
            lines.isEmpty() -> EmptyState(onAdd, Modifier.padding(padding))
            else -> {
                val attention = lines.filter { it.needsAttention }
                val others = lines.filterNot { it.needsAttention }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding), state = listState,
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (attention.isNotEmpty()) {
                        item(key = "h-attention") { SectionTitle("À surveiller") }
                        items(attention, key = { it.item.id }) { ItemCard(it) { onOpen(it) } }
                    }
                    if (others.isNotEmpty()) {
                        if (attention.isNotEmpty()) item(key = "h-others") { SectionTitle("Tous les rappels", Modifier.padding(top = 8.dp)) }
                        items(others, key = { it.item.id }) { ItemCard(it) { onOpen(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Illustration(Drawing.EMPTY_BOX)
        Text("Rien à suivre pour l'instant", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Ajoutez ce dont vous voulez ne rien oublier : un rappel régulier, avec ou sans suivi de stock.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd, Modifier.padding(top = 8.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
            Text("Ajouter un rappel", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ItemCard(line: ItemLine, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val item = line.item
    val tone = stockTone(line.level)
    val sub = if (line.needsAttention) tone.content else c.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Avatar(item.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StockStatus(line.level, tone.content, Modifier.padding(vertical = 2.dp))
                Text(rhythmLine(line), style = MaterialTheme.typography.bodyMedium, color = sub)
                nextLine(line)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = sub) }
                stockLine(line)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tone.content) }
            }
        }
    }
}

/** "Tous les jours · 2 fois par jour", or the invitation to define a rhythm. */
internal fun rhythmLine(line: ItemLine): String {
    val active = line.schedules.filter { it.schedule.active }
    return when {
        line.schedules.isEmpty() -> "Aucun rythme défini"
        active.isEmpty() -> "En pause"
        active.size == 1 -> rhythmSummary(active[0].schedule) + timesCount(active[0].times.size)
        else -> "${active.size} rythmes"
    }
}

private fun timesCount(n: Int) = when {
    n <= 1 -> ""
    else -> " · $n fois par jour"
}

internal fun nextLine(line: ItemLine, today: LocalDate = LocalDate.now()): String? =
    line.next?.let { "Prochain : ${relativeDay(it.date, today)} à ${formatTime(it.time)}" }

/** "Reste 12 doses · environ 12 jours", or null when the stock is not tracked. */
internal fun stockLine(line: ItemLine): String? {
    if (!line.item.trackStock) return null
    val base = "Reste ${formatWithUnit(line.stock.coerceAtLeast(0.0), line.item.unit)}"
    val days = line.coverage?.days
    return if (days != null && line.level != StockLevel.OUT) "$base · environ ${formatDays(days)}" else base
}
