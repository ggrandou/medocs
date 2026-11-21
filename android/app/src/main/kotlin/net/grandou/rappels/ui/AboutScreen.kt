package net.grandou.rappels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

/** "À propos" (About) screen: presentation, data, license. Provisional texts, to be reviewed with the Play listing. */
@Composable
fun AboutScreen(appVersion: String, onBack: () -> Unit) {
    SubScreen("À propos", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("T'as pensé à ?", style = MaterialTheme.typography.titleLarge)
            Text("Version $appVersion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Des rappels qui demandent si c'est fait, et qui surveillent ce qui s'épuise.")

            SectionTitle("Vos données")
            Text("Tout reste sur ce téléphone : pas d'accès à internet, pas de compte, pas d'analytique.")
            SectionTitle("Licence")
            Text("Logiciel libre, gratuit et sans publicité, sous licence GNU GPL version 3 ou ultérieure.")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
