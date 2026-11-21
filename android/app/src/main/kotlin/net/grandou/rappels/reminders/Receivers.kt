package net.grandou.rappels.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.grandou.rappels.data.AppDatabase
import net.grandou.rappels.data.Repository
import net.grandou.rappels.data.Status
import net.grandou.rappels.ui.SNOOZE_MINUTES
import java.time.LocalDate

/** Runs [block] off the main thread without the system killing the receiver before it ends. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { pending.finish() }
    }
}

/**
 * The alarm, and every system event after which the alarm may be wrong or lost: boot, clock or time zone change,
 * app update, exact-alarm permission change. They all do the same thing: [ReminderEngine.sync].
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async { ReminderEngine.sync(context) }

    companion object {
        const val ACTION_ALARM = "net.grandou.rappels.ALARM"
    }
}

/** The buttons of a reminder (Fait / Reporter / Ignorer), and its swiping away. Same repository as the screens. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val timeId = intent.getLongExtra(Notifications.EXTRA_SCHEDULE_TIME, -1)
        val date = intent.getStringExtra(Notifications.EXTRA_DATE)?.let(LocalDate::parse)
        if (timeId >= 0 && date != null) {
            val repo = Repository(AppDatabase.get(context))
            try {
                when (intent.action) {
                    Notifications.ACTION_DONE -> repo.respond(timeId, date, Status.DONE)
                    Notifications.ACTION_SKIP -> repo.respond(timeId, date, Status.SKIPPED)
                    Notifications.ACTION_SNOOZE ->
                        repo.respond(timeId, date, Status.SNOOZED, snoozedUntil = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000)
                    // Swiped away: nothing to write, the sync puts it back if it is persistent and still waiting
                }
            } catch (e: IllegalStateException) {
                // The schedule was deleted since: nothing left to answer
            }
        }
        ReminderEngine.sync(context)
    }
}
