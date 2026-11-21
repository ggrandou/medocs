package net.grandou.rappels

import android.content.Context

/** Appearance mode chosen by the user. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Du système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** App settings (SharedPreferences, local to the phone). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString("theme", null))
        set(v) = prefs.edit().putString("theme", v.key).apply()

    /** Instant (epoch ms) of the last backup made from this phone; 0 = never. */
    var lastBackup: Long
        get() = prefs.getLong("last_backup", 0L)
        set(v) = prefs.edit().putLong("last_backup", v).apply()

    /**
     * Persistent reminders (default): the notification stays until the occurrence gets an answer and comes back
     * if swiped away. Off: an ordinary notification that the user can dismiss for good.
     */
    var persistentReminders: Boolean
        get() = prefs.getBoolean("persistent_reminders", true)
        set(v) = prefs.edit().putBoolean("persistent_reminders", v).apply()

    /** Version of the start-up carousel the user has gone through; 0 = never. */
    var onboardingVersion: Int
        get() = prefs.getInt("onboarding_version", 0)
        set(v) = prefs.edit().putInt("onboarding_version", v).apply()

    /** Version of the medical warning the user has accepted; 0 = never. */
    var warningVersion: Int
        get() = prefs.getInt("warning_version", 0)
        set(v) = prefs.edit().putInt("warning_version", v).apply()
}
