package net.grandou.medocs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.grandou.medocs.R

/** Onglet « Plus » : une en-tête, puis des entrées groupées en cartes ; chacune ouvre un écran complet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusScreen(
    versionApp: String,
    nbArchives: Int,
    theme: String,
    onArchives: () -> Unit,
    onSauvegarde: () -> Unit,
    onApparence: () -> Unit,
    onPriseEnMain: () -> Unit,
    onAideRappels: () -> Unit,
    onAPropos: () -> Unit,
    onProtoAlarmes: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Plus") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IllustrationPage(Dessin.PILULE, 110.dp)
            Text("Mes Médocs", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Version $versionApp", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section("Mes données")
            Groupe {
                Entree(R.drawable.ic_plus_sauvegarde, "Sauvegarde", "Exporter ou restaurer un fichier chiffré", onSauvegarde)
                Entree(R.drawable.ic_plus_archive, "Médicaments archivés", if (nbArchives == 0) "Aucun" else "$nbArchives", onArchives, dernier = true)
            }
            Section("Application")
            Groupe {
                Entree(R.drawable.ic_plus_apparence, "Apparence", "Mode " + libelleTheme(theme), onApparence)
                Entree(R.drawable.ic_plus_rappels, "Des rappels fiables", "Notifications, alarmes, économie d'énergie", onAideRappels)
                Entree(R.drawable.ic_plus_prise_en_main, "Revoir la prise en main", null, onPriseEnMain, dernier = true)
            }
            Section("Informations")
            Groupe {
                Entree(R.drawable.ic_plus_info, "À propos et avertissement", "Licence, sources des données", onAPropos)
                Entree(R.drawable.ic_plus_proto, "Test des alarmes", "Prototype pour les curieux", onProtoAlarmes, dernier = true)
            }
        }
    }
}

fun libelleTheme(theme: String) = when (theme) { "clair" -> "clair"; "sombre" -> "sombre"; else -> "du système" }

@Composable
private fun Section(titre: String) {
    Text(titre, Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Groupe(contenu: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column { contenu() } }
}

/** Une entrée : pastille à icône, titre, détail éventuel et chevron (décoratif : toute la ligne est cliquable). */
@Composable
private fun Entree(icone: Int, titre: String, detail: String?, onClick: () -> Unit, dernier: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icone), contentDescription = null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!dernier) HorizontalDivider(Modifier.padding(start = 72.dp))
}
