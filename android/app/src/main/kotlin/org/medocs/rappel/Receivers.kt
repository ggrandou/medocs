package org.medocs.rappel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        val mode = intent.getStringExtra(Scheduler.EXTRA_MODE)
        val now = System.currentTimeMillis()
        Store.removePending(context, id)
        Store.log(context, "TIRÉE #$id $mode : prévue ${Store.time(at)}, écart ${(now - at) / 1000.0} s")
        Notifier.show(context, id, "Rappel #$id", "Prévu à ${Store.time(at)} ($mode)")
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raison = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Redémarrage du téléphone"
            Intent.ACTION_TIME_CHANGED -> "Changement de l'heure"
            Intent.ACTION_TIMEZONE_CHANGED -> "Changement de fuseau horaire"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "Mise à jour de l'application"
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> "Autorisation des alarmes exactes modifiée"
            else -> intent.action ?: "?"
        }
        Scheduler.rescheduleAll(context, raison)
    }
}
