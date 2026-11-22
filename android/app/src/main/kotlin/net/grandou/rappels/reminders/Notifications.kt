package net.grandou.rappels.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import net.grandou.rappels.MainActivity
import net.grandou.rappels.R
import net.grandou.rappels.engine.StockLevel
import net.grandou.rappels.ui.ItemLine
import net.grandou.rappels.ui.SNOOZE_MINUTES
import net.grandou.rappels.ui.TodayEntry
import net.grandou.rappels.ui.formatDays
import net.grandou.rappels.ui.formatTime
import net.grandou.rappels.ui.formatWithUnit
import java.time.ZoneId

/** Builds, posts and cancels the notifications: one per occurrence waiting for an answer, one per low-stock item. */
object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_STOCK = "stock"

    const val ACTION_DONE = "net.grandou.rappels.DONE"
    const val ACTION_SNOOZE = "net.grandou.rappels.SNOOZE"
    const val ACTION_SKIP = "net.grandou.rappels.SKIP"
    /** Fired by the system when the user swipes the notification away. */
    const val ACTION_DISMISSED = "net.grandou.rappels.DISMISSED"

    const val EXTRA_SCHEDULE_TIME = "schedule_time"
    const val EXTRA_DATE = "date"

    /** Stable id of an occurrence's notification (never negative: negative ids are the stock alerts). */
    fun reminderId(scheduleTimeId: Long, date: java.time.LocalDate): Int = "$scheduleTimeId|$date".hashCode() and 0x7fffffff

    private fun stockId(itemId: Long): Int = -(itemId.toInt() + 1)

    private fun manager(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_REMINDERS) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Rappels", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_STOCK, "Stock faible", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return nm
    }

    /** `areNotificationsEnabled` covers the Android 13+ runtime permission and the system setting of older versions. */
    fun enabled(c: Context): Boolean = c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Ids of the reminder notifications currently shown (stock alerts excluded). */
    fun shownReminderIds(c: Context): Set<Int> =
        c.getSystemService(NotificationManager::class.java).activeNotifications.map { it.id }.filter { it >= 0 }.toSet()

    fun cancelReminder(c: Context, id: Int) = c.getSystemService(NotificationManager::class.java).cancel(id)

    private fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /**
     * A broadcast for one occurrence and one kind of action. The intent is told apart by its action and data
     * (extras do not count for [PendingIntent] identity), so each button and each occurrence has its own.
     */
    private fun occurrenceIntent(c: Context, e: TodayEntry, action: String): PendingIntent {
        val data = Uri.parse("rappels://occurrence/${e.planned.scheduleTimeId}/${e.planned.date}")
        val i = Intent(c, NotificationActionReceiver::class.java).setAction(action).setData(data)
            .putExtra(EXTRA_SCHEDULE_TIME, e.planned.scheduleTimeId)
            .putExtra(EXTRA_DATE, e.planned.date.toString())
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(c: Context, e: TodayEntry, code: String, icon: Int, title: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(c, icon), title, occurrenceIntent(c, e, code)).build()

    /**
     * Posts (or updates, silently) the reminder of an occurrence waiting for an answer.
     * [persistent]: ongoing, and put back by [ACTION_DISMISSED] if swiped away; otherwise an ordinary
     * notification, dismissed for good when swiped or tapped.
     */
    fun postReminder(c: Context, e: TodayEntry, persistent: Boolean, zone: ZoneId) {
        val nm = manager(c)
        val text = (listOfNotNull(e.planned.quantity?.let { formatWithUnit(it, e.item.unit) }, formatTime(e.planned.time)))
            .joinToString(" · ") + if (e.row != null) " (reporté)" else ""
        val b = Notification.Builder(c, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(e.item.name)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(e.planned.dateTime.atZone(zone).toInstant().toEpochMilli())
            .setShowWhen(true)
            // A refresh (new style, catch-up) must not ring again
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(c))
            .addAction(action(c, e, ACTION_DONE, R.drawable.ic_check, "Fait"))
            .addAction(action(c, e, ACTION_SNOOZE, R.drawable.ic_schedule, "Reporter ${SNOOZE_MINUTES} min"))
            .addAction(action(c, e, ACTION_SKIP, R.drawable.ic_skip, "Ignorer"))
        if (persistent) {
            // Not swipeable before Android 14; since then the system lets it go, hence the delete intent that puts it back.
            b.setOngoing(true).setAutoCancel(false).setDeleteIntent(occurrenceIntent(c, e, ACTION_DISMISSED))
        } else {
            b.setOngoing(false).setAutoCancel(true)
        }
        nm.notify(reminderId(e.planned.scheduleTimeId, e.planned.date), b.build())
    }

    fun postLowStock(c: Context, line: ItemLine) {
        val nm = manager(c)
        val item = line.item
        val title = if (line.level == StockLevel.OUT) "Bientôt épuisé : ${item.name}" else "Stock faible : ${item.name}"
        val days = line.coverage?.days
        val text = "Il reste ${formatWithUnit(line.stock.coerceAtLeast(0.0), item.unit)}" + when {
            line.stock <= 0.0 -> ", plus rien pour le prochain rappel"
            days == 0 -> ", pas assez pour le prochain rappel"
            days != null -> " (environ ${formatDays(days)})"
            else -> ""
        }
        nm.notify(
            stockId(item.id),
            Notification.Builder(c, CHANNEL_STOCK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .build(),
        )
    }
}
