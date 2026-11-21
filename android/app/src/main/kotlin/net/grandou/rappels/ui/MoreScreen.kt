package net.grandou.rappels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.grandou.rappels.ThemeMode
import net.grandou.rappels.R

/** "Plus" (More) tab: entries grouped in a card, each one opens a full screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(theme: ThemeMode, persistent: Boolean, onPersistent: (Boolean) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, onAppearance: () -> Unit, onPermissions: () -> Unit, onAbout: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Plus") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Mes données")
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Entry(R.drawable.ic_more_backup, "Sauvegarder", "Enregistrer un fichier chiffré", onBackup)
                HorizontalDivider(Modifier.padding(start = 72.dp))
                Entry(R.drawable.ic_more_restore, "Restaurer", "Depuis un fichier, même de l'ancienne version", onRestore)
            }
            SectionTitle("Application", Modifier.padding(top = 8.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                SwitchEntry(
                    R.drawable.ic_perm_notifications, "Rappels persistants",
                    if (persistent) "Le rappel reste affiché jusqu'à votre réponse" else "Le rappel peut être effacé d'un geste",
                    persistent, onPersistent,
                )
                HorizontalDivider(Modifier.padding(start = 72.dp))
                Entry(R.drawable.ic_more_appearance, "Apparence", "Mode " + theme.label.lowercase(), onAppearance)
                HorizontalDivider(Modifier.padding(start = 72.dp))
                Entry(R.drawable.ic_more_permissions, "Autorisations", "Notifications, alarmes, batterie", onPermissions)
                HorizontalDivider(Modifier.padding(start = 72.dp))
                Entry(R.drawable.ic_more_info, "À propos", "Version, licence", onAbout)
            }
        }
    }
}

/** Same look as [Entry], with a switch instead of the chevron; the whole row toggles. */
@Composable
private fun SwitchEntry(icon: Int, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One entry: icon badge, title, detail and chevron (decorative: the whole row is clickable). */
@Composable
private fun Entry(icon: Int, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
