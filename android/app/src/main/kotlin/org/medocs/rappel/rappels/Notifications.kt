package org.medocs.rappel.rappels

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import org.medocs.rappel.MainActivity
import org.medocs.rappel.data.Medicament
import org.medocs.rappel.domain.Occurrence
import org.medocs.rappel.domain.ProjectionStock
import org.medocs.rappel.ui.formaterAvecUnite
import org.medocs.rappel.ui.formaterQuantite

object Notifications {
    private const val CANAL_RAPPELS = "rappels"
    private const val CANAL_STOCK = "stock"

    const val ACTION_PRENDRE = "prendre"
    const val ACTION_SAUTER = "sauter"
    const val ACTION_REPORTER = "reporter"

    /** Identifiant stable d'une occurrence (heure de planning + jour de l'année) pour pouvoir l'annuler. */
    fun idOccurrence(o: Occurrence): Int = (o.planningHeure.id * 1000 + o.date.dayOfYear).toInt()

    private fun gestionnaire(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL_RAPPELS) == null) {
            nm.createNotificationChannel(NotificationChannel(CANAL_RAPPELS, "Rappels de prise", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CANAL_STOCK, "Stock faible", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return nm
    }

    /**
     * Notifications activées pour l'app. `areNotificationsEnabled` couvre à la fois la permission
     * d'exécution (Android 13+) et le réglage système des versions précédentes, où
     * `checkSelfPermission(POST_NOTIFICATIONS)` renvoie « refusé » à tort.
     */
    fun autorisees(c: Context): Boolean = c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun ouvrirApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun action(c: Context, o: Occurrence, m: Medicament, code: String, rang: Int, titre: String): Notification.Action {
        val i = Intent(c, ActionReceiver::class.java).setAction(code)
            .putExtra(ActionReceiver.EXTRA_MED, m.id)
            .putExtra(ActionReceiver.EXTRA_PH, o.planningHeure.id)
            .putExtra(ActionReceiver.EXTRA_DATE, o.date.toString())
            .putExtra(ActionReceiver.EXTRA_HEURE, o.heure.toString())
            .putExtra(ActionReceiver.EXTRA_QTE, o.quantite)
        val pi = PendingIntent.getBroadcast(c, idOccurrence(o) * 4 + rang, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_menu_agenda), titre, pi).build()
    }

    fun rappel(c: Context, m: Medicament, o: Occurrence, reporte: Boolean) {
        val nm = gestionnaire(c)
        if (!autorisees(c)) return
        val texte = "${formaterAvecUnite(o.quantite, m.unite)} — ${o.heure}" + if (reporte) " (rappel reporté)" else ""
        val n = Notification.Builder(c, CANAL_RAPPELS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(m.nom)
            .setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(ouvrirApp(c))
            .setOngoing(false)
            .addAction(action(c, o, m, Notifications.ACTION_PRENDRE, 0, "Pris"))
            .addAction(action(c, o, m, Notifications.ACTION_REPORTER, 1, "Reporter ${Moteur.REPORT_MIN} min"))
            .addAction(action(c, o, m, Notifications.ACTION_SAUTER, 2, "Sauter"))
            .build()
        nm.notify(idOccurrence(o), n)
    }

    fun annuler(c: Context, o: Occurrence) = c.getSystemService(NotificationManager::class.java).cancel(idOccurrence(o))

    fun stockFaible(c: Context, m: Medicament, stock: Double, projection: ProjectionStock) {
        val nm = gestionnaire(c)
        if (!autorisees(c)) return
        val detail = when (projection) {
            is ProjectionStock.Rupture -> when (projection.joursRestants) {
                0 -> "insuffisant dès aujourd'hui"
                1 -> "rupture demain"
                else -> "rupture dans ${projection.joursRestants} jours"
            }
            else -> "à renouveler"
        }
        val n = Notification.Builder(c, CANAL_STOCK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Stock faible : ${m.nom}")
            .setContentText("Il reste ${formaterAvecUnite(stock.coerceAtLeast(0.0), m.unite)} (${detail})")
            .setContentIntent(ouvrirApp(c))
            .setAutoCancel(true)
            .build()
        nm.notify(-(m.id.toInt() + 1), n)
    }
}
