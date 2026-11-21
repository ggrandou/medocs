package net.grandou.rappels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Answers to an occurrence, shared by "Aujourd'hui" and the day editor of "Historique". */
class EntryActions(
    val onDone: (TodayEntry) -> Unit,
    val onSkip: (TodayEntry) -> Unit,
    val onPostpone: (TodayEntry) -> Unit,
    val onUndo: (TodayEntry) -> Unit,
    val onCancelOneOff: (OneOffEntry) -> Unit,
    val onAddOneOff: (itemId: Long, quantity: Double, at: Long) -> Unit,
)

/** The usual grouping of a day's entries: to do, missed, ahead, settled. */
fun androidx.compose.foundation.lazy.LazyListScope.entrySections(
    entries: List<TodayEntry>,
    actions: EntryActions,
    allowPostpone: Boolean = true,
    extras: List<OneOffEntry> = emptyList(),
) {
    entrySection("À faire", entries.filter { it.state == EntryState.DUE }, actions, allowPostpone)
    entrySection("Manqués", entries.filter { it.state == EntryState.MISSED }, actions, allowPostpone)
    entrySection("À venir", entries.filter { it.state == EntryState.UPCOMING || it.state == EntryState.SNOOZED }, actions, allowPostpone)
    entrySection("Terminé", entries.filter { it.state == EntryState.DONE || it.state == EntryState.SKIPPED }, actions, allowPostpone)
    oneOffSection(extras, actions.onCancelOneOff)
}

/** A titled group of entry cards in a lazy list. */
fun androidx.compose.foundation.lazy.LazyListScope.entrySection(
    title: String,
    list: List<TodayEntry>,
    actions: EntryActions,
    allowPostpone: Boolean = true,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionTitle(title, Modifier.padding(top = 8.dp)) }
    items(list, key = { it.key }) { e -> EntryCard(e, Modifier.animateItem(), actions, allowPostpone) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(e: TodayEntry, modifier: Modifier, a: EntryActions, allowPostpone: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val tone = entryTone(e.state)
    val sub = if (e.state == EntryState.UPCOMING) c.onSurfaceVariant else tone.content
    val name = e.item.name
    val what = "$name, ${formatTime(e.planned.time)}"
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Time, name, quantity and status read as one sentence for TalkBack; the buttons stay separate.
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(e.planned.time), Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium, color = tone.content)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    e.planned.quantity?.let { Text(formatWithUnit(it, e.item.unit), style = MaterialTheme.typography.bodyMedium, color = sub) }
                    StateLine(e, tone.content, Modifier.padding(top = 2.dp))
                }
            }
            // Same style for every action: a filled "Done" would read as an already validated state.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.actionable) {
                    ActionButton("Fait", R.drawable.ic_check, "Fait : $what") { a.onDone(e) }
                    if (allowPostpone && (e.state == EntryState.DUE || e.state == EntryState.MISSED)) {
                        ActionButton("Reporter", R.drawable.ic_schedule, "Reporter de $SNOOZE_MINUTES minutes : $what") { a.onPostpone(e) }
                    }
                    ActionButton("Ignorer", R.drawable.ic_skip, "Ignorer : $what") { a.onSkip(e) }
                } else {
                    ActionButton("Annuler", R.drawable.ic_undo, "Annuler : $what") { a.onUndo(e) }
                }
            }
        }
    }
}

/** Icon + verb, tonal. Never wraps: a button narrower than its text would clip it, so the row wraps instead. */
@Composable
fun ActionButton(label: String, @DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.semantics { contentDescription = description }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1, softWrap = false)
    }
}

/** What happened, in words and with an icon, in the card's text color. */
@Composable
fun StateLine(e: TodayEntry, color: Color, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    fun at(ms: Long?) = ms?.let { formatTime(Instant.ofEpochMilli(it).atZone(zone).toLocalTime()) }
    when (e.state) {
        EntryState.UPCOMING -> Unit
        EntryState.DUE -> StatusLine("En retard", R.drawable.ic_warning, color, modifier)
        EntryState.SNOOZED -> StatusLine("Reporté à ${at(e.row?.snoozedUntil)}", R.drawable.ic_schedule, color, modifier)
        EntryState.DONE -> StatusLine("Fait" + (at(e.row?.timestamp)?.let { " à $it" } ?: ""), R.drawable.ic_check, color, modifier)
        EntryState.SKIPPED -> StatusLine("Ignoré", R.drawable.ic_skip, color, modifier)
        EntryState.MISSED -> StatusLine("Manqué", R.drawable.ic_warning, color, modifier)
    }
}
