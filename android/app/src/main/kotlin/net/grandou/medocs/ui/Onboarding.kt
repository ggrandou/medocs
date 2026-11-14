package net.grandou.medocs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** À incrémenter pour réafficher le carrousel après une évolution majeure. */
const val VERSION_ONBOARDING = 1

/** État des réglages qui conditionnent la fiabilité des rappels (relu à chaque retour dans l'application). */
data class ReglagesRappels(
    val notifications: Boolean,
    /** Faux avant Android 12 : aucune autorisation à demander. */
    val alarmesExactesAFaire: Boolean,
    val alarmesExactes: Boolean,
    val batterieOptimisee: Boolean,
)

private const val NB_PAGES = 5
private const val PAGE_AVERTISSEMENT = 3

/**
 * Carrousel de prise en main : présentation, premiers pas, conseils, avertissement (à accepter), réglages.
 * @param revoir vrai quand l'utilisateur le relance lui-même : on peut le fermer à tout moment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(
    revoir: Boolean,
    avertissementAccepte: Boolean,
    reglages: ReglagesRappels,
    versionApp: String,
    onAccepterAvertissement: () -> Unit,
    onNotifications: () -> Unit,
    onAlarmesExactes: () -> Unit,
    onBatterie: () -> Unit,
    onTerminer: () -> Unit,
) {
    val pager = rememberPagerState(pageCount = { NB_PAGES })
    val portee = rememberCoroutineScope()
    val page = pager.currentPage
    val derniere = page == NB_PAGES - 1
    val bloque = !avertissementAccepte && page == PAGE_AVERTISSEMENT

    // On ne peut pas dépasser l'avertissement sans l'avoir accepté (balayage compris)
    LaunchedEffect(page, avertissementAccepte) {
        if (!avertissementAccepte && page > PAGE_AVERTISSEMENT) pager.scrollToPage(PAGE_AVERTISSEMENT)
    }
    BackHandler(enabled = page > 0 || revoir) {
        if (page > 0) portee.launch { pager.animateScrollToPage(page - 1) } else onTerminer()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (revoir) TextButton(onClick = onTerminer) { Text("Fermer") }
                else Box(Modifier.size(48.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !bloque) { numero ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    when (numero) {
                        0 -> PagePresentation()
                        1 -> PagePremiersPas()
                        2 -> PageConseils()
                        3 -> PageAvertissement(avertissementAccepte, versionApp)
                        else -> PageReglages(reglages, onNotifications, onAlarmesExactes, onBatterie)
                    }
                }
            }
            // Indicateur : décoratif pour TalkBack, qui lit le texte « Étape X sur N » à la place
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.Center) {
                repeat(NB_PAGES) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (i == page) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                "Étape ${page + 1} sur $NB_PAGES",
                Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (page > 0) OutlinedButton(onClick = { portee.launch { pager.animateScrollToPage(page - 1) } }) { Text("Précédent") }
                when {
                    bloque -> Button(onClick = { onAccepterAvertissement(); portee.launch { pager.animateScrollToPage(page + 1) } }) {
                        Text("J'ai compris et j'accepte")
                    }
                    derniere -> Button(onClick = onTerminer) { Text(if (revoir) "Fermer" else "Commencer") }
                    else -> Button(onClick = { portee.launch { pager.animateScrollToPage(page + 1) } }) { Text("Suivant") }
                }
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, Modifier.semantics { heading() }.padding(bottom = 8.dp), style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Point(titre: String, texte: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(texte, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PagePresentation() {
    IllustrationPage(Dessin.PILULE)
    Titre("Bienvenue dans Mes Médocs")
    Text("Ton aide-mémoire pour ne plus oublier tes médicaments.", style = MaterialTheme.typography.titleMedium)
    Point("Des rappels à l'heure", "Une notification à chaque prise.")
    Point("Un stock à jour", "Tu es prévenu avant la rupture.")
    Point("100 % privé", "Pas de compte, pas d'internet : tout reste sur ce téléphone.")
}

@Composable
private fun PagePremiersPas() {
    IllustrationPage(Dessin.BOITE)
    Titre("Premiers pas")
    Point("1. Ajoute un médicament", "Scanne sa boîte ou cherche son nom.")
    Point("2. Crée son planning", "Heures, doses, jours.")
    Point("3. Réponds au rappel", "« J'ai pris », Reporter ou Sauter.")
}

@Composable
private fun PageConseils() {
    IllustrationPage(Dessin.CONSEIL)
    Titre("Quelques conseils")
    Point("Compte ton stock au départ", "Les alertes en dépendent.")
    Point("Une prise hors planning ?", "Note-la dans l'onglet Aujourd'hui.")
    Point("Fais une sauvegarde", "Menu Plus : c'est le seul moyen de garder tes données si tu changes de téléphone.")
}

@Composable
private fun PageAvertissement(accepte: Boolean, versionApp: String) {
    IllustrationPage(Dessin.BOUCLIER)
    Titre("Avant d'aller plus loin")
    TexteAvertissement(versionApp, resume = true)
    Text(
        if (accepte) "Tu as déjà accepté cet avertissement." else "Pour continuer, touche « J'ai compris et j'accepte ».",
        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageReglages(r: ReglagesRappels, onNotifications: () -> Unit, onAlarmesExactes: () -> Unit, onBatterie: () -> Unit) {
    IllustrationPage(Dessin.CLOCHE)
    Titre("Pour que les rappels sonnent à l'heure")
    Reglage("Notifications", r.notifications, "Sans elles, aucun rappel.", "Autoriser les notifications", onNotifications)
    if (r.alarmesExactesAFaire) {
        Reglage("Alarmes exactes", r.alarmesExactes, "Pour sonner à la minute près.", "Autoriser les alarmes exactes", onAlarmesExactes)
    }
    Reglage(
        "Économie d'énergie", !r.batterieOptimisee,
        "Certains téléphones retardent les rappels.",
        "Voir comment l'éviter", onBatterie,
    )
    Text("Revois cette présentation à tout moment depuis « Plus ».", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
}

/** Une ligne de réglage : l'état est dit en toutes lettres (jamais par la seule couleur). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reglage(titre: String, ok: Boolean, explication: String, bouton: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (ok) "Fait ✔" else "À faire", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(explication, style = MaterialTheme.typography.bodyMedium)
            if (!ok) Button(onClick = onClick) { Text(bouton) }
        }
    }
}
