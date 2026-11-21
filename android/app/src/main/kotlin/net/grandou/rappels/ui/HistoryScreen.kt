package net.grandou.rappels.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import net.grandou.rappels.R
import net.grandou.rappels.data.Occurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRANCE)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/** "3 rappels : 2 faits, 1 manqué" (every state present, in a fixed order); empty for a day without reminder. */
fun daySummary(entries: List<TodayEntry>): String {
    if (entries.isEmpty()) return ""
    fun part(state: EntryState, one: String, many: String): String? =
        entries.count { it.state == state }.takeIf { it > 0 }?.let { "$it ${if (it > 1) many else one}" }
    val parts = listOfNotNull(
        part(EntryState.DONE, "fait", "faits"),
        part(EntryState.SKIPPED, "ignoré", "ignorés"),
        part(EntryState.MISSED, "manqué", "manqués"),
        part(EntryState.DUE, "en retard", "en retard"),
        part(EntryState.SNOOZED, "reporté", "reportés"),
        part(EntryState.UPCOMING, "à venir", "à venir"),
    )
    return "${entries.size} ${if (entries.size > 1) "rappels" else "rappel"} : ${parts.joinToString(", ")}"
}

/**
 * Whether the stock will not cover this occurrence: still to come (or late), consuming something, on or after
 * the day its item's stock runs out ([runOut]: item id to first uncovered day, from the coverage simulation).
 */
fun lacksStock(e: TodayEntry, runOut: Map<Long, LocalDate>): Boolean {
    val pending = e.state == EntryState.UPCOMING || e.state == EntryState.DUE || e.state == EntryState.SNOOZED
    val from = runOut[e.item.id] ?: return false
    return pending && (e.planned.quantity ?: 0.0) > 0 && e.planned.date >= from
}

/**
 * "Historique" tab: a month calendar where each day is a ring with one slice per reminder, colored by its state;
 * the selected day is detailed below and can be edited (same cards as "Aujourd'hui").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: AppState?,
    rowsBetween: (LocalDate, LocalDate) -> Flow<List<Occurrence>>,
    oneOffsBetween: (LocalDate, LocalDate) -> Flow<List<Occurrence>>,
    actions: EntryActions,
) {
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)

    val rows by remember(month) { rowsBetween(month.atDay(1), month.atEndOfMonth()) }
        .collectAsStateWithLifecycle(emptyList())
    val extraRows by remember(month) { oneOffsBetween(month.atDay(1), month.atEndOfMonth()) }
        .collectAsStateWithLifecycle(emptyList())
    val extrasByDate: Map<LocalDate, List<OneOffEntry>> = remember(state, extraRows) {
        val zone = ZoneId.systemDefault()
        if (state == null) emptyMap()
        else buildOneOffs(state.lines.map { it.item }, extraRows)
            .groupBy { Instant.ofEpochMilli(it.row.timestamp).atZone(zone).toLocalDate() }
    }
    val extras = extrasByDate[selected].orEmpty()
    val days: Map<LocalDate, List<TodayEntry>> = remember(state, rows, month) {
        if (state == null) emptyMap() else {
            val items = state.lines.map { it.item }
            val schedules = state.lines.flatMap { it.schedules }
            val now = LocalDateTime.now()
            val byDate = rows.filter { it.scheduledDate != null }.groupBy { it.scheduledDate!! }
            (1..month.lengthOfMonth()).associate { d ->
                val date = month.atDay(d)
                date to buildDay(items, schedules, byDate[date].orEmpty(), date, now)
            }
        }
    }
    val entries = days[selected].orEmpty().takeIf { selected.year == month.year && selected.month == month.month }
        ?: if (state != null) buildDay(state.lines.map { it.item }, state.lines.flatMap { it.schedules }, emptyList(), selected, LocalDateTime.now()) else emptyList()

    val runOut = remember(state) {
        state?.lines?.mapNotNull { l -> l.coverage?.runOutDate?.let { l.item.id to it } }?.toMap().orEmpty()
    }

    fun showMonth(m: YearMonth) {
        monthText = m.toString()
        // Keep the selection inside the month shown: same day number, or the 1st / today.
        selectedText = (if (m == YearMonth.from(today)) today else m.atDay(minOf(selected.dayOfMonth, m.lengthOfMonth()))).toString()
    }

    BackHandler(enabled = editing) { editing = false }
    if (editing) {
        DayEditor(selected, entries, extras, state?.lines.orEmpty(), today, actions) { editing = false }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Historique") }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding)
                // Swipe left = next month, right = previous month (the arrows stay available).
                .pointerInput(month) {
                    var total = 0f
                    val threshold = 64.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total <= -threshold) showMonth(month.plusMonths(1))
                            else if (total >= threshold) showMonth(month.minusMonths(1))
                        },
                        onHorizontalDrag = { _, delta -> total += delta },
                    )
                },
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") {
                // Framed: the header, the grid and the legend read as one block.
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonthHeader(
                            month, today,
                            onPrevious = { showMonth(month.minusMonths(1)) },
                            onNext = { showMonth(month.plusMonths(1)) },
                            onToday = { monthText = YearMonth.from(today).toString(); selectedText = today.toString() },
                        )
                        MonthGrid(month, today, selected, days, extrasByDate, runOut) { selectedText = it.toString() }
                        Legend()
                    }
                }
            }
            item(key = "day") {
                DayDetail(selected, today, entries, extras, runOut, onEdit = { editing = true })
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, today: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Mois précédent", Modifier.rotate(180f))
        }
        Text(
            month.format(MONTH_FORMAT).replaceFirstChar { it.uppercase() },
            Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        IconButton(onNext) { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Mois suivant") }
    }
    if (month != YearMonth.from(today)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onToday) { Text("Revenir à aujourd'hui") }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    days: Map<LocalDate, List<TodayEntry>>,
    extrasByDate: Map<LocalDate, List<OneOffEntry>>,
    runOut: Map<Long, LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val cells = offset + month.lengthOfMonth()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    dayShort(it).first().uppercase(), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = week * 7 + col - offset + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val date = month.atDay(n)
                            val entries = days[date].orEmpty()
                            DayCell(date, today, date == selected, entries, extrasByDate[date].orEmpty().size, entries.any { lacksStock(it, runOut) }) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

/** A ring split in equal slices (one per reminder of the day), the day number inside. */
@Composable
private fun DayCell(date: LocalDate, today: LocalDate, selected: Boolean, entries: List<TodayEntry>, extraCount: Int, lacking: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    // One slice per planned reminder, then one per one-off intake (blue, the structure color).
    val tones = entries.map { sliceColor(it.state) } + List(extraCount) { c.primary }
    val label = "${date.format(SHORT_DAY_FORMAT)}" + (if (date == today) ", aujourd'hui" else "") +
        (daySummary(entries).takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ", aucun rappel") +
        (if (extraCount > 0) ", $extraCount ${if (extraCount > 1) "prises ponctuelles" else "prise ponctuelle"}" else "") +
        (if (lacking) ", stock insuffisant" else "")
    // Outer ring = today; filled disc = selected day; the slices sit inside. Each is readable alone and together.
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .then(if (date == today) Modifier.border(2.5.dp, c.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (tones.isNotEmpty()) {
                Canvas(Modifier.size(40.dp)) {
                    val w = 4.dp.toPx()
                    val box = Size(size.width - w, size.height - w)
                    val topLeft = Offset(w / 2, w / 2)
                    val gap = if (tones.size > 1) 6f else 0f
                    val sweep = 360f / tones.size
                    // Dashed ring = the stock will not cover this day.
                    val dash = if (lacking) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null
                    tones.forEachIndexed { i, color ->
                        drawArc(color, -90f + i * sweep + gap / 2, sweep - gap, false, topLeft, box, style = Stroke(w, pathEffect = dash))
                    }
                }
            }
            // Selected disc sits inside the ring, so the slices keep their own colors (even blue ones).
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (selected) c.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (date == today || selected) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (selected) c.onPrimary else if (date == today) c.primary else c.onSurface,
                )
            }
        }
    }
}

/** Slice color of a state: the status tones of the cards, a neutral for what is still ahead. */
@Composable
private fun sliceColor(state: EntryState): Color {
    val tone = entryTone(state).container
    return if (state == EntryState.UPCOMING) MaterialTheme.colorScheme.outlineVariant else tone
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            "Fait" to EntryState.DONE, "En retard" to EntryState.DUE, "Reporté" to EntryState.SNOOZED,
            "Ignoré ou manqué" to EntryState.MISSED, "À venir" to EntryState.UPCOMING, "Prise ponctuelle" to null,
        ).forEach { (text, state) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (state == null) c.primary else sliceColor(state)))
                Text(text, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(16.dp)) {
                val w = 3.dp.toPx()
                drawCircle(
                    c.onSurfaceVariant, radius = (size.width - w) / 2,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))),
                )
            }
            Text("Stock insuffisant (anneau en pointillés)", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayDetail(date: LocalDate, today: LocalDate, entries: List<TodayEntry>, extras: List<OneOffEntry>, runOut: Map<Long, LocalDate>, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(formatLongDay(date) + if (date == today) " (aujourd'hui)" else "", Modifier.padding(top = 8.dp))
        val sub = MaterialTheme.colorScheme.onSurfaceVariant
        if (entries.isEmpty()) {
            Text("Aucun rappel ce jour-là.", style = MaterialTheme.typography.bodyMedium, color = sub)
        } else {
            Text(daySummary(entries), style = MaterialTheme.typography.bodyMedium, color = sub)
            entries.forEach { DayRow(it, lacksStock(it, runOut)) }
        }
        if (extras.isNotEmpty()) {
            Text(
                "${extras.size} ${if (extras.size > 1) "prises ponctuelles" else "prise ponctuelle"}",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = sub,
            )
            extras.forEach { OneOffRow(it) }
        }
        if (entries.isNotEmpty() || extras.isNotEmpty() || date <= today) FilledTonalButton(onEdit, Modifier.padding(top = 4.dp), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Modifier ce jour")
        }
    }
}

/** Compact line of a one-off intake. */
@Composable
private fun OneOffRow(e: OneOffEntry) {
    val tone = successTone()
    val time = formatTime(Instant.ofEpochMilli(e.row.timestamp).atZone(ZoneId.systemDefault()).toLocalTime())
    Card(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time, Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, color = tone.content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.item.name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.row.quantity?.let { Text(formatWithUnit(it, e.item.unit), style = MaterialTheme.typography.bodyMedium, color = tone.content) }
                StatusLine("Prise ponctuelle", R.drawable.ic_check, tone.content, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Compact line of the selected day: time, name, quantity and state in words. */
@Composable
private fun DayRow(e: TodayEntry, lacking: Boolean) {
    val tone = entryTone(e.state)
    val sub = if (e.state == EntryState.UPCOMING) MaterialTheme.colorScheme.onSurfaceVariant else tone.content
    Card(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(e.planned.time), Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, color = tone.content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.item.name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.planned.quantity?.let { Text(formatWithUnit(it, e.item.unit), style = MaterialTheme.typography.bodyMedium, color = sub) }
                StateLine(e, tone.content, Modifier.padding(top = 2.dp))
                if (lacking) StatusLine("Stock insuffisant", R.drawable.ic_warning, tone.content, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Edits one day: the same cards and answers as "Aujourd'hui"; "Reporter" only makes sense for today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEditor(
    date: LocalDate, entries: List<TodayEntry>, extras: List<OneOffEntry>, lines: List<ItemLine>,
    today: LocalDate, actions: EntryActions, onBack: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        OneOffDialog(lines, date, { id, q, at -> actions.onAddOneOff(id, q, at); adding = false }) { adding = false }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatLongDay(date)) },
                navigationIcon = { IconButton(onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty() && extras.isEmpty()) item { Text("Aucun rappel ce jour-là.", style = MaterialTheme.typography.bodyMedium) }
            entrySections(entries, actions, allowPostpone = date == today, extras = extras)
            if (date <= today && lines.isNotEmpty()) {
                item(key = "add-one-off") {
                    FilledTonalButton({ adding = true }, Modifier.padding(top = 8.dp), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Ajouter une prise ponctuelle")
                    }
                }
            }
        }
    }
}
