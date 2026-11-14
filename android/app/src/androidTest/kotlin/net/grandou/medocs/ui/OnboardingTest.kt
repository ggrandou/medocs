package net.grandou.medocs.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var acceptes = 0
    private var notifs = 0
    private var exactes = 0
    private var batterie = 0
    private var termine = 0
    private var accepte by mutableStateOf(false)
    private var densite = Density(1f)

    private val titres = listOf(
        "Bienvenue dans Mes Médocs", "Premiers pas", "Quelques conseils", "Avant d'aller plus loin", "Pour que les rappels sonnent à l'heure",
    )

    private fun lancer(
        revoir: Boolean = false, dejaAccepte: Boolean = false, echelle: Float = 1f,
        reglages: ReglagesRappels = ReglagesRappels(notifications = false, alarmesExactesAFaire = true, alarmesExactes = false, batterieOptimisee = true),
    ) {
        accepte = dejaAccepte
        rule.setContent {
            AppTheme {
                val d = LocalDensity.current
                densite = Density(d.density, echelle)
                CompositionLocalProvider(LocalDensity provides densite) {
                    Onboarding(
                        revoir = revoir, avertissementAccepte = accepte, reglages = reglages, versionApp = "0.1.0",
                        onAccepterAvertissement = { acceptes++; accepte = true },
                        onNotifications = { notifs++ }, onAlarmesExactes = { exactes++ }, onBatterie = { batterie++ },
                        onTerminer = { termine++ },
                    )
                }
            }
        }
    }

    private fun suivant() { rule.onNodeWithText("Suivant").performClick(); rule.waitForIdle() }
    private fun allerAPage(n: Int) { repeat(n) { if (it < 3) suivant() } }

    @Test fun parcoursDeLaPremiereFois() {
        lancer()
        rule.onNodeWithText(titres[0]).assertIsDisplayed()
        rule.onNodeWithText("Étape 1 sur 5").assertIsDisplayed()
        suivant(); rule.onNodeWithText(titres[1]).assertIsDisplayed()
        suivant(); rule.onNodeWithText(titres[2]).assertIsDisplayed()
        suivant(); rule.onNodeWithText(titres[3]).assertIsDisplayed()
        // Page d'avertissement : pas de « Suivant », un seul bouton qui vaut acceptation
        rule.onNodeWithText("J'ai compris et j'accepte").assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasText("Suivant")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("J'ai compris et j'accepte").performClick(); rule.waitForIdle()
        assertEquals(1, acceptes)
        rule.onNodeWithText(titres[4]).assertIsDisplayed()
        rule.onNodeWithText("Commencer").performClick()
        assertEquals(1, termine)
    }

    @Test fun onNePeutPasContournerLAvertissement() {
        lancer()
        allerAPage(3)
        rule.onNodeWithText(titres[3]).assertIsDisplayed()
        rule.onNodeWithText(titres[3]).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText(titres[3]).assertIsDisplayed() // le balayage ne passe pas
        assertEquals(0, acceptes)
        rule.onNodeWithText("Précédent").performClick(); rule.waitForIdle()
        rule.onNodeWithText(titres[2]).assertIsDisplayed() // mais on peut revenir en arrière
    }

    @Test fun lesReglagesNonFaitsProposentLesActions() {
        lancer(dejaAccepte = true)
        allerAPage(3); suivant()
        rule.onNodeWithText(titres[4]).assertIsDisplayed()
        assertEquals(3, rule.onAllNodes(hasText("À faire")).fetchSemanticsNodes().size)
        rule.onNodeWithText("Autoriser les notifications").performScrollTo().performClick()
        rule.onNodeWithText("Autoriser les alarmes exactes").performScrollTo().performClick()
        rule.onNodeWithText("Voir comment l'éviter").performScrollTo().performClick()
        assertEquals(listOf(1, 1, 1), listOf(notifs, exactes, batterie))
    }

    @Test fun lesReglagesFaitsSontAffichesCommeTels() {
        lancer(dejaAccepte = true, reglages = ReglagesRappels(notifications = true, alarmesExactesAFaire = true, alarmesExactes = true, batterieOptimisee = false))
        allerAPage(3); suivant()
        assertEquals(3, rule.onAllNodes(hasText("Fait ✔")).fetchSemanticsNodes().size)
        assertTrue(rule.onAllNodes(hasText("À faire")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasText("Autoriser", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test fun avantAndroid12PasDAlarmesExactesAAutoriser() {
        lancer(dejaAccepte = true, reglages = ReglagesRappels(notifications = true, alarmesExactesAFaire = false, alarmesExactes = true, batterieOptimisee = false))
        allerAPage(3); suivant()
        assertTrue(rule.onAllNodes(hasText("Alarmes exactes")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun relancerLeCarrouselPermetDeFermerEtMontreLAvertissementDejaAccepte() {
        lancer(revoir = true, dejaAccepte = true)
        rule.onNodeWithText("Fermer").assertIsDisplayed()
        allerAPage(3)
        rule.onNodeWithText("Tu as déjà accepté cet avertissement.").performScrollTo().assertIsDisplayed() // en bas de la page : on y défile
        rule.onNodeWithText("Suivant").assertIsDisplayed() // plus d'acceptation à refaire
        suivant()
        rule.onNodeWithText(titres[4]).assertIsDisplayed()
        rule.onAllNodes(hasText("Fermer")).fetchSemanticsNodes().let { assertEquals(2, it.size) } // en haut ET en bas
        rule.onAllNodes(hasText("Fermer"))[0].performClick()
        assertEquals(1, termine)
        assertEquals(0, acceptes)
    }

    @Test fun lePremierLancementNeProposePasDeFermer() {
        lancer()
        assertTrue(rule.onAllNodes(hasText("Fermer")).fetchSemanticsNodes().isEmpty())
    }

    // --- Accessibilité : zones tactiles, libellés, lisibilité à 200 % (captures dans le dossier externe de l'app) ---

    private fun verifierToutesLesPages(echelle: Float) {
        lancer(dejaAccepte = true, echelle = echelle)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dossier = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
        val problemes = mutableListOf<String>()
        for (i in titres.indices) {
            rule.onNodeWithText(titres[i]).assertIsDisplayed()
            File(dossier, "onboarding-${i + 1}-${(echelle * 100).toInt()}.png").outputStream().use {
                rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            problemes += rule.problemesDAccessibilite(densite).map { "page ${i + 1} : $it" }
            if (i < titres.size - 1) suivant()
        }
        assertTrue("Problèmes d'accessibilité à ${(echelle * 100).toInt()} % :\n" + problemes.joinToString("\n"), problemes.isEmpty())
    }

    @Test fun accessibilitePoliceNormale() = verifierToutesLesPages(1f)
    @Test fun accessibilitePolice200() = verifierToutesLesPages(2f)
}
