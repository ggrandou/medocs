package net.grandou.medocs.rappels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.grandou.medocs.ProtoAlarmesActivity
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.data.Repository
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.domain.Echeance
import net.grandou.medocs.domain.Rappel
import net.grandou.medocs.domain.evenementsEntre
import net.grandou.medocs.domain.projeterStock
import net.grandou.medocs.domain.prochainEvenement
import net.grandou.medocs.domain.stockFaible
import java.time.LocalDate
import java.time.ZoneId

/**
 * Moteur de rappels. Une SEULE alarme système est armée : celle du prochain événement (rappel,
 * report arrivé à échéance, passage en « manquée »). À son déclenchement, [executer] traite tout ce
 * qui est dû depuis la dernière exécution (ce qui rattrape aussi les événements d'un téléphone éteint)
 * puis arme l'alarme suivante.
 */
object Moteur {
    const val DELAI_MANQUE_MIN = 120L
    const val REPORT_MIN = 30
    private const val REQ_ALARME = 4242
    private const val PREFS = "moteur"
    private val verrou = Mutex()

    /** Délai avant « manquée » : 120 min par défaut ; la pref `delai_manque_min` permet de le raccourcir pour les tests. */
    private fun delaiMs(prefs: android.content.SharedPreferences) = prefs.getLong("delai_manque_min", DELAI_MANQUE_MIN) * 60_000L

    /**
     * @param traiter faux pour une simple replanification (après une modification de données par
     * l'utilisateur) : rien n'est notifié, seule l'alarme est recalée.
     */
    suspend fun executer(contexte: Context, traiter: Boolean = true) = verrou.withLock {
        val ctx = contexte.applicationContext
        val db = AppDatabase.get(ctx)
        val repo = Repository(db)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val delaiMs = delaiMs(prefs)
        val zone = ZoneId.systemDefault()
        val maintenant = System.currentTimeMillis()
        val limite = maintenant + 2_000 // tolérance : une alarme ne se déclenche jamais en avance, mais on reste prudent
        val derniere = prefs.getLong("derniere", -1).takeIf { it >= 0 } ?: maintenant
        val aujourdhui = LocalDate.now(zone)

        // Un médicament archivé ne génère plus ni rappel ni alerte
        val idsActifs = db.medicaments().tousActifs().map { it.id }.toSet()
        val plannings = db.plannings().tousLesActifs().filter { it.planning.medicamentId in idsActifs }
        var prises = db.prises().pourMoteur(aujourdhui.minusDays(3))

        if (traiter) {
            val evenements = evenementsEntre(plannings, prises, derniere, limite, zone, delaiMs)
            val manquees = evenements.filterIsInstance<Echeance>().map { it.occurrence.cle }.toSet()
            for (e in evenements) {
                val o = e.occurrence
                when (e) {
                    is Rappel -> if (o.cle !in manquees) {
                        db.medicaments().parId(o.medicamentId)?.let { Notifications.rappel(ctx, it, o, e.reporte) }
                    }
                    is Echeance -> {
                        // Re-lecture : l'utilisateur a pu répondre entre-temps (même verrou, mais autre processus possible)
                        val actuelle = db.prises().occurrence(o.planningHeure.id, o.date)
                        if (actuelle == null || actuelle.statut == StatutPrise.REPORTEE) {
                            repo.sauter(o.medicamentId, o.quantite, maintenant, o.planningHeure.id, o.date, o.heure, StatutPrise.MANQUEE)
                            Notifications.annuler(ctx, o)
                        }
                    }
                }
            }
            prises = db.prises().pourMoteur(aujourdhui.minusDays(3))
        }
        prefs.edit().putLong("derniere", if (traiter) limite else derniere).apply()

        alertesStock(ctx, db, plannings, prises, aujourdhui, prefs)
        programmer(ctx, prochainEvenement(plannings, prises, if (traiter) limite else maxOf(derniere, maintenant), zone, delaiMs)?.instant)
    }

    /** Une notification de stock faible au plus par médicament et par jour. */
    private suspend fun alertesStock(
        ctx: Context, db: AppDatabase,
        plannings: List<net.grandou.medocs.data.PlanningAvecHeures>,
        prises: List<net.grandou.medocs.data.Prise>,
        aujourdhui: LocalDate,
        prefs: android.content.SharedPreferences,
    ) {
        val cles = prises.filter { it.planningHeureId != null && it.statut != StatutPrise.REPORTEE && it.datePrevue != null && !it.datePrevue.isBefore(aujourdhui) }
            .map { it.planningHeureId!! to it.datePrevue!! }.toSet()
        val dejaEnvoyees = prefs.getStringSet("alertes", emptySet())!!.filter { it.endsWith(":$aujourdhui") }.toMutableSet()
        val avant = dejaEnvoyees.size
        db.medicaments().tousActifs().forEach { m ->
            val stock = db.stock().stock(m.id)
            val projection = projeterStock(stock, plannings.filter { it.planning.medicamentId == m.id }, aujourdhui, cles)
            if (stockFaible(m, stock, projection) && dejaEnvoyees.add("${m.id}:$aujourdhui")) {
                Notifications.stockFaible(ctx, m, stock, projection)
            }
        }
        if (dejaEnvoyees.size != avant) prefs.edit().putStringSet("alertes", dejaEnvoyees).apply()
    }

    /** Avant Android 12 (API 31), les alarmes exactes n'exigent aucune autorisation. */
    fun alarmesExactesAutorisees(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Alarme exacte si l'utilisateur a autorisé « Alarmes et rappels » (SCHEDULE_EXACT_ALARM, refusée
     * par défaut depuis Android 14 ; `setAlarmClock` l'exige aussi). Sinon alarme INEXACTE : elle
     * peut être retardée de plusieurs minutes en veille profonde, d'où le bandeau d'avertissement.
     */
    private fun programmer(ctx: Context, instant: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_ALARME, Intent(ctx, MoteurReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (instant == null) { am.cancel(pi); return }
        try {
            if (alarmesExactesAutorisees(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
        } catch (e: SecurityException) {
            // Autorisation retirée entre le test et l'appel : on dégrade plutôt que de planter
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
        }
    }
}
