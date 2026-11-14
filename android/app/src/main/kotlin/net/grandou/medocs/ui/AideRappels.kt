package net.grandou.medocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun AideRappelsScreen(onRetour: () -> Unit, onReglagesBatterie: () -> Unit, onReglagesApp: () -> Unit) {
    SousEcran("Des rappels fiables", onRetour) {
        IllustrationPage(Dessin.CLOCHE)
        Text(
            "Pour économiser la batterie, Android et surtout Samsung « endorment » les applications peu utilisées, " +
                "ce qui peut retarder ou supprimer un rappel. Une application de rappel doit en être exclue.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Etape("1. Le plus simple", "Ouvre les réglages de l'application, touche « Batterie », puis choisis « Sans restriction ».") {
            OutlinedButton(onClick = onReglagesApp) { Text("Ouvrir les réglages de l'application") }
        }
        Etape(
            "2. Ou la liste d'optimisation",
            "Touche le filtre en haut et choisis « Toutes les applications » : par défaut, la liste ne montre que les applications déjà exclues, donc pas celle-ci. " +
                "Puis choisis « Ne pas optimiser » pour Mes Médocs.",
        ) { OutlinedButton(onClick = onReglagesBatterie) { Text("Ouvrir la liste d'optimisation") } }
        Etape("3. Sur Samsung, en plus", "Paramètres > Batterie > Limites d'utilisation en arrière-plan. Désactive « Mettre en veille les applis inutilisées » et ajoute Mes Médocs à « Applications jamais en veille ».")
        Etape("4. Veille profonde", "Si ton téléphone propose de mettre l'application en « veille profonde », refuse.")
        Etape("5. Autorisations", "Laisse les notifications et les alarmes exactes autorisées pour l'application.")
    }
}

@Composable
private fun Etape(titre: String, texte: String, action: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(texte, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
