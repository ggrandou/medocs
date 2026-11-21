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

/**
 * "Aujourd'hui" tab: what is planned today, and the answers (done, postpone, skip).
 * Due items first, then missed ones, what is still ahead, and finally what is already settled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: AppState?,
    onDone: (TodayEntry) -> Unit,
    onSkip: (TodayEntry) -> Unit,
    onPostpone: (TodayEntry) -> Unit,
    onUndo: (TodayEntry) -> Unit,
    onOpenReminders: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { LargeTopAppBar(title = { Text("Aujourd'hui") }, scrollBehavior = scroll) },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val entries = state.today
        val due = entries.filter { it.state == EntryState.DUE }
        val missed = entries.filter { it.state == EntryState.MISSED }
        val ahead = entries.filter { it.state == EntryState.UPCOMING || it.state == EntryState.SNOOZED }
        val settled = entries.filter { it.state == EntryState.DONE || it.state == EntryState.SKIPPED }
        val attention = state.lines.filter { it.needsAttention }
        val today = LocalDate.now()

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { Summary(entries, today) }
            if (attention.isNotEmpty()) item(key = "attention") { AttentionCard(attention, onOpenReminders) }

            if (state.lines.isEmpty()) {
                item(key = "empty-all") {
                    Empty("Rien à suivre pour l'instant", "Ajoutez un premier rappel dans l'onglet Rappels.", "Ajouter un rappel", onOpenReminders)
                }
            } else if (entries.isEmpty()) {
                item(key = "empty-today") {
                    val next = state.lines.mapNotNull { it.next }.minByOrNull { it.dateTime }
                    Empty(
                        "Rien de prévu aujourd'hui",
                        next?.let { "Prochain rappel : ${relativeDay(it.date, today)} à ${formatTime(it.time)}." } ?: "Aucun rappel à venir.",
                    )
                }
            }

            section("À faire", due, onDone, onSkip, onPostpone, onUndo)
            section("Manqués", missed, onDone, onSkip, onPostpone, onUndo)
            section("À venir", ahead, onDone, onSkip, onPostpone, onUndo)
            section("Terminé", settled, onDone, onSkip, onPostpone, onUndo)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<TodayEntry>,
    onDone: (TodayEntry) -> Unit,
    onSkip: (TodayEntry) -> Unit,
    onPostpone: (TodayEntry) -> Unit,
    onUndo: (TodayEntry) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionTitle(title, Modifier.padding(top = 8.dp)) }
    items(list, key = { it.key }) { e ->
        EntryCard(e, Modifier.animateItem(), { onDone(e) }, { onSkip(e) }, { onPostpone(e) }, { onUndo(e) })
    }
}

@Composable
private fun Summary(entries: List<TodayEntry>, today: LocalDate) {
    val total = entries.size
    val settled = entries.count { it.state == EntryState.DONE || it.state == EntryState.SKIPPED }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatLongDay(today), style = MaterialTheme.typography.titleMedium)
        if (total > 0) {
            LinearProgressIndicator(
                progress = { settled.toFloat() / total },
                Modifier.fillMaxWidth().semantics { contentDescription = "$settled sur $total ${if (total > 1) "terminés" else "terminé"}" },
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                drawStopIndicator = {},
            )
            Text(
                if (settled == total) "Tout est fait pour aujourd'hui" else "$settled sur $total ${if (total > 1) "terminés" else "terminé"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Amber card: stock running low or out. The names are in the text, the amber only underlines it. */
@Composable
private fun AttentionCard(lines: List<ItemLine>, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = c.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = c.onTertiaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Stock à surveiller", style = MaterialTheme.typography.titleSmall, color = c.onTertiaryContainer)
                Text(
                    lines.joinToString(", ") { it.item.name + if (it.level == net.grandou.rappels.engine.StockLevel.OUT) " (épuisé)" else "" },
                    style = MaterialTheme.typography.bodyMedium, color = c.onTertiaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = c.onTertiaryContainer)
        }
    }
}

@Composable
private fun Empty(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = onAction, Modifier.padding(top = 8.dp)) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(e: TodayEntry, modifier: Modifier, onDone: () -> Unit, onSkip: () -> Unit, onPostpone: () -> Unit, onUndo: () -> Unit) {
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
                    ActionButton("Fait", R.drawable.ic_check, "Fait : $what", onDone)
                    if (e.state == EntryState.DUE || e.state == EntryState.MISSED) {
                        ActionButton("Reporter", R.drawable.ic_schedule, "Reporter de $SNOOZE_MINUTES minutes : $what", onPostpone)
                    }
                    ActionButton("Ignorer", R.drawable.ic_skip, "Ignorer : $what", onSkip)
                } else {
                    ActionButton("Annuler", R.drawable.ic_undo, "Annuler : $what", onUndo)
                }
            }
        }
    }
}

/** Icon + verb, tonal. Never wraps: a button narrower than its text would clip it, so the row wraps instead. */
@Composable
private fun ActionButton(label: String, @DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.semantics { contentDescription = description }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1, softWrap = false)
    }
}

/** What happened, in words and with an icon, in the card's text color. */
@Composable
private fun StateLine(e: TodayEntry, color: Color, modifier: Modifier = Modifier) {
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
