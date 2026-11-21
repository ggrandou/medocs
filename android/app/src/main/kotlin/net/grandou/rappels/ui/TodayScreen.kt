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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
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
    actions: EntryActions,
    onOpenReminders: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding && state != null) {
        OneOffDialog(state.lines, LocalDate.now(), { id, q, at -> actions.onAddOneOff(id, q, at); adding = false }) { adding = false }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Aujourd'hui") }) },
        floatingActionButton = {
            if (state?.lines?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Prise ponctuelle") },
                )
            }
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val entries = state.today
        val attention = state.lines.filter { it.needsAttention }
        val today = LocalDate.now()

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            // Room under the last card for the floating button.
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { Summary(entries, today) }
            if (attention.isNotEmpty()) item(key = "attention") { AttentionCard(attention, onOpenReminders) }

            if (state.lines.isEmpty()) {
                item(key = "empty-all") {
                    Empty("Rien à suivre pour l'instant", "Ajoutez un premier rappel dans l'onglet Rappels.", Drawing.EMPTY_BOX, "Ajouter un rappel", onOpenReminders)
                }
            } else if (entries.isEmpty() && state.todayExtras.isEmpty()) {
                item(key = "empty-today") {
                    val next = state.lines.mapNotNull { it.next }.minByOrNull { it.dateTime }
                    Empty(
                        "Rien de prévu aujourd'hui",
                        next?.let { "Prochain rappel : ${relativeDay(it.date, today)} à ${formatTime(it.time)}." } ?: "Aucun rappel à venir.",
                        Drawing.FREE_DAY,
                    )
                }
            }

            else if (entries.isNotEmpty() && entries.all { it.state == EntryState.DONE || it.state == EntryState.SKIPPED }) {
                item(key = "all-done") { Illustration(Drawing.ALL_DONE, 120.dp) }
            }

            entrySections(entries, actions, extras = state.todayExtras)
        }
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
private fun Empty(title: String, detail: String, drawing: Drawing, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Illustration(drawing)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = onAction, Modifier.padding(top = 8.dp)) { Text(action) }
    }
}
