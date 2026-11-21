package net.grandou.rappels.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import net.grandou.rappels.data.IntervalUnit
import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import net.grandou.rappels.data.ScheduleWithTimes
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The four ways to describe a rhythm, as the user sees them. */
private enum class Mode(val label: String) {
    EVERY("Tous les…"), WEEKDAYS("Jours fixes"), RANK("Dans le mois"), CYCLE("Cycle"),
}

/** A time of the schedule being edited; [id] is kept so that its history stays linked. Saved as text. */
private data class TimeRow(val id: Long, val time: LocalTime, val quantity: String) {
    fun encode() = "$id|$time|$quantity"

    companion object {
        fun decode(s: String): TimeRow {
            val (id, time, qty) = s.split("|", limit = 3)
            return TimeRow(id.toLong(), LocalTime.parse(time), qty)
        }
    }
}

private fun encodeRows(rows: List<TimeRow>) = rows.sortedBy { it.time }.joinToString("\n") { it.encode() }
private fun decodeRows(s: String) = if (s.isEmpty()) emptyList() else s.split("\n").map(TimeRow::decode)

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.fromPickerMillis() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Creation or edition of a schedule: rhythm, times with quantities, and period. A live summary stays on top. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormScreen(
    item: Item,
    existing: ScheduleWithTimes?,
    onBack: () -> Unit,
    onSave: (Schedule, List<ScheduleTime>) -> Unit,
    onDelete: (Schedule) -> Unit,
) {
    val old = existing?.schedule
    val today = LocalDate.now()

    var mode by rememberSaveable { mutableStateOf(modeOf(old).name) }
    var everyN by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.PERIODIC) old.interval.toString() else "1") }
    var everyUnit by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.PERIODIC) old.intervalUnit.name else IntervalUnit.DAY.name) }
    var days by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.WEEKDAYS) old.weekdays else 0) }
    var weekInterval by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.WEEKDAYS) old.interval.toString() else "1") }
    var rank by rememberSaveable { mutableStateOf(old?.rank ?: 1) }
    var rankDay by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.RANK_IN_MONTH) old.weekdays else 1) }
    var monthInterval by rememberSaveable { mutableStateOf(if (old?.recurrence == Recurrence.RANK_IN_MONTH) old.interval.toString() else "1") }
    var activeDays by rememberSaveable { mutableStateOf(old?.activeDays?.toString() ?: "21") }
    var pauseDays by rememberSaveable { mutableStateOf(old?.pauseDays?.toString() ?: "7") }
    var start by rememberSaveable { mutableStateOf((old?.startDate ?: today).toString()) }
    var end by rememberSaveable { mutableStateOf(old?.endDate?.toString() ?: "") }
    var rowsRaw by rememberSaveable {
        mutableStateOf(
            encodeRows(
                existing?.times?.map { TimeRow(it.id, it.time, it.quantity?.let(::formatQuantity) ?: "") }
                    ?: listOf(TimeRow(0, LocalTime.of(8, 0), "1")),
            ),
        )
    }
    var timeDialog by rememberSaveable { mutableStateOf<String?>(null) } // null, "new" or the row's index
    var dateDialog by rememberSaveable { mutableStateOf<String?>(null) } // null, "start" or "end"
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val rows = decodeRows(rowsRaw)
    val m = Mode.valueOf(mode)
    val startDate = LocalDate.parse(start)
    val endDate = end.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)

    // The schedule as currently described; used for the live summary and for saving.
    val draft = Schedule(
        id = old?.id ?: 0, itemId = item.id, active = old?.active ?: true, startDate = startDate, endDate = endDate,
        recurrence = when (m) { Mode.EVERY -> Recurrence.PERIODIC; Mode.WEEKDAYS -> Recurrence.WEEKDAYS; Mode.RANK -> Recurrence.RANK_IN_MONTH; Mode.CYCLE -> Recurrence.CYCLE },
        interval = when (m) { Mode.EVERY -> everyN; Mode.WEEKDAYS -> weekInterval; Mode.RANK -> monthInterval; Mode.CYCLE -> "1" }.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        intervalUnit = if (m == Mode.EVERY) IntervalUnit.valueOf(everyUnit) else IntervalUnit.DAY,
        weekdays = when (m) { Mode.WEEKDAYS -> days; Mode.RANK -> rankDay; else -> 0 },
        rank = if (m == Mode.RANK) rank else null,
        activeDays = if (m == Mode.CYCLE) activeDays.toIntOrNull() else null,
        pauseDays = if (m == Mode.CYCLE) pauseDays.toIntOrNull() else null,
    )

    val quantitiesOk = rows.all { it.quantity.isBlank() || parseDecimal(it.quantity)?.let { q -> q > 0 } == true }
    val problem: String? = when {
        rows.isEmpty() -> "Ajoutez au moins une heure."
        !quantitiesOk -> "Une quantité n'est pas valide."
        m == Mode.EVERY && everyN.toIntOrNull()?.let { it >= 1 } != true -> "Indiquez un nombre supérieur à 0."
        m == Mode.WEEKDAYS && days == 0 -> "Choisissez au moins un jour."
        m == Mode.WEEKDAYS && weekInterval.toIntOrNull()?.let { it >= 1 } != true -> "Indiquez un nombre de semaines supérieur à 0."
        m == Mode.RANK && monthInterval.toIntOrNull()?.let { it >= 1 } != true -> "Indiquez un nombre de mois supérieur à 0."
        m == Mode.CYCLE && activeDays.toIntOrNull()?.let { it >= 1 } != true -> "Indiquez la durée de la phase active."
        m == Mode.CYCLE && pauseDays.toIntOrNull()?.let { it >= 0 } != true -> "Indiquez la durée de la pause."
        endDate != null && endDate < startDate -> "La date de fin précède la date de début."
        else -> null
    }

    FormScaffold(
        title = if (existing == null) "Nouveau rythme" else "Modifier le rythme",
        onBack = onBack,
        saveLabel = "Enregistrer",
        saveEnabled = problem == null,
        onSave = {
            onSave(draft, rows.sortedBy { it.time }.map { ScheduleTime(id = it.id, scheduleId = old?.id ?: 0, time = it.time, quantity = parseDecimal(it.quantity)) })
        },
    ) {
        // Live summary
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(rhythmSummary(draft), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (rows.isNotEmpty()) Text(rows.sortedBy { it.time }.joinToString(" · ") { formatTime(it.time) }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // Rhythm
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Rythme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { o ->
                    FilterChip(
                        selected = o == m, onClick = { mode = o.name }, label = { Text(o.label) },
                        leadingIcon = if (o == m) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            when (m) {
                Mode.EVERY -> {
                    OutlinedTextField(
                        everyN, { everyN = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Tous les") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(IntervalUnit.DAY to "jours", IntervalUnit.WEEK to "semaines", IntervalUnit.MONTH to "mois", IntervalUnit.YEAR to "ans").forEach { (u, l) ->
                            FilterChip(selected = everyUnit == u.name, onClick = { everyUnit = u.name }, label = { Text(l) })
                        }
                    }
                }
                Mode.WEEKDAYS -> {
                    DayRow(selected = { days and it != 0 }, onToggle = { days = days xor it })
                    OutlinedTextField(
                        weekInterval, { weekInterval = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Une semaine sur") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("1 = toutes les semaines") },
                    )
                }
                Mode.RANK -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "1er", 2 to "2e", 3 to "3e", 4 to "4e", -1 to "Dernier").forEach { (r, l) ->
                            FilterChip(selected = rank == r, onClick = { rank = r }, label = { Text(l) })
                        }
                    }
                    DayRow(selected = { rankDay == it }, onToggle = { rankDay = it })
                    OutlinedTextField(
                        monthInterval, { monthInterval = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Tous les … mois") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("1 = chaque mois") },
                    )
                }
                Mode.CYCLE -> {
                    OutlinedTextField(
                        activeDays, { activeDays = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Jours actifs") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        pauseDays, { pauseDays = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Jours de pause") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Le cycle recommence ensuite, depuis la date de début.") },
                    )
                }
            }
        }

        // Times
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Heures")
            rows.forEachIndexed { i, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { timeDialog = i.toString() }, Modifier.heightIn(min = 56.dp)) {
                        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, Modifier.size(18.dp))
                        Text(formatTime(row.time), Modifier.padding(start = 8.dp))
                    }
                    OutlinedTextField(
                        row.quantity, { q -> rowsRaw = encodeRows(rows.mapIndexed { j, r -> if (j == i) r.copy(quantity = q) else r }) },
                        Modifier.weight(1f), label = { Text("Quantité") }, singleLine = true, suffix = { Text(item.unit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = row.quantity.isNotBlank() && parseDecimal(row.quantity) == null,
                    )
                    IconButton(onClick = { rowsRaw = encodeRows(rows.filterIndexed { j, _ -> j != i }) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Retirer ${formatTime(row.time)}")
                    }
                }
            }
            TextButton(onClick = { timeDialog = "new" }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                Text("Ajouter une heure", Modifier.padding(start = 8.dp))
            }
            Text(
                "La quantité est retirée du stock à chaque prise confirmée. Laissez-la vide pour ne rien retirer.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Period
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Période")
            DateRow("Début", formatDate(startDate)) { dateDialog = "start" }
            if (endDate == null) {
                TextButton(onClick = { dateDialog = "end" }) { Text("Ajouter une date de fin") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DateRow("Fin", formatDate(endDate)) { dateDialog = "end" } }
                    IconButton(onClick = { end = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Retirer la date de fin") }
                }
            }
        }

        if (problem != null) Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)

        if (old != null) {
            TextButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text("Supprimer ce rythme", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    timeDialog?.let { which ->
        val index = which.toIntOrNull()
        val initial = index?.let { rows.getOrNull(it)?.time } ?: LocalTime.of(12, 0)
        TimeDialog(initial, onDismiss = { timeDialog = null }) { t ->
            rowsRaw = encodeRows(
                if (index != null && index in rows.indices) rows.mapIndexed { j, r -> if (j == index) r.copy(time = t) else r }
                else rows + TimeRow(0, t, rows.lastOrNull()?.quantity ?: "1"),
            )
            timeDialog = null
        }
    }

    dateDialog?.let { which ->
        val initial = if (which == "end") endDate ?: startDate else startDate
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { dateDialog = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.fromPickerMillis()?.let { if (which == "end") end = it.toString() else start = it.toString() }
                    dateDialog = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = null }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete && old != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce rythme ?") },
            text = { Text("L'historique déjà enregistré est conservé.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(old) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun modeOf(s: Schedule?): Mode = when (s?.recurrence) {
    Recurrence.WEEKDAYS -> Mode.WEEKDAYS
    Recurrence.RANK_IN_MONTH -> Mode.RANK
    Recurrence.CYCLE -> Mode.CYCLE
    else -> Mode.EVERY
}

/** Seven round toggles, Monday first. The state is carried by fill and border, and read aloud by TalkBack. */
@Composable
private fun DayRow(selected: (Int) -> Boolean, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val bit = 1 shl (d.value - 1)
            val on = selected(bit)
            val c = MaterialTheme.colorScheme
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (on) c.primary else c.surfaceContainerHighest)
                        .then(if (on) Modifier else Modifier.border(1.dp, c.outline, CircleShape))
                        .clickable(role = Role.Checkbox) { onToggle(bit) }
                        .semantics { contentDescription = dayName(d); this.selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(dayName(d).first().uppercase(), style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_event), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heure") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
