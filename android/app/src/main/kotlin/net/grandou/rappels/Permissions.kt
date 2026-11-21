package net.grandou.rappels

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** What the reminders need from the system to ring reliably. */
enum class Permission {
    NOTIFICATIONS, EXACT_ALARMS, BATTERY;

    /** Whether this setting exists on this Android version (exact alarms need no permission before Android 12). */
    val applicable: Boolean get() = this != EXACT_ALARMS || Build.VERSION.SDK_INT >= 31

    fun isGranted(c: Context): Boolean = when (this) {
        NOTIFICATIONS -> c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        EXACT_ALARMS -> Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        BATTERY -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    }

    /** Screen of the system settings where the user can grant it (no extra permission needed to open it). */
    fun settingsIntent(c: Context): Intent {
        val pkg = Uri.parse("package:${c.packageName}")
        return when (this) {
            NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            BATTERY -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg) // app page: Battery > Unrestricted
        }
    }

    companion object {
        /** The settings relevant on this device, in display order. */
        val shown: List<Permission> get() = entries.filter { it.applicable }

        /** Runtime permission for notifications (Android 13+), null before: a settings screen is the only way. */
        val notificationsRuntime: String? get() =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

        fun runtimeGranted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }
}
