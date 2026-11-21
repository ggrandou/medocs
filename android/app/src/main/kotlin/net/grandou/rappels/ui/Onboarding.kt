package net.grandou.rappels.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Bump to show the carousel again after a major change. */
const val ONBOARDING_VERSION = 1

/** Bump to ask users to accept the warning again after its wording changes materially. */
const val WARNING_VERSION = 1

private const val PAGE_COUNT = 5
private const val WARNING_PAGE = 3

/**
 * Start-up carousel: presentation, first steps, tips, warning (must be accepted), settings that make reminders reliable.
 * @param replay true when the user reopened it from "Plus": it can be closed at any time.
 */
@Composable
fun Onboarding(
    replay: Boolean,
    warningAccepted: Boolean,
    appVersion: String,
    onAcceptWarning: () -> Unit,
    onFinish: () -> Unit,
) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == PAGE_COUNT - 1
    val blocked = !warningAccepted && page == WARNING_PAGE

    // Nobody gets past the warning without accepting it (swiping included)
    LaunchedEffect(page, warningAccepted) {
        if (!warningAccepted && page > WARNING_PAGE) pager.scrollToPage(WARNING_PAGE)
    }
    BackHandler(enabled = page > 0 || replay) {
        if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } else onFinish()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (replay) TextButton(onClick = onFinish) { Text("Fermer") } else Box(Modifier.size(48.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !blocked) { n ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    when (n) {
                        0 -> PageIntro()
                        1 -> PageFirstSteps()
                        2 -> PageTips()
                        3 -> PageWarning(warningAccepted, appVersion)
                        else -> PageSettings()
                    }
                }
            }
            // Dots are decorative for TalkBack, which reads the "Étape X sur N" text instead
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.Center) {
                repeat(PAGE_COUNT) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (i == page) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                "Étape ${page + 1} sur $PAGE_COUNT", Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (page > 0) OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(page - 1) } }) { Text("Précédent") }
                when {
                    blocked -> Button(onClick = { onAcceptWarning(); scope.launch { pager.animateScrollToPage(page + 1) } }) {
                        Text("J'ai compris et j'accepte")
                    }
                    last -> Button(onClick = onFinish) { Text(if (replay) "Fermer" else "Commencer") }
                    else -> Button(onClick = { scope.launch { pager.animateScrollToPage(page + 1) } }) { Text("Suivant") }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, Modifier.semantics { heading() }.padding(bottom = 8.dp), style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Point(title: String, text: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PageIntro() {
    Illustration(Drawing.APP_ICON)
    Title("Bienvenue dans T'as pensé à ?")
    Text("Des rappels qui demandent si c'est fait, et qui surveillent ce qui s'épuise.", style = MaterialTheme.typography.titleMedium)
    Point("Pour tout ce qui revient", "Médicaments, lentilles, croquettes, filtres, vitamines…")
    Point("Un rappel qui insiste", "Il reste affiché jusqu'à votre réponse : fait, reporté ou ignoré.")
    Point("Un stock surveillé", "Vous êtes prévenu avant d'en manquer.")
    Point("100 % privé", "Pas de compte, pas d'internet : tout reste sur ce téléphone.")
}

@Composable
private fun PageFirstSteps() {
    Illustration(Drawing.EMPTY_BOX)
    Title("Premiers pas")
    Point("1. Ajoutez ce que vous suivez", "Onglet Rappels : un nom, une unité, et si vous voulez, le stock de départ.")
    Point("2. Programmez-le", "Heures, quantités, jours : chaque élément peut avoir plusieurs rythmes.")
    Point("3. Répondez au rappel", "« Fait », « Reporter » (30 min) ou « Ignorer », depuis la notification ou l'onglet Aujourd'hui.")
}

@Composable
private fun PageTips() {
    Illustration(Drawing.FREE_DAY)
    Title("Quelques conseils")
    Point("Comptez votre stock au départ", "Les alertes de stock bas en dépendent.")
    Point("Une prise hors planning ?", "Le bouton « + Prise ponctuelle » de l'onglet Aujourd'hui la note et décompte le stock.")
    Point("Un oubli ? Corrigez après coup", "L'onglet Historique permet de modifier n'importe quel jour.")
    Point("Faites une sauvegarde", "Menu Plus : c'est le seul moyen de garder vos données si vous changez de téléphone.")
}

@Composable
private fun PageWarning(accepted: Boolean, appVersion: String) {
    Illustration(Drawing.ALL_DONE)
    Title("Avant d'aller plus loin")
    Text(
        "Si vous l'utilisez pour des rappels de médicaments, gardez à l'esprit que l'application n'est pas un dispositif médical. " +
            "Elle ne donne aucun conseil : elle ne vérifie ni les doses, ni les interactions, ni les contre-indications, " +
            "et ne remplace ni votre médecin ni votre pharmacien. Elle vous rappelle ce que vous avez vous-même programmé.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text("Version $appVersion", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        if (accepted) "Vous avez déjà accepté cet avertissement." else "Pour continuer, touchez « J'ai compris et j'accepte ».",
        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PageSettings() {
    Title("Pour que les rappels sonnent à l'heure")
    PermissionsPanel()
    Text("Retrouvez cette présentation à tout moment dans « Plus ».", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
}
