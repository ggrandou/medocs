package org.medocs.rappel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.medocs.rappel.ui.AjoutScreen
import org.medocs.rappel.ui.AppTheme
import org.medocs.rappel.ui.AppViewModel
import org.medocs.rappel.ui.AujourdhuiScreen
import org.medocs.rappel.ui.ListeScreen
import org.medocs.rappel.ui.PlanningScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                var onglet by rememberSaveable { mutableStateOf(0) }
                var ajout by rememberSaveable { mutableStateOf(false) }
                var planningDe by rememberSaveable { mutableLongStateOf(0L) } // id du médicament, 0 = aucun
                val lignes by vm.lignes.collectAsStateWithLifecycle()
                val aujourdhui by vm.aujourdhui.collectAsStateWithLifecycle()

                val medicamentPlanning = lignes?.firstOrNull { it.medicament.id == planningDe }?.medicament
                when {
                    ajout -> AjoutScreen(vm, onTermine = { ajout = false })
                    medicamentPlanning != null -> PlanningScreen(vm, medicamentPlanning, onRetour = { planningDe = 0L })
                    else -> Scaffold(bottomBar = {
                        NavigationBar {
                            NavigationBarItem(onglet == 0, { onglet = 0 }, icon = { Text("📅") }, label = { Text("Aujourd'hui") })
                            NavigationBarItem(onglet == 1, { onglet = 1 }, icon = { Text("💊") }, label = { Text("Médicaments") })
                        }
                    }) { padding ->
                        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                            if (onglet == 0) {
                                AujourdhuiScreen(
                                    etat = aujourdhui,
                                    medicaments = lignes?.map { it.medicament }.orEmpty(),
                                    onPrendre = vm::prendre, onSauter = vm::sauter, onReporter = vm::reporter,
                                    onAnnuler = vm::annuler, onHorsPlanning = vm::priseHorsPlanning,
                                )
                            } else {
                                ListeScreen(
                                    lignes = lignes,
                                    onAjouter = { ajout = true },
                                    onProtoAlarmes = { startActivity(Intent(this@MainActivity, ProtoAlarmesActivity::class.java)) },
                                    onPlanning = { planningDe = it.medicament.id },
                                    onAjouterStock = { l, q -> vm.ajouterStock(l.medicament.id, q) },
                                    onDefinirStock = { l, q -> vm.definirStock(l.medicament.id, q) },
                                    onSupprimer = { vm.supprimer(it.medicament) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
