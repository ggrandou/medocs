package net.grandou.medocs.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.Occurrence
import net.grandou.medocs.domain.ProjectionStock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Rend les écrans avec des données fictives, à police normale et à 200 %, et lance les vérifications
 * automatiques d'accessibilité d'Android (taille des zones tactiles, contraste, libellés manquants…).
 * Les captures d'écran sont écrites dans le dossier externe de l'application pour relecture.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibiliteTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val aujourdhui = LocalDate.now()
    private val pantoprazole = Medicament(
        id = 1, nom = "PANTOPRAZOLE SANDOZ 40 mg, comprimé gastro-résistant", substances = "PANTOPRAZOLE 40 mg",
        unite = "comprimé", unitesParBoite = 28.0,
    )
    private val doliprane = Medicament(id = 2, nom = "DOLIPRANE 1000 mg, comprimé", substances = "PARACÉTAMOL 1000 mg", unite = "comprimé")

    private fun occurrence(id: Long, h: Int, m: Medicament) =
        Occurrence(PlanningHeure(id = id, planningId = id, heure = LocalTime.of(h, 0), quantite = 1.0), aujourdhui, m.id)

    private fun prise(o: Occurrence, s: StatutPrise) = Prise(
        id = o.planningHeure.id, medicamentId = o.medicamentId, planningHeureId = o.planningHeure.id, datePrevue = o.date,
        heurePrevue = o.heure, statut = s, quantite = 1.0, horodatage = System.currentTimeMillis(), reporteJusqua = System.currentTimeMillis() + 1_800_000,
    )

    private val etat: EtatAujourdhui get() {
        val a = occurrence(1, 8, pantoprazole); val b = occurrence(2, 12, doliprane); val c = occurrence(3, 20, pantoprazole); val d = occurrence(4, 22, doliprane)
        return EtatAujourdhui(
            date = aujourdhui,
            lignes = listOf(
                LigneAujourdhui(a, pantoprazole, prise(a, StatutPrise.PRISE), EtatOccurrence.PRISE),
                LigneAujourdhui(b, doliprane, null, EtatOccurrence.EN_RETARD),
                LigneAujourdhui(c, pantoprazole, prise(c, StatutPrise.REPORTEE), EtatOccurrence.REPORTEE),
                LigneAujourdhui(d, doliprane, null, EtatOccurrence.A_VENIR),
            ),
            ponctuelles = listOf(PonctuelleAujourdhui(prise(a, StatutPrise.PRISE).copy(id = 99, planningHeureId = null), doliprane)),
            manqueesRecentes = listOf(PonctuelleAujourdhui(prise(b, StatutPrise.MANQUEE).copy(datePrevue = aujourdhui.minusDays(1)), pantoprazole)),
        )
    }

    private val lignes = listOf(
        LigneMedicament(pantoprazole, 5.0, ProjectionStock.Rupture(aujourdhui.plusDays(5), 5), alerte = true),
        LigneMedicament(doliprane, 40.0, ProjectionStock.SansPlanning, alerte = false),
    )

    private var densite: Density = Density(1f)

    private fun controlerSemantique(nom: String, echelle: Float) {
        val problemes = rule.problemesDAccessibilite(densite)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(File(ctx.getExternalFilesDir(null), "captures"), "$nom-${(echelle * 100).toInt()}.txt").writeText(problemes.joinToString("\n"))
        org.junit.Assert.assertTrue("Problèmes d'accessibilité ($nom, ${(echelle * 100).toInt()} %) :\n" + problemes.joinToString("\n"), problemes.isEmpty())
    }

    private fun verifier(nom: String, echelle: Float, controleA11y: Boolean = true, contenu: @Composable () -> Unit) {
        rule.setContent {
            net.grandou.medocs.ui.AppTheme {
                val d = LocalDensity.current
                densite = Density(d.density, echelle)
                CompositionLocalProvider(LocalDensity provides Density(d.density, echelle)) { contenu() }
            }
        }
        rule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dossier = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
        File(dossier, "$nom-${(echelle * 100).toInt()}.png").outputStream().use {
            // Une boîte de dialogue a sa propre racine : on prend la dernière (la plus au premier plan)
            rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        if (controleA11y) controlerSemantique(nom, echelle)
    }

    @Composable private fun aujourdhuiScreen() = AujourdhuiScreen(
        etat = etat, medicaments = listOf(pantoprazole, doliprane), onPrendre = {}, onSauter = {}, onReporter = { _, _ -> }, onAnnuler = {}, onHorsPlanning = { _, _ -> },
        alertes = listOf(AlerteAutorisation("L'économie d'énergie du téléphone peut retarder ou bloquer tes rappels.", "Voir comment l'éviter", ignorer = {}, action = {})),
    )

    @Composable private fun listeScreen() = ListeScreen(
        lignes = lignes, onAjouter = {}, onPlanning = {}, onModifier = {},
        onHistorique = {}, onAjouterStock = { _, _ -> }, onDefinirStock = { _, _ -> }, onSupprimer = {},
    )

    @Composable private fun plusScreen() = PlusScreen(
        theme = "systeme", onTheme = {}, archives = emptyList(), onRestaurer = {}, onSauvegarde = {}, onPriseEnMain = {}, onAPropos = {}, onAideRappels = {}, onProtoAlarmes = {},
    )

    /** Témoin : un composant volontairement fautif DOIT être rejeté, sinon les autres tests ne prouveraient rien. */
    @Test fun leVerificateurDetecteUnComposantMalConcu() {
        var erreur: Throwable? = null
        try {
            verifier("temoin", 1f) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.androidx_petitCliquable()) {}
            }
        } catch (e: Throwable) { erreur = e }
        org.junit.Assert.assertNotNull("le contrôle d'accessibilité aurait dû échouer", erreur)
    }

    /** Un exemplaire de chaque statut (dont « manquée », « sautée »), pour relire les couleurs sur la capture. */
    @Test fun lesSixStatutsSontRendus() {
        val os = (1..6).map { occurrence(it.toLong(), 6 + it, doliprane) }
        val etats = listOf(EtatOccurrence.PRISE to StatutPrise.PRISE, EtatOccurrence.REPORTEE to StatutPrise.REPORTEE, EtatOccurrence.EN_RETARD to null,
            EtatOccurrence.MANQUEE to StatutPrise.MANQUEE, EtatOccurrence.SAUTEE to StatutPrise.SAUTEE, EtatOccurrence.A_VENIR to null)
        val lignes6 = os.zip(etats).map { (o, e) -> LigneAujourdhui(o, doliprane, e.second?.let { prise(o, it) }, e.first) }
        verifier("statuts", 1f) {
            AujourdhuiScreen(etat = EtatAujourdhui(aujourdhui, lignes6, emptyList(), emptyList()), medicaments = listOf(doliprane), onPrendre = {}, onSauter = {},
                onReporter = { _, _ -> }, onAnnuler = {}, onHorsPlanning = { _, _ -> })
        }
    }

    @Test fun aujourdhuiPoliceNormale() = verifier("aujourdhui", 1f) { aujourdhuiScreen() }
    @Test fun aujourdhuiPolice200() = verifier("aujourdhui", 2f) { aujourdhuiScreen() }
    @Test fun listePoliceNormale() = verifier("liste", 1f) { listeScreen() }
    @Test fun listePolice200() = verifier("liste", 2f) { listeScreen() }
    @Test fun plusPoliceNormale() = verifier("plus", 1f) { plusScreen() }
    @Test fun plusPolice200() = verifier("plus", 2f) { plusScreen() }
    @Test fun avertissementPoliceNormale() = verifier("avertissement", 1f, controleA11y = false) { AvertissementDialog(premiereFois = true, versionApp = "0.1.0") {} }
    @Test fun avertissementPolice200() = verifier("avertissement", 2f, controleA11y = false) { AvertissementDialog(premiereFois = true, versionApp = "0.1.0") {} }


    // --- Ce que TalkBack lit ---

    @Test fun lesBoutonsDUnePriseDisentDeQuellePriseIlSAgit() {
        rule.setContent { net.grandou.medocs.ui.AppTheme { aujourdhuiScreen() } }
        for (action in listOf("J'ai pris", "Reporter", "Sauter")) {
            rule.onNodeWithContentDescription("$action : DOLIPRANE 1000 mg, comprimé, 12:00").assertHasClickAction()
        }
        rule.onNodeWithContentDescription("Annuler : PANTOPRAZOLE SANDOZ 40 mg, comprimé gastro-résistant, 08:00").assertHasClickAction()
    }

    @Test fun lesSectionsSontDesTitres() {
        rule.setContent { net.grandou.medocs.ui.AppTheme { aujourdhuiScreen() } }
        // Le titre est en bas de la liste : on y fait défiler (une liste paresseuse n'affiche que ce qui est visible)
        rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("Prises hors planning"))
        rule.onNodeWithText("Prises hors planning").assert(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
    }

    @Test fun uneCarteDePriseSeLitCommeUnePhrase() {
        rule.setContent { net.grandou.medocs.ui.AppTheme { aujourdhuiScreen() } }
        // heure, nom, dose et état sont fusionnés en un seul élément : un seul focus TalkBack
        rule.onNode(androidx.compose.ui.test.hasText("DOLIPRANE 1000 mg, comprimé", substring = true) and androidx.compose.ui.test.hasText("En retard", substring = true))
            .assertExists()
    }

    @Test fun uneVignetteDecorativeNEstPasLueEnDouble() {
        // Dans la liste, le nom est déjà lu : aucune image ne doit s'annoncer en plus
        rule.setContent { net.grandou.medocs.ui.AppTheme { listeScreen() } }
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Photo de la boîte")).assertCountEquals(0)
    }
}

/** 16 dp cliquables, sans libellé : trop petit (< 48 dp) et muet pour TalkBack. */
private fun androidx.compose.ui.Modifier.androidx_petitCliquable(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.size(16.dp).clickable { })
