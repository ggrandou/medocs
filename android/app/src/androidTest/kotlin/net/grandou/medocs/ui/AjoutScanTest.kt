package net.grandou.medocs.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.domain.CodeBoite
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Enchaînement « code lu par le scanner → médicament retrouvé → formulaire prérempli », avec un faux scanner. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AjoutScanTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun lancer(code: CodeBoite) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent {
            AppTheme {
                AjoutScreen(vm, onTermine = {}, ecranScan = { onCode, _ -> LaunchedEffect(Unit) { onCode(code) } })
            }
        }
        rule.onNodeWithText("Scanner le code-barres de la boîte").performClick()
    }

    @Test fun unCodeConnuOuvreLeFormulairePrerempli() {
        lancer(CodeBoite("3400935955838", lot = "LOT42"))
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Quantité par boîte (facultatif)")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("DOLIPRANE 1000 mg, comprimé").assertIsDisplayed()   // nom
        rule.onNodeWithText("PARACÉTAMOL 1000 mg").assertIsDisplayed()            // molécule
        rule.onNodeWithText("comprimé").assertIsDisplayed()                       // unité
        // 8 comprimés par boîte, déduits du libellé de la présentation, en quantité par boîte ET en stock proposé
        rule.onAllNodes(hasText("8")).fetchSemanticsNodes().let { assert(it.size >= 2) { "8 attendu deux fois, vu ${it.size}" } }
    }

    @Test fun unCodeInconnuProposeLesAlternatives() {
        lancer(CodeBoite("3400000000000"))
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Code non trouvé")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Scanner un autre code").assertIsDisplayed()
        rule.onNodeWithText("Saisie manuelle (garde ce code)").assertIsDisplayed()
        rule.onNodeWithText("Chercher par le nom").assertIsDisplayed()
        rule.onNodeWithText("Saisie manuelle (garde ce code)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Quantité par boîte (facultatif)")).fetchSemanticsNodes().isNotEmpty() }
    }
}
