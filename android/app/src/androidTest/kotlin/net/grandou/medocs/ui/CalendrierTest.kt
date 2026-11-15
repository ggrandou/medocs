package net.grandou.medocs.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.Occurrence
import net.grandou.medocs.domain.StatutJour
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CalendrierTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val mois = YearMonth.of(2026, 11)
    private val aujourdhui = LocalDate.of(2026, 11, 15)
    private val moisChoisis = mutableListOf<YearMonth>()
    private val joursOuverts = mutableListOf<LocalDate>()

    private fun calendrier(m: YearMonth = mois, statuts: Map<LocalDate, StatutJour> = emptyMap()) = rule.setContent {
        AppTheme { CalendrierScreen(m, statuts, aujourdhui, { moisChoisis += it }, { joursOuverts += it }) }
    }

    @Test fun letitreDuMoisEtLesJoursSontAffiches() {
        calendrier()
        rule.onNodeWithText("Novembre 2026").assertIsDisplayed()
        // 1er novembre 2026 = dimanche : la grille compte bien 30 cases cliquables
        rule.onAllNodesWithDescription("novembre 2026").assertCountEquals(30)
    }

    @Test fun lesBoutonsDeMoisNavigentDeMoisEnMois() {
        calendrier()
        rule.onNodeWithContentDescription("Mois précédent").performClick()
        rule.onNodeWithContentDescription("Mois suivant").performClick()
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 12)), moisChoisis)
    }

    @Test fun passerDeDecembreAJanvierChangeAussiLAnnee() {
        calendrier(YearMonth.of(2026, 12))
        rule.onNodeWithContentDescription("Mois suivant").performClick()
        assertEquals(listOf(YearMonth.of(2027, 1)), moisChoisis)
    }

    @Test fun toucherUnJourLOuvre() {
        calendrier(statuts = mapOf(LocalDate.of(2026, 11, 3) to StatutJour.TOUT_PRIS))
        rule.onNodeWithContentDescription("3 novembre 2026, tout pris").performClick()
        rule.onNodeWithContentDescription("20 novembre 2026, aucune prise prévue").performClick() // un point aussi s'ouvre
        assertEquals(listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 20)), joursOuverts)
    }

    @Test fun lesStatutsSontDitsATalkBackEtAujourdhuiEstIndique() {
        calendrier(statuts = mapOf(
            aujourdhui to StatutJour.PARTIEL,
            LocalDate.of(2026, 11, 2) to StatutJour.AUCUNE_PRISE,
            LocalDate.of(2026, 11, 20) to StatutJour.A_VENIR_COUVERT,
            LocalDate.of(2026, 11, 25) to StatutJour.A_VENIR_SANS_STOCK,
        ))
        rule.onNodeWithContentDescription("15 novembre 2026, aujourd'hui, des prises sautées ou non faites").assertIsDisplayed()
        rule.onNodeWithContentDescription("2 novembre 2026, aucune prise faite").assertIsDisplayed()
        rule.onNodeWithContentDescription("20 novembre 2026, prises prévues, stock suffisant").assertIsDisplayed()
        rule.onNodeWithContentDescription("25 novembre 2026, prises prévues, stock insuffisant").assertIsDisplayed()
    }

    @Test fun leRetourAuMoisEnCoursSeProposeSeulementAilleurs() {
        calendrier()
        rule.onAllNodes(hasText("Revenir au mois en cours")).assertCountEquals(0)
    }

    @Test fun leRetourAuMoisEnCoursRamene() {
        calendrier(YearMonth.of(2027, 2))
        rule.onNodeWithText("Revenir au mois en cours").performClick()
        assertEquals(listOf(mois), moisChoisis)
    }

    /** Capture du calendrier en mode sombre pour relire les couleurs (dossier captures de l'application). */
    @Test fun captureEnModeSombre() {
        val ordre = StatutJour.entries
        rule.setContent {
            AppTheme(sombre = true) {
                CalendrierScreen(mois, (1..30).associate { mois.atDay(it) to ordre[it % ordre.size] }, aujourdhui, {}, {})
            }
        }
        rule.waitForIdle()
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dossier = java.io.File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
        java.io.File(dossier, "calendrier-sombre.png").outputStream().use {
            rule.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // --- Écran d'un jour passé ou à venir ---

    private val medicament = Medicament(id = 1, nom = "DOLIPRANE 1000 mg", unite = "comprimé")
    private fun ligne(date: LocalDate, etat: EtatOccurrence) =
        LigneAujourdhui(Occurrence(PlanningHeure(id = 1, planningId = 1, heure = LocalTime.of(8, 0), quantite = 1.0), date, 1), medicament, null, etat)

    private fun jour(date: LocalDate, quand: QuandJour, etat: EtatOccurrence, onSauter: (LigneAujourdhui) -> Unit = {}) = rule.setContent {
        AppTheme {
            AujourdhuiScreen(
                etat = EtatAujourdhui(date, listOf(ligne(date, etat)), emptyList()), medicaments = listOf(medicament),
                onPrendre = {}, onSauter = onSauter, onReporter = { _, _ -> }, onAnnuler = {}, onHorsPlanning = { _, _ -> },
                quand = quand, onRetour = {},
            )
        }
    }

    @Test fun unJourPasseNonRepondupropose_prendreEtSauterMaisPasReporter() {
        var saute: LigneAujourdhui? = null
        jour(LocalDate.of(2026, 11, 10), QuandJour.PASSE, EtatOccurrence.EN_RETARD) { saute = it }
        rule.onNodeWithText("Mardi 10 novembre 2026").assertIsDisplayed()
        rule.onNodeWithText("Non pris").assertIsDisplayed() // pas « En retard » pour un jour révolu
        rule.onNodeWithContentDescription("J'ai pris : DOLIPRANE 1000 mg, 08:00").assertIsDisplayed()
        rule.onAllNodes(hasContentDescription("Reporter : DOLIPRANE 1000 mg, 08:00")).assertCountEquals(0)
        rule.onNodeWithContentDescription("Sauter : DOLIPRANE 1000 mg, 08:00").performClick()
        assertEquals(1L, saute?.occurrence?.planningHeure?.id)
        rule.onNodeWithContentDescription("Retour").assertIsDisplayed()
    }

    @Test fun unJourFuturEstEnLectureSeule() {
        jour(LocalDate.of(2026, 11, 25), QuandJour.FUTUR, EtatOccurrence.A_VENIR)
        rule.onNodeWithText("Jour à venir").assertIsDisplayed()
        rule.onAllNodes(hasContentDescription("J'ai pris", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("Sauter", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasText("Prise hors planning", substring = true)).assertCountEquals(0)
    }

    @Test fun lEcranDAujourdhuiResteInchange() {
        jour(aujourdhui, QuandJour.AUJOURDHUI, EtatOccurrence.EN_RETARD)
        rule.onNodeWithText("Aujourd'hui").assertIsDisplayed()
        rule.onNodeWithText("En retard").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reporter : DOLIPRANE 1000 mg, 08:00").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithDescription(fin: String) =
        onAllNodes(hasContentDescription(fin, substring = true) and hasClickActionMatcher)
}

private val hasClickActionMatcher = androidx.compose.ui.test.hasClickAction()
