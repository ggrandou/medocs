package org.medocs.rappel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.medocs.rappel.ui.AjoutScreen
import org.medocs.rappel.ui.AppTheme
import org.medocs.rappel.ui.AppViewModel
import org.medocs.rappel.ui.ListeScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                var ajout by rememberSaveable { mutableStateOf(false) }
                val lignes by vm.lignes.collectAsStateWithLifecycle()
                if (ajout) {
                    AjoutScreen(vm, onTermine = { ajout = false })
                } else {
                    ListeScreen(
                        lignes = lignes,
                        onAjouter = { ajout = true },
                        onProtoAlarmes = { startActivity(Intent(this, ProtoAlarmesActivity::class.java)) },
                        onAjouterStock = { l, q -> vm.ajouterStock(l.medicament.id, q) },
                        onDefinirStock = { l, q -> vm.definirStock(l.medicament.id, q) },
                        onSupprimer = { vm.supprimer(it.medicament) },
                    )
                }
            }
        }
    }
}
