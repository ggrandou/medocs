package net.grandou.medocs.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.Occurrence
import net.grandou.medocs.domain.ProjectionStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Rend les écrans dans les deux modes (clair et sombre) avec la barre d'onglets, pour relecture visuelle et contrôle des zones tactiles. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EsthetiqueTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val auj = LocalDate.now()
    private val a = Medicament(id = 1, nom = "PANTOPRAZOLE SANDOZ 40 mg, comprimé gastro-résistant", substances = "PANTOPRAZOLE 40 mg", unite = "comprimé")
    private val b = Medicament(id = 2, nom = "DOLIPRANE 1000 mg, comprimé", substances = "PARACÉTAMOL 1000 mg", unite = "comprimé")

    private fun occ(id: Long, h: Int, m: Medicament) = Occurrence(PlanningHeure(id = id, planningId = id, heure = LocalTime.of(h, 0), quantite = 1.0), auj, m.id)
    private fun prise(o: Occurrence, s: StatutPrise) = Prise(
        id = o.planningHeure.id, medicamentId = o.medicamentId, planningHeureId = o.planningHeure.id, datePrevue = o.date, heurePrevue = o.heure,
        statut = s, quantite = 1.0, horodatage = System.currentTimeMillis(),
    )

    private val etat: EtatAujourdhui get() {
        val o1 = occ(1, 8, a); val o2 = occ(2, 12, b); val o3 = occ(3, 20, a)
        return EtatAujourdhui(
            date = auj,
            lignes = listOf(
                LigneAujourdhui(o1, a, prise(o1, StatutPrise.PRISE), EtatOccurrence.PRISE),
                LigneAujourdhui(o2, b, null, EtatOccurrence.EN_RETARD),
                LigneAujourdhui(o3, a, null, EtatOccurrence.A_VENIR),
            ),
            ponctuelles = emptyList(), manqueesRecentes = emptyList(),
        )
    }
    private val lignes = listOf(
        LigneMedicament(a, 5.0, ProjectionStock.Rupture(auj.plusDays(5), 5), alerte = true),
        LigneMedicament(b, 40.0, ProjectionStock.Suffisant, alerte = false),
    )

    private var onglet by mutableStateOf(0)

    private fun capturer(nom: String) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dossier = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
        File(dossier, "$nom.png").outputStream().use {
            rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun appli(sombre: Boolean) {
        onglet = 0
        rule.setContent {
            AppTheme(sombre = sombre) {
                Scaffold(bottomBar = { BarreNavigation(onglet) { onglet = it } }) { padding ->
                    Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        if (onglet == 0) AujourdhuiScreen(
                            etat = etat, medicaments = listOf(a, b), onPrendre = {}, onSauter = {}, onReporter = { _, _ -> }, onAnnuler = {}, onHorsPlanning = { _, _ -> },
                        ) else ListeScreen(
                            lignes = lignes, onAjouter = {},
                            onPlanning = {}, onModifier = {}, onHistorique = {}, onAjouterStock = { _, _ -> },
                            onDefinirStock = { _, _ -> }, onSupprimer = {},
                        )
                    }
                }
            }
        }
    }

    private fun parcours(sombre: Boolean) {
        val mode = if (sombre) "sombre" else "clair"
        appli(sombre)
        rule.waitForIdle(); capturer("aujourdhui-$mode")
        val d = LocalDensityDeTest.valeur
        assertTrue("zones tactiles : " + rule.problemesDAccessibilite(androidx.compose.ui.unit.Density(d)), rule.problemesDAccessibilite(androidx.compose.ui.unit.Density(d)).isEmpty())
        rule.onNodeWithText("Médicaments").performClick(); rule.waitForIdle()
        assertEquals(1, onglet)
        capturer("medicaments-$mode")
        rule.onNodeWithText("Aujourd'hui").performClick(); rule.waitForIdle()
        assertEquals(0, onglet)
    }

    private object LocalDensityDeTest { val valeur: Float get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density }

    @Test fun modeClair() = parcours(sombre = false)
    @Test fun modeSombre() = parcours(sombre = true)

    @Test fun lOnboardingEnModeSombre() {
        rule.setContent {
            AppTheme(sombre = true) {
                Onboarding(
                    revoir = false, avertissementAccepte = true, versionApp = "0.1.0",
                    reglages = ReglagesRappels(notifications = true, alarmesExactesAFaire = true, alarmesExactes = false, batterieOptimisee = true),
                    onAccepterAvertissement = {}, onNotifications = {}, onAlarmesExactes = {}, onBatterie = {}, onTerminer = {},
                )
            }
        }
        rule.waitForIdle(); capturer("onboarding-1-sombre")
    }
}
