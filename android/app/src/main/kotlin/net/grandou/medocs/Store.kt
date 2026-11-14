package net.grandou.medocs

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Mode { EXACT, ALARM_CLOCK }

data class Pending(val id: Int, val triggerAt: Long, val mode: Mode)

/** Alarmes en attente + journal, persistés en SharedPreferences (suffisant pour le prototype). */
object Store {
    private const val PREFS = "proto"
    private const val KEY_PENDING = "pending"
    private const val KEY_LOG = "log"
    private const val KEY_NEXT_ID = "next_id"
    private const val MAX_LOG = 200
    private val fmt get() = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nextId(c: Context): Int {
        val id = prefs(c).getInt(KEY_NEXT_ID, 1)
        prefs(c).edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    @Synchronized
    fun pending(c: Context): List<Pending> =
        prefs(c).getStringSet(KEY_PENDING, emptySet())!!.mapNotNull {
            val p = it.split("|")
            if (p.size == 3) Pending(p[0].toInt(), p[1].toLong(), Mode.valueOf(p[2])) else null
        }.sortedBy { it.triggerAt }

    @Synchronized
    fun addPending(c: Context, p: Pending) {
        val set = prefs(c).getStringSet(KEY_PENDING, emptySet())!!.toMutableSet()
        set.add("${p.id}|${p.triggerAt}|${p.mode}")
        prefs(c).edit().putStringSet(KEY_PENDING, set).commit()
    }

    @Synchronized
    fun removePending(c: Context, id: Int) {
        val set = prefs(c).getStringSet(KEY_PENDING, emptySet())!!.filterNot { it.startsWith("$id|") }.toSet()
        prefs(c).edit().putStringSet(KEY_PENDING, set).commit()
    }

    @Synchronized
    fun log(c: Context, msg: String) {
        val lines = prefs(c).getString(KEY_LOG, "")!!.lines().filter { it.isNotBlank() }.toMutableList()
        lines.add(0, "${fmt.format(Date())}  $msg")
        prefs(c).edit().putString(KEY_LOG, lines.take(MAX_LOG).joinToString("\n")).commit()
    }

    fun logText(c: Context): String = prefs(c).getString(KEY_LOG, "")!!

    fun clearLog(c: Context) = prefs(c).edit().remove(KEY_LOG).apply()

    fun time(t: Long): String = fmt.format(Date(t))
}
