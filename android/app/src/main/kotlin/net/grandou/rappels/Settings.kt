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
}
