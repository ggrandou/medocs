package net.grandou.medocs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.grandou.medocs.data.Medicament

/** Onglet « Plus » : sauvegarde, archives, aide, à propos, prise en main. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusScreen(
    archives: List<Medicament>,
    onRestaurer: (Medicament) -> Unit,
    onSauvegarde: () -> Unit,
    onPriseEnMain: () -> Unit,
    onAPropos: () -> Unit,
    onAideRappels: () -> Unit,
    onProtoAlarmes: () -> Unit,
) {
    var voirArchives by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Plus") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Ligne("Médicaments archivés (${archives.size})", "Retrouver un médicament archivé") { voirArchives = true }
            Ligne("Sauvegarde : export / import", "Fichier chiffré, choisi par toi") { onSauvegarde() }
            Ligne("Aide : rappels fiables", "Notifications, alarmes exactes, économie d'énergie") { onAideRappels() }
            Ligne("Revoir la prise en main", null) { onPriseEnMain() }
            Ligne("À propos et avertissement", null) { onAPropos() }
            Ligne("Test des alarmes (prototype)", null) { onProtoAlarmes() }
        }
    }

    if (voirArchives) {
        AlertDialog(
            onDismissRequest = { voirArchives = false },
            title = { Text("Médicaments archivés") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (archives.isEmpty()) Text("Aucun médicament archivé.")
                    archives.forEach { m ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.nom, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onRestaurer(m) }) { Text("Restaurer") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { voirArchives = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun Ligne(titre: String, detail: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center) {
        Text(titre, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
