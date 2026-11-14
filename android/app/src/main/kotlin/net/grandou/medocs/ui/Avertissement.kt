package net.grandou.medocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/** À incrémenter si le texte change de façon significative : l'utilisateur doit alors le ré-accepter. */
const val VERSION_AVERTISSEMENT = 1

/**
 * Avertissement affiché au premier lancement (bloquant : ni retour ni clic extérieur ne le ferme) puis
 * consultable depuis « À propos ». [versionApp] est affichée en bas.
 */
@Composable
fun AvertissementDialog(premiereFois: Boolean, versionApp: String, onFermer: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!premiereFois) onFermer() },
        properties = DialogProperties(dismissOnBackPress = !premiereFois, dismissOnClickOutside = !premiereFois),
        title = { Text(if (premiereFois) "Avant de commencer" else "À propos et avertissement") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Un simple aide-mémoire", fontWeight = FontWeight.SemiBold)
                Text(
                    "Cette application te rappelle l'heure de tes prises et suit ton stock. Elle ne donne aucun conseil médical : " +
                        "elle ne vérifie ni les doses, ni les interactions, ni les contre-indications, et ne remplace ni ton médecin ni ton pharmacien.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Vérifie toujours", fontWeight = FontWeight.SemiBold)
                Text(
                    "La liste des médicaments peut être incomplète ou dépassée. Ce que tu saisis (doses, horaires, stock) est de ta responsabilité : " +
                        "contrôle-le sur ta boîte et ton ordonnance.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Un rappel peut échouer", fontWeight = FontWeight.SemiBold)
                Text(
                    "Téléphone éteint, batterie faible, économie d'énergie, volume coupé, notifications désactivées… Pour un traitement dont un oubli " +
                        "serait grave, ne te fie pas uniquement à cette application.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Tes données restent chez toi", fontWeight = FontWeight.SemiBold)
                Text(
                    "L'application n'a aucun accès à internet et ne partage rien. Rien n'est sauvegardé ailleurs que sur ce téléphone, " +
                        "sauf le fichier chiffré que tu exportes toi-même.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Données des médicaments : Base de données publique des médicaments (ANSM, HAS, Assurance Maladie), Licence Ouverte 2.0 (Etalab).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Logiciel libre et gratuit, sous licence GNU GPL version 3 ou ultérieure : tu peux le copier, l'étudier et le modifier. Il est fourni sans aucune garantie.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Version $versionApp", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text(if (premiereFois) "J'ai compris" else "Fermer") } },
    )
}
