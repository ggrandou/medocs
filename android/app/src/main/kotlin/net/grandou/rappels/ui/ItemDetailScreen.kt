package net.grandou.rappels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.engine.StockLevel
import java.time.LocalDate

/** One item: what comes next, stock management, and its rhythms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    line: ItemLine,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (ScheduleWithTimes) -> Unit,
    onToggleSchedule: (ScheduleWithTimes, Boolean) -> Unit,
    onPurchase: (Double) -> Unit,
    onAddPackage: () -> Unit,
    onCorrectStock: (Double) -> Unit,
) {
    val item = line.item
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Retour") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Modifier") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Supprimer") }
                },
            )
        },
        floatingActionButton = {
            if (line.schedules.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddSchedule,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Ajouter un rythme") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { Header(line, today) }
            item(key = "stock") { StockCard(line, onPurchase, onAddPackage, onCorrectStock, onEdit) }
            item(key = "h-schedules") { SectionTitle("Rythmes") }
            if (line.schedules.isEmpty()) {
                item(key = "no-schedule") { NoSchedule(onAddSchedule) }
            } else {
                items(line.schedules, key = { it.schedule.id }) { sw ->
                    ScheduleCard(sw, item.unit, today, { onEditSchedule(sw) }, { onToggleSchedule(sw, it) })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${item.name} » ?") },
            text = { Text("Les rythmes, l'historique et le stock seront effacés. Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Header(line: ItemLine, today: LocalDate) {
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(line.item.name, size = 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.item.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                nextLine(line, today) ?: "Aucun rappel à venir",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StockCard(line: ItemLine, onPurchase: (Double) -> Unit, onAddPackage: () -> Unit, onCorrect: (Double) -> Unit, onEdit: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val item = line.item
    var dialog by rememberSaveable { mutableStateOf("") } // "", "add" or "correct"
    val tone = stockTone(line.level)
    val fg = tone.content
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = fg),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_inventory), contentDescription = null, Modifier.size(20.dp), tint = fg)
                Text("Stock", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = fg)
                StockStatus(line.level, fg)
            }
            if (!item.trackStock) {
                Text("Le suivi du stock est désactivé pour ce rappel.", style = MaterialTheme.typography.bodyMedium, color = fg)
                TextButton(onClick = onEdit) { Text("Modifier") }
            } else {
                Text(formatWithUnit(line.stock.coerceAtLeast(0.0), item.unit), style = MaterialTheme.typography.headlineMedium, color = fg)
                coverageText(line)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = fg) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { dialog = "add" }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                        Text("Ajouter", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { dialog = "correct" }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, Modifier.size(18.dp))
                        Text("Corriger", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (dialog == "add") AddStockDialog(item.unit, item.packaging, item.unitsPerPackage, { dialog = "" }, onPurchase, onAddPackage)
    if (dialog == "correct") CorrectStockDialog(item.unit, line.stock, { dialog = "" }, onCorrect)
}

private fun coverageText(line: ItemLine): String? {
    val cov = line.coverage ?: return null
    val days = cov.days ?: return "Suffisant pour plus d'un an"
    val until = cov.runOutDate?.let { " (jusqu'au ${formatDate(it.minusDays(1).coerceAtLeast(LocalDate.now()))})" }.orEmpty()
    return when {
        line.level == StockLevel.OUT -> "Pas assez pour la prochaine prise"
        else -> "Suffisant pour environ ${formatDays(days)}$until"
    }
}

@Composable
private fun NoSchedule(onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Aucun rythme", style = MaterialTheme.typography.titleMedium)
            Text("Définissez quand vous souhaitez être rappelé : tous les jours, certains jours, une fois par mois…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                Text("Ajouter un rythme", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ScheduleCard(sw: ScheduleWithTimes, unit: String, today: LocalDate, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = sw.schedule
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rhythmSummary(s), style = MaterialTheme.typography.titleMedium)
                sw.times.sortedBy { it.time }.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                        Text(timeLine(it, unit), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                    Text(periodText(s, today), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                if (!s.active) Pill("En pause", c.secondaryContainer, c.onSecondaryContainer, R.drawable.ic_pause, Modifier.padding(top = 4.dp))
            }
            Switch(
                s.active, onToggle,
                Modifier.semantics { contentDescription = if (s.active) "Rythme actif, mettre en pause" else "Rythme en pause, reprendre" },
            )
        }
    }
}

@Composable
private fun AddStockDialog(
    unit: String, packaging: String?, perPackage: Double?,
    onDismiss: () -> Unit, onPurchase: (Double) -> Unit, onAddPackage: () -> Unit,
) {
    var qty by rememberSaveable { mutableStateOf("") }
    val value = parseDecimal(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter au stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (perPackage != null) {
                    FilledTonalButton(onClick = { onAddPackage(); onDismiss() }, Modifier.fillMaxWidth()) {
                        Text("+ 1 ${packaging ?: "lot"} (${formatWithUnit(perPackage, unit)})")
                    }
                }
                NumberField(if (perPackage != null) "Ou une autre quantité" else "Quantité", qty, { qty = it }, suffix = unit)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPurchase(value!!); onDismiss() }, enabled = value != null && value > 0) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun CorrectStockDialog(unit: String, current: Double, onDismiss: () -> Unit, onCorrect: (Double) -> Unit) {
    var qty by rememberSaveable { mutableStateOf(formatQuantity(current.coerceAtLeast(0.0))) }
    val value = parseDecimal(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corriger le stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Indiquez ce qu'il reste réellement.", style = MaterialTheme.typography.bodyMedium)
                NumberField("Stock réel", qty, { qty = it }, suffix = unit)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCorrect(value!!); onDismiss() }, enabled = value != null && value >= 0) { Text("Corriger") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
