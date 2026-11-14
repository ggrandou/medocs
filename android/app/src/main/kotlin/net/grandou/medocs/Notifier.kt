package net.grandou.medocs

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager

object Notifier {
    private const val CHANNEL = "rappels"

    fun show(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rappels de prise", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (!net.grandou.medocs.rappels.Notifications.autorisees(c)) {
            Store.log(c, "#$id notification NON affichée : permission refusée")
            return
        }
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(net.grandou.medocs.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
