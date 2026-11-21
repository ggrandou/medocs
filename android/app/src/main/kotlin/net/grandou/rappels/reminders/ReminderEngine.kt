package net.grandou.rappels.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.grandou.rappels.Permission
import net.grandou.rappels.Settings
import net.grandou.rappels.data.AppDatabase
import net.grandou.rappels.data.Repository
import net.grandou.rappels.data.Status
import net.grandou.rappels.engine.AlarmPlanner
import net.grandou.rappels.engine.StockLevel
import net.grandou.rappels.ui.EntryState
import net.grandou.rappels.ui.buildLine
import net.grandou.rappels.ui.buildToday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Alarm engine. Idempotent and driven by the state of the database, not by events: [sync] looks at what is
 * planned and answered, then (1) writes "missed" for past occurrences left without answer, (2) shows or removes
 * the reminders of the occurrences waiting for an answer, (3) raises the low-stock alerts, (4) arms the SINGLE
 * system alarm for the next time it has to run. Calling it too often is harmless; calling it late (phone off,
 * alarm killed by the vendor) catches up.
 *
 * Called by the alarm, by the system events (boot, clock, time zone, update, exact-alarm permission), after every
 * change of data, and when the app comes to the foreground.
 */
object ReminderEngine {
    private const val ALARM_REQUEST = 4242
    private const val PREFS = "engine"
    private const val KEY_LAST_RUN = "last_run"
    private const val KEY_NOTIFIED = "notified"
    private const val KEY_ALERTS = "alerts"

    /** Past occurrences are marked missed back to this many days at most (phone left off for a long time). */
    private const val MAX_LOOKBACK_DAYS = 31L

    private val lock = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Fire and forget, from the UI. [refreshStyle]: redraw the notifications already shown (persistent setting changed). */
    fun launchSync(context: Context, refreshStyle: Boolean = false) {
        val app = context.applicationContext
        scope.launch { sync(app, refreshStyle) }
    }

    /** After a restore, the days before today belong to the restored data: nothing in them is "missed" by this phone. */
    fun forgetPast(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_RUN, LocalDate.now().toString()).apply()
    }

    suspend fun sync(context: Context, refreshStyle: Boolean = false) = lock.withLock {
        val ctx = context.applicationContext
        val repo = Repository(AppDatabase.get(ctx))
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()

        // Archived items neither ring nor alert
        val rows = repo.itemsWithStock().first()
        val activeIds = rows.map { it.item.id }.toSet()
        val schedules = repo.activeSchedules().filter { it.schedule.itemId in activeIds }

        // 0. Clock set back: "missed" rows of today or later were written while it was ahead, drop them
        repo.clearFutureMissed(today)

        // 1. Day change: what was left without answer before today is missed
        val lastRun = prefs.getString(KEY_LAST_RUN, null)?.let(LocalDate::parse) ?: today
        val from = maxOf(lastRun, today.minusDays(MAX_LOOKBACK_DAYS))
        if (from < today) {
            val past = repo.scheduledRows(from, today.minusDays(1))
            AlarmPlanner.toMarkMissed(schedules, past, from, today).forEach { repo.markMissed(it.scheduleTimeId, it.date) }
        }
        prefs.edit().putString(KEY_LAST_RUN, today.toString()).apply()

        val todayRows = repo.scheduledRows(today, today)
        if (Notifications.enabled(ctx)) {
            // 2. Reminders
            val due = buildToday(rows.map { it.item }, schedules, todayRows, now, zone).filter { it.state == EntryState.DUE }
            syncReminders(ctx, due, Settings(ctx).persistentReminders, prefs, refreshStyle, today, zone)
            // 3. Low stock, once a day per item
            val handled = todayRows.filter { it.status != Status.SNOOZED }.map { it.scheduleTimeId!! to it.scheduledDate!! }.toSet()
            val sent = prefs.getStringSet(KEY_ALERTS, emptySet())!!.filter { it.endsWith("|$today") }.toMutableSet()
            rows.map { buildLine(it, schedules, now, handled) }.filter { it.needsAttention }.forEach { line ->
                if (sent.add("${line.item.id}|$today")) Notifications.postLowStock(ctx, line)
            }
            prefs.edit().putStringSet(KEY_ALERTS, sent).apply()
        }

        // 4. Next alarm
        arm(ctx, if (schedules.isEmpty()) null else AlarmPlanner.nextWake(schedules, todayRows, now, zone))
    }

    /**
     * Persistent: every occurrence waiting for an answer has its notification, put back if it is missing (swiped away).
     * Not persistent: each is shown once ([KEY_NOTIFIED] remembers it), so a dismissed one stays dismissed.
     * In both cases the notifications of occurrences that got an answer (or that are not today's) are removed.
     */
    private fun syncReminders(
        ctx: Context,
        due: List<net.grandou.rappels.ui.TodayEntry>,
        persistent: Boolean,
        prefs: android.content.SharedPreferences,
        refreshStyle: Boolean,
        today: LocalDate,
        zone: ZoneId,
    ) {
        val shown = Notifications.shownReminderIds(ctx)
        val wanted = due.associateBy { Notifications.reminderId(it.planned.scheduleTimeId, it.planned.date) }
        (shown - wanted.keys).forEach { Notifications.cancelReminder(ctx, it) }

        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet())!!.filter { "|$today|" in it }.toMutableSet()
        for ((id, e) in wanted) {
            // A new postponement is a new reminder: its end instant is part of the key
            val key = "${e.planned.scheduleTimeId}|${e.planned.date}|${e.row?.snoozedUntil ?: 0}"
            val isShown = id in shown
            val post = when {
                isShown -> refreshStyle
                persistent -> true
                else -> key !in notified
            }
            if (post) Notifications.postReminder(ctx, e, persistent, zone)
            notified += key
        }
        prefs.edit().putStringSet(KEY_NOTIFIED, notified).apply()
    }

    /**
     * Exact alarm if the user allowed "Alarmes et rappels" (denied by default since Android 14, reset at every
     * reinstall; `setAlarmClock` needs it too). Otherwise an INEXACT alarm, possibly late in deep sleep.
     */
    private fun arm(ctx: Context, instant: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST, Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (instant == null) { am.cancel(pi); return }
        try {
            if (Permission.EXACT_ALARMS.isGranted(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the call: degrade rather than crash
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant, pi)
        }
    }
}
