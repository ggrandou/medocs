package net.grandou.medocs.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.Occurrence
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime

/** Les libellés « J'ai pris », « Reporter », « Sauter » ne doivent JAMAIS être coupés, quelles que soient la largeur d'écran et la police. */
@RunWith(AndroidJUnit4::class)
class BoutonsActionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var largeurDp by mutableStateOf(360)
    private var echelle by mutableStateOf(1f)

    private val m = Medicament(id = 1, nom = "DOLIPRANE 1000 mg, comprimé", unite = "comprimé")
    private val occ = Occurrence(PlanningHeure(id = 1, planningId = 1, heure = LocalTime.of(12, 0), quantite = 1.0), LocalDate.now(), 1)
    private val etat = EtatAujourdhui(LocalDate.now(), listOf(LigneAujourdhui(occ, m, null, EtatOccurrence.EN_RETARD)), emptyList(), emptyList())

    private fun lignesDe(libelle: String): Int {
        val noeud = rule.onAllNodes(hasText(libelle), useUnmergedTree = true).fetchSemanticsNodes().first { SemanticsActions.GetTextLayoutResult in it.config }
        val resultats = mutableListOf<TextLayoutResult>()
        noeud.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultats)
        return resultats.first().lineCount
    }

    @Test fun lesLibellesRestentSurUneLigne() {
        rule.setContent {
            AppTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, echelle)) {
                    Box(Modifier.width(largeurDp.dp)) {
                        AujourdhuiScreen(etat = etat, medicaments = listOf(m), onPrendre = {}, onSauter = {}, onReporter = { _, _ -> }, onAnnuler = {}, onHorsPlanning = { _, _ -> })
                    }
                }
            }
        }
        val coupes = mutableListOf<String>()
        for (e in listOf(1f, 1.3f, 2f)) for (w in listOf(280, 320, 360, 411, 480, 600)) {
            echelle = e; largeurDp = w
            rule.waitForIdle()
            for (libelle in listOf("J'ai pris", "Reporter", "Sauter")) {
                if (lignesDe(libelle) != 1) coupes += "« $libelle » sur ${lignesDe(libelle)} lignes à ${w} dp, police x$e"
            }
        }
        assertTrue("Libellés coupés :\n" + coupes.joinToString("\n"), coupes.isEmpty())
    }
}
