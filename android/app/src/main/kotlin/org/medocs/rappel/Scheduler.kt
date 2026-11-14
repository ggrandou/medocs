package org.medocs.rappel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    const val EXTRA_ID = "id"
    const val EXTRA_AT = "at"
    const val EXTRA_MODE = "mode"

    private fun alarmManager(c: Context) = c.getSystemService(AlarmManager::class.java)

    fun canExact(c: Context) = alarmManager(c).canScheduleExactAlarms()

    private fun pi(c: Context, p: Pending): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra(EXTRA_ID, p.id).putExtra(EXTRA_AT, p.triggerAt).putExtra(EXTRA_MODE, p.mode.name)
        return PendingIntent.getBroadcast(c, p.id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Programme l'alarme auprès du système. Retourne false si le mode demandé est indisponible. */
    fun schedule(c: Context, p: Pending): Boolean {
        val am = alarmManager(c)
        return when (p.mode) {
            Mode.EXACT -> {
                if (!am.canScheduleExactAlarms()) return false
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.triggerAt, pi(c, p))
                true
            }
            Mode.ALARM_CLOCK -> {
                val show = PendingIntent.getActivity(
                    c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(p.triggerAt, show), pi(c, p))
                true
            }
        }
    }

    fun create(c: Context, delayMs: Long, mode: Mode): Boolean {
        val p = Pending(Store.nextId(c), System.currentTimeMillis() + delayMs, mode)
        if (!schedule(c, p)) {
            Store.log(c, "REFUSÉ : ${mode} indisponible (permission alarmes exactes ?)")
            return false
        }
        Store.addPending(c, p)
        Store.log(c, "programmée #${p.id} ${mode} pour ${Store.time(p.triggerAt)}")
        return true
    }

    /** Reprogramme toutes les alarmes en attente ; celles dont l'heure est passée sont déclenchées tout de suite. */
    fun rescheduleAll(c: Context, reason: String) {
        val now = System.currentTimeMillis()
        val list = Store.pending(c)
        Store.log(c, "$reason : ${list.size} alarme(s) en attente à reprogrammer")
        for (p in list) {
            if (p.triggerAt <= now) {
                Store.log(c, "#${p.id} manquée (prévue ${Store.time(p.triggerAt)}) : rattrapage immédiat")
                Store.removePending(c, p.id)
                Notifier.show(c, p.id, "Rappel manqué (rattrapé)", "Prévu à ${Store.time(p.triggerAt)}")
            } else if (!schedule(c, p)) {
                Store.log(c, "#${p.id} non reprogrammée : ${p.mode} indisponible")
            }
        }
    }
}
