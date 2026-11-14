package org.medocs.rappel.ui

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
                Text("1. Ouvre les réglages de la batterie (bouton ci-dessous) et choisis « Sans restriction » ou « Non optimisée » pour Rappel médicaments.", style = MaterialTheme.typography.bodyMedium)
                Text("2. Sur Samsung : Paramètres > Batterie > Limites d'utilisation en arrière-plan. Désactive « Mettre en veille les applis inutilisées » et ajoute Rappel médicaments à « Applications jamais en veille ».", style = MaterialTheme.typography.bodyMedium)
                Text("3. Si ton téléphone propose de mettre l'application en « veille profonde », refuse.", style = MaterialTheme.typography.bodyMedium)
                Text("4. Laisse les notifications et les alarmes exactes autorisées pour l'application.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onReglagesBatterie) { Text("Ouvrir les réglages de la batterie") }
                OutlinedButton(onClick = onReglagesApp) { Text("Ouvrir les réglages de l'application") }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
