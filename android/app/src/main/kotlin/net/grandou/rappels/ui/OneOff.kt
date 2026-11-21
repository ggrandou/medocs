package net.grandou.rappels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import net.grandou.rappels.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Parses "8:30", "08:30" or "8h30"; null if it is not a time of day. */
fun parseTime(s: String): LocalTime? {
    val m = Regex("""^\s*(\d{1,2})\s*[:hH]\s*(\d{2})\s*$""").matchEntire(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
}

/** Quantity proposed for an item: its first planned dose, otherwise 1. */
fun defaultQuantity(line: ItemLine): Double =
    line.schedules.flatMap { it.times }.firstNotNullOfOrNull { it.quantity?.takeIf { q -> q > 0 } } ?: 1.0

/** Cards of one-off intakes ("Hors planning"), shared by "Aujourd'hui" and the day editor. */
fun LazyListScope.oneOffSection(list: List<OneOffEntry>, onCancel: (OneOffEntry) -> Unit) {
    if (list.isEmpty()) return
    item(key = "h-extras") { SectionTitle("Hors planning", Modifier.padding(top = 8.dp)) }
    items(list, key = { it.key }) { e -> OneOffCard(e, Modifier.animateItem(), onCancel) }
}

@Composable
private fun OneOffCard(e: OneOffEntry, modifier: Modifier, onCancel: (OneOffEntry) -> Unit) {
    val tone = successTone()
    val time = formatTime(Instant.ofEpochMilli(e.row.timestamp).atZone(ZoneId.systemDefault()).toLocalTime())
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(time, Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, color = tone.content)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.item.name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    e.row.quantity?.let { Text(formatWithUnit(it, e.item.unit), style = MaterialTheme.typography.bodyMedium, color = tone.content) }
                    StatusLine("Prise ponctuelle à $time", R.drawable.ic_check, tone.content, Modifier.padding(top = 2.dp))
                }
            }
            ActionButton("Annuler", R.drawable.ic_undo, "Annuler la prise ponctuelle : ${e.item.name}, $time") { onCancel(e) }
        }
    }
}

/**
 * Asks for an intake outside the schedule: which item, how much, at what time of [date] (today or a past day).
 * [onConfirm] receives the item id, the quantity and the instant (epoch ms).
 */
@Composable
fun OneOffDialog(
    lines: List<ItemLine>,
    date: LocalDate,
    onConfirm: (itemId: Long, quantity: Double, at: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val now = java.time.LocalDateTime.now()
    var itemId by rememberSaveable { mutableStateOf(lines.firstOrNull()?.item?.id ?: 0L) }
    val line = lines.firstOrNull { it.item.id == itemId }
    var quantity by rememberSaveable { mutableStateOf(line?.let { formatQuantity(defaultQuantity(it)) } ?: "1") }
    var time by rememberSaveable {
        mutableStateOf(formatTime(if (date == now.toLocalDate()) now.toLocalTime().withSecond(0).withNano(0) else LocalTime.NOON))
    }
    val q = parseDecimal(quantity)?.takeIf { it > 0 }
    val t = parseTime(time)
    val at = t?.let { date.atTime(it).atZone(zone).toInstant().toEpochMilli() }
    val future = at != null && at > System.currentTimeMillis() + 60_000
    val error = when {
        q == null -> "Quantité invalide"
        t == null -> "Heure invalide (exemple : 8:30)"
        future -> "Cette heure n'est pas encore passée"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prise ponctuelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatLongDay(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    lines.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().selectable(l.item.id == itemId, role = Role.RadioButton) {
                                itemId = l.item.id
                                quantity = formatQuantity(defaultQuantity(l))
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(l.item.id == itemId, onClick = null, Modifier.padding(12.dp))
                            Text(l.item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        quantity, { quantity = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("Quantité" + (line?.item?.unit?.let { " ($it)" } ?: "")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        time, { time = it }, Modifier.weight(1f), singleLine = true, label = { Text("Heure") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(itemId, q!!, at!!) }, enabled = error == null && line != null) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}
