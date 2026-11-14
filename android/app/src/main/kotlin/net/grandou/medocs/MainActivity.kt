package net.grandou.medocs

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import net.grandou.medocs.ui.AjoutScreen
import net.grandou.medocs.rappels.Moteur
import net.grandou.medocs.rappels.Notifications
import androidx.activity.result.contract.ActivityResultContracts
import net.grandou.medocs.ui.ImportDialog
import net.grandou.medocs.ui.MessageDialog
import net.grandou.medocs.ui.SauvegardeDialog
import androidx.compose.runtime.remember
import net.grandou.medocs.ui.AvertissementDialog
import net.grandou.medocs.ui.VERSION_AVERTISSEMENT
import net.grandou.medocs.ui.AideRappelsDialog
import net.grandou.medocs.ui.AlerteAutorisation
import net.grandou.medocs.ui.AppTheme
import net.grandou.medocs.ui.AppViewModel
import net.grandou.medocs.ui.AujourdhuiScreen
import net.grandou.medocs.ui.HistoriqueScreen
import net.grandou.medocs.ui.ListeScreen
import net.grandou.medocs.ui.ModifierMedicamentScreen
import net.grandou.medocs.ui.PlanningScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    // Rafraîchis à chaque retour dans l'app (l'utilisateur revient des réglages système)
    private var alarmesExactes by mutableStateOf(true)
    private var notifications by mutableStateOf(true)
    private var batterieOptimisee by mutableStateOf(false)
    private var aideIgnoree by mutableStateOf(false)
    private var avertissementAccepte by mutableStateOf(true) // vrai par défaut pour éviter un clignotement avant la lecture des préférences
    // Sauvegarde : le mot de passe saisi attend le retour du sélecteur de fichier ; il n'est jamais enregistré
    private var motDePasseExport: CharArray? = null
    private var fichierAImporter by mutableStateOf<android.net.Uri?>(null)

    private val choisirFichierExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val mdp = motDePasseExport
        motDePasseExport = null
        if (uri != null && mdp != null) vm.exporter(uri, mdp) else mdp?.fill('\u0000')
    }
    private val choisirFichierImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> fichierAImporter = uri }

    private val prefsUi by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    override fun onResume() {
        super.onResume()
        alarmesExactes = Moteur.alarmesExactesAutorisees(this)
        notifications = Notifications.autorisees(this)
        batterieOptimisee = !getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        aideIgnoree = prefsUi.getBoolean("aide_rappels_ignoree", false)
        avertissementAccepte = prefsUi.getInt("avertissement_version", 0) >= VERSION_AVERTISSEMENT
        vm.rafraichirRappels()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.autorisees(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            AppTheme {
                var onglet by rememberSaveable { mutableStateOf(0) }
                var ajout by rememberSaveable { mutableStateOf(false) }
                var planningDe by rememberSaveable { mutableLongStateOf(0L) } // id du médicament, 0 = aucun
                var modificationDe by rememberSaveable { mutableLongStateOf(0L) }
                var historiqueDe by rememberSaveable { mutableLongStateOf(0L) }
                var aide by rememberSaveable { mutableStateOf(false) }
                var sauvegarde by rememberSaveable { mutableStateOf(false) }
                var aPropos by rememberSaveable { mutableStateOf(false) }
                val versionApp = remember { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }
                if (!avertissementAccepte) {
                    AvertissementDialog(premiereFois = true, versionApp = versionApp) {
                        prefsUi.edit().putInt("avertissement_version", VERSION_AVERTISSEMENT).apply()
                        avertissementAccepte = true
                    }
                } else if (aPropos) {
                    AvertissementDialog(premiereFois = false, versionApp = versionApp) { aPropos = false }
                }
                val messageSauvegarde by vm.messageSauvegarde.collectAsStateWithLifecycle()
                if (sauvegarde) SauvegardeDialog(
                    onFermer = { sauvegarde = false },
                    onExporter = { mdp ->
                        motDePasseExport = mdp
                        sauvegarde = false
                        choisirFichierExport.launch("medocs-${java.time.LocalDate.now()}.medocs")
                    },
                    onImporter = { sauvegarde = false; choisirFichierImport.launch(arrayOf("*/*")) },
                )
                fichierAImporter?.let { uri ->
                    ImportDialog(
                        onAnnuler = { fichierAImporter = null },
                        onConfirmer = { mdp -> vm.importer(uri, mdp); fichierAImporter = null },
                    )
                }
                messageSauvegarde?.let { MessageDialog(it) { vm.messageSauvegarde.value = null } }
                val archives by vm.archives.collectAsStateWithLifecycle()
                val lignes by vm.lignes.collectAsStateWithLifecycle()
                val aujourdhui by vm.aujourdhui.collectAsStateWithLifecycle()

                if (aide) AideRappelsDialog(
                    onFermer = { aide = false },
                    onReglagesBatterie = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    onReglagesApp = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                )
                val medicamentPlanning = lignes?.firstOrNull { it.medicament.id == planningDe }?.medicament
                val medicamentModif = lignes?.firstOrNull { it.medicament.id == modificationDe }?.medicament
                val medicamentHisto = lignes?.firstOrNull { it.medicament.id == historiqueDe }?.medicament
                when {
                    medicamentModif != null -> ModifierMedicamentScreen(vm, medicamentModif, onRetour = { modificationDe = 0L })
                    medicamentHisto != null -> HistoriqueScreen(vm, medicamentHisto, onRetour = { historiqueDe = 0L })
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
                                    alertes = buildList {
                                        if (batterieOptimisee && !aideIgnoree) add(AlerteAutorisation(
                                            "L'économie d'énergie du téléphone peut retarder ou bloquer tes rappels.",
                                            "Voir comment l'éviter",
                                            ignorer = { prefsUi.edit().putBoolean("aide_rappels_ignoree", true).apply(); aideIgnoree = true },
                                            action = { aide = true },
                                        ))
                                        if (!notifications) add(AlerteAutorisation(
                                            "Les notifications sont désactivées : aucun rappel ne s'affichera.", "Ouvrir les réglages",
                                        ) { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) })
                                        if (!alarmesExactes) add(AlerteAutorisation(
                                            "Alarmes exactes non autorisées : les rappels peuvent être retardés de plusieurs minutes, surtout téléphone en veille.",
                                            "Autoriser les alarmes exactes",
                                        ) { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) })
                                    },
                                )
                            } else {
                                ListeScreen(
                                    lignes = lignes,
                                    onAjouter = { ajout = true },
                                    onProtoAlarmes = { startActivity(Intent(this@MainActivity, ProtoAlarmesActivity::class.java)) },
                                    onAideRappels = { aide = true },
                                    onSauvegarde = { sauvegarde = true },
                                    onAPropos = { aPropos = true },
                                    onPlanning = { planningDe = it.medicament.id },
                                    onModifier = { modificationDe = it.medicament.id },
                                    onHistorique = { historiqueDe = it.medicament.id },
                                    archives = archives,
                                    onRestaurer = { vm.modifierMedicament(it.copy(archive = false)) },
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
