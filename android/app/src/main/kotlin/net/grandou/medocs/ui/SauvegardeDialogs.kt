package net.grandou.medocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

const val LONGUEUR_MIN_MOT_DE_PASSE = 8

@Composable
fun SauvegardeDialog(onFermer: () -> Unit, onExporter: (CharArray) -> Unit, onImporter: () -> Unit) {
    var mdp by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valide = mdp.length >= LONGUEUR_MIN_MOT_DE_PASSE && mdp == confirmation
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "L'application ne se connecte jamais à internet : ta seule sauvegarde est un fichier que tu gardes toi-même. " +
                        "Il est chiffré par un mot de passe que personne ne peut retrouver : s'il est perdu, le fichier est illisible.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Exporter", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    mdp, { mdp = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mot de passe (8 caractères minimum)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    confirmation, { confirmation = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Confirmer le mot de passe") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmation.isNotEmpty() && confirmation != mdp,
                )
                OutlinedButton(enabled = valide, onClick = { onExporter(mdp.toCharArray()) }) { Text("Exporter vers un fichier…") }
                Text("Importer", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onImporter) { Text("Restaurer depuis un fichier…") }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}

@Composable
fun ImportDialog(onAnnuler: () -> Unit, onConfirmer: (CharArray) -> Unit) {
    var mdp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Restaurer la sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Toutes les données actuelles (médicaments, plannings, journal, stock) seront REMPLACÉES par celles du fichier. " +
                        "Si le mot de passe est faux ou le fichier abîmé, rien n'est modifié.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    mdp, { mdp = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mot de passe du fichier") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(enabled = mdp.isNotEmpty(), onClick = { onConfirmer(mdp.toCharArray()) }) { Text("Remplacer mes données") } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}

@Composable
fun MessageDialog(message: String, onFermer: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFermer,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onFermer) { Text("OK") } },
    )
}
