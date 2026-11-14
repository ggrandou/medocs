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
import androidx.compose.ui.test.hasClickAction
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

/** Les libellés « J'ai pris », « Reporter », « Sauter » ne doivent JAMAIS être coupés ni rognés, quelles que soient la largeur d'écran et la police. */
@RunWith(AndroidJUnit4::class)
class BoutonsActionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var largeurDp by mutableStateOf(360)
    private var echelle by mutableStateOf(1f)

    private val m = Medicament(id = 1, nom = "DOLIPRANE 1000 mg, comprimé", unite = "comprimé")
    private val occ = Occurrence(PlanningHeure(id = 1, planningId = 1, heure = LocalTime.of(12, 0), quantite = 1.0), LocalDate.now(), 1)
    private val etat = EtatAujourdhui(LocalDate.now(), listOf(LigneAujourdhui(occ, m, null, EtatOccurrence.EN_RETARD)), emptyList(), emptyList())

    /**
     * Un bouton d'action doit avoir EXACTEMENT la largeur de son contenu (icône + texte + marges de Material) :
     * plus étroit, son texte est rogné (« Reporte ») ; plus large, une largeur lui a été imposée (partage forcé entre boutons).
     * Retourne la description du défaut, ou null si tout va bien.
     */
    private fun defautDe(libelle: String): String? {
        val texte = rule.onAllNodes(hasText(libelle), useUnmergedTree = true).fetchSemanticsNodes().first { SemanticsActions.GetTextLayoutResult in it.config }
        val resultats = mutableListOf<TextLayoutResult>()
        texte.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultats)
        val r = resultats.first()
        if (r.lineCount != 1) return "sur ${r.lineCount} lignes"
        val bouton = rule.onAllNodes(hasText(libelle) and hasClickAction()).fetchSemanticsNodes().first()
        val marges = (16 + 18 + 8 + 24) * rule.activity.resources.displayMetrics.density // début + icône + espace + fin
        val attendu = marges + r.getLineRight(0)
        val reel = bouton.size.width.toFloat()
        return when {
            reel < attendu - 2f -> "TEXTE ROGNÉ : bouton de ${reel.toInt()} px, il en faut ${attendu.toInt()}"
            reel > attendu + 2f -> "largeur imposée : bouton de ${reel.toInt()} px pour un contenu de ${attendu.toInt()}"
            else -> null
        }
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
                defautDe(libelle)?.let { coupes += "« $libelle » $it à ${w} dp, police x$e" }
            }
        }
        assertTrue("Libellés coupés :\n" + coupes.joinToString("\n"), coupes.isEmpty())
    }
}
