package net.grandou.medocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.grandou.medocs.data.Medicament

/** Écran « Médicaments archivés » : chaque médicament avec son visuel et un bouton Restaurer. */
@Composable
fun ArchivesScreen(archives: List<Medicament>, onRestaurer: (Medicament) -> Unit, onRetour: () -> Unit) {
    SousEcran("Médicaments archivés", onRetour) {
        if (archives.isEmpty()) {
            IllustrationPage(Dessin.FLACON)
            Text("Aucun médicament archivé.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("Un traitement terminé s'archive depuis sa fiche : les rappels s'arrêtent, l'historique reste.", Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        archives.forEach { m ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        VisuelMedicament(m, 48.dp)
                        Text(m.nom, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                    FilledTonalButton(onClick = { onRestaurer(m) }, Modifier.semantics { contentDescription = "Restaurer : ${m.nom}" }) { Text("Restaurer", maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

/** Écran « Apparence » : trois cartes à choisir (système, clair, sombre). */
@Composable
fun ApparenceScreen(theme: String, onTheme: (String) -> Unit, onRetour: () -> Unit) {
    SousEcran("Apparence", onRetour) {
        Text("Choisis l'aspect de l'application.", style = MaterialTheme.typography.bodyLarge)
        listOf(
            Triple("systeme", "Système", "Suit le réglage de ton téléphone"),
            Triple("clair", "Clair", "Fond clair en toutes circonstances"),
            Triple("sombre", "Sombre", "Fond sombre, plus reposant le soir"),
        ).forEach { (id, titre, detail) ->
            val choisi = theme == id
            Card(
                Modifier.fillMaxWidth().selectable(selected = choisi, role = Role.RadioButton) { if (!choisi) onTheme(id) },
                colors = CardDefaults.cardColors(containerColor = if (choisi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RadioButton(selected = choisi, onClick = null)
                    Column {
                        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Écran « À propos et avertissement » : le texte complet, la version en tête. */
@Composable
fun AProposScreen(versionApp: String, onRetour: () -> Unit) {
    SousEcran("À propos et avertissement", onRetour) {
        IllustrationPage(Dessin.BOUCLIER)
        TexteAvertissement(versionApp)
    }
}
