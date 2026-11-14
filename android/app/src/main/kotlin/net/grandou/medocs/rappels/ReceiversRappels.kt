package net.grandou.medocs.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.data.Repository
import net.grandou.medocs.domain.Occurrence
import java.time.LocalDate
import java.time.LocalTime

/** Exécute [bloc] hors du thread principal sans que le système tue le récepteur avant la fin. */
fun BroadcastReceiver.asynchrone(bloc: suspend () -> Unit) {
    val attente = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { bloc() } finally { attente.finish() }
    }
}

/** Déclenché par l'alarme système : notifie ce qui est dû et arme l'alarme suivante. */
class MoteurReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = asynchrone { Moteur.executer(context) }
}

/** Boutons Pris / Reporter / Sauter de la notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = asynchrone {
        val med = intent.getLongExtra(EXTRA_MED, -1)
        val ph = intent.getLongExtra(EXTRA_PH, -1)
        val date = LocalDate.parse(intent.getStringExtra(EXTRA_DATE) ?: return@asynchrone)
        val heure = LocalTime.parse(intent.getStringExtra(EXTRA_HEURE) ?: return@asynchrone)
        val quantite = intent.getDoubleExtra(EXTRA_QTE, 0.0)
        if (med < 0 || ph < 0) return@asynchrone

        val repo = Repository(AppDatabase.get(context))
        val maintenant = System.currentTimeMillis()
        when (intent.action) {
            Notifications.ACTION_PRENDRE -> repo.prendre(med, quantite, maintenant, ph, date, heure)
            Notifications.ACTION_SAUTER -> repo.sauter(med, quantite, maintenant, ph, date, heure)
            Notifications.ACTION_REPORTER ->
                repo.reporter(med, quantite, maintenant, maintenant + Moteur.REPORT_MIN * 60_000L, ph, date, heure)
        }
        // Retire la notification (le rappel reporté en créera une nouvelle) puis recale l'alarme
        val fictive = net.grandou.medocs.data.PlanningHeure(id = ph, planningId = 0, heure = heure, quantite = quantite)
        Notifications.annuler(context, Occurrence(fictive, date, med))
        Moteur.executer(context, traiter = false)
    }

    companion object {
        const val EXTRA_MED = "med"
        const val EXTRA_PH = "ph"
        const val EXTRA_DATE = "date"
        const val EXTRA_HEURE = "heure"
        const val EXTRA_QTE = "qte"
    }
}
