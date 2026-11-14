package net.grandou.medocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Explique comment éviter que le système (surtout One UI de Samsung) endorme l'application et
 * retarde ou supprime les rappels. Aucune de ces options ne peut être activée par l'application.
 */
@Composable
fun AideRappelsDialog(onFermer: () -> Unit, onReglagesBatterie: () -> Unit, onReglagesApp: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Des rappels fiables") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pour économiser la batterie, Android et surtout Samsung « endorment » les applications peu utilisées, " +
                        "ce qui peut retarder ou supprimer un rappel. Une application de rappel doit en être exclue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("À faire une fois :", fontWeight = FontWeight.SemiBold)
                Text("1. Le plus simple : ouvre les réglages de l'application (premier bouton), touche « Batterie », puis choisis « Sans restriction ».", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReglagesApp) { Text("Ouvrir les réglages de l'application") }
                Text(
                    "2. Ou, dans la liste d'optimisation de la batterie (second bouton), touche le filtre en haut et choisis « Toutes les applications » : " +
                        "par défaut, la liste ne montre que les applications déjà exclues, donc pas celle-ci. Puis choisis « Ne pas optimiser » pour Mes Médocs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onReglagesBatterie) { Text("Ouvrir la liste d'optimisation de la batterie") }
                Text("3. Sur Samsung, en plus : Paramètres > Batterie > Limites d'utilisation en arrière-plan. Désactive « Mettre en veille les applis inutilisées » et ajoute Mes Médocs à « Applications jamais en veille ».", style = MaterialTheme.typography.bodyMedium)
                Text("4. Si ton téléphone propose de mettre l'application en « veille profonde », refuse.", style = MaterialTheme.typography.bodyMedium)
                Text("5. Laisse les notifications et les alarmes exactes autorisées pour l'application.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
