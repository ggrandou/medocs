package net.grandou.rappels.ui

import net.grandou.rappels.data.IntervalUnit
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

// Pure display helpers (French), tested on the JVM.

private val INVARIABLE_UNITS = setOf("ml", "l", "g", "mg", "kg", "cl")

/** "30" or "0,5": no useless decimals, French comma. */
fun formatQuantity(q: Double): String =
    if (q == Math.rint(q)) q.toLong().toString()
    else String.format(Locale.FRANCE, "%.2f", q).trimEnd('0').trimEnd(',')

/** "1 dose", "30 doses", "50 ml". French plural starts at 2 ("1,5 dose"). */
fun formatWithUnit(q: Double, unit: String): String {
    val u = unit.trim()
    val plural = abs(q) >= 2 && u.lowercase() !in INVARIABLE_UNITS && !u.endsWith("s") && !u.endsWith("x") && !u.endsWith("z")
    return "${formatQuantity(q)} ${if (plural) u + "s" else u}"
}

/** Tolerant decimal input (comma or dot); null if invalid. */
fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
private val LONG_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

fun formatDate(d: LocalDate): String = d.format(DATE_FORMAT)
fun formatTime(t: LocalTime): String = t.format(TIME_FORMAT)

/** "Samedi 21 novembre". */
fun formatLongDay(d: LocalDate): String = d.format(LONG_DAY_FORMAT).replaceFirstChar { it.uppercase() }

fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.FRANCE)
fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.FRANCE)

/** "aujourd'hui", "demain", "jeudi" (within the week), otherwise "jeudi 16 avril". */
fun relativeDay(date: LocalDate, today: LocalDate): String {
    val diff = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        diff == 0L -> "aujourd'hui"
        diff == 1L -> "demain"
        diff in 2..6 -> dayName(date.dayOfWeek)
        else -> date.format(LONG_DAY_FORMAT)
    }
}

private fun ordinal(rank: Int) = when (rank) {
    -1 -> "dernier"
    1 -> "1er"
    else -> "${rank}e"
}

private fun daysOf(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }

/** "Tous les jours", "Tous les 3 mois", "Lun., mer., ven.", "Le 1er lundi du mois", "21 jours, puis 7 jours de pause"… */
fun rhythmSummary(s: Schedule): String {
    val n = s.interval.coerceAtLeast(1)
    return when (s.recurrence) {
        Recurrence.PERIODIC -> when (s.intervalUnit) {
            IntervalUnit.DAY -> if (n == 1) "Tous les jours" else "Tous les $n jours"
            IntervalUnit.WEEK -> if (n == 1) "Toutes les semaines" else "Toutes les $n semaines"
            IntervalUnit.MONTH -> (if (n == 1) "Tous les mois" else "Tous les $n mois") + ", le ${s.startDate.dayOfMonth}"
            IntervalUnit.YEAR -> (if (n == 1) "Tous les ans" else "Tous les $n ans") +
                ", le ${s.startDate.dayOfMonth} " + s.startDate.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)
        }
        Recurrence.WEEKDAYS -> {
            val days = daysOf(s.weekdays)
            val base = when {
                days.isEmpty() -> "Aucun jour"
                days.size == 7 -> "Tous les jours"
                else -> days.joinToString(", ") { dayShort(it) }.replaceFirstChar { it.uppercase() }
            }
            if (n == 1 || days.isEmpty()) base else "$base, 1 semaine sur $n"
        }
        Recurrence.RANK_IN_MONTH -> {
            val day = daysOf(s.weekdays).firstOrNull()?.let { dayName(it) } ?: "jour"
            val base = "Le ${ordinal(s.rank ?: 1)} $day du mois"
            if (n == 1) base else "$base, tous les $n mois"
        }
        Recurrence.CYCLE -> {
            val a = s.activeDays ?: 0
            val p = s.pauseDays ?: 0
            "$a jour${if (a > 1) "s" else ""}" + if (p > 0) ", puis $p jour${if (p > 1) "s" else ""} de pause" else ""
        }
    }
}

/** "08:00 · 1 dose", or just "08:00" when there is no quantity. */
fun timeLine(t: ScheduleTime, unit: String): String =
    formatTime(t.time) + (t.quantity?.let { " · " + formatWithUnit(it, unit) } ?: "")

/** "Depuis le 16/11/2026", "À partir du …", "Du … au …", "Le … (une seule fois)". */
fun periodText(s: Schedule, today: LocalDate): String {
    val end = s.endDate
    return when {
        end == s.startDate -> "Le ${formatDate(s.startDate)} (une seule fois)"
        end != null -> "Du ${formatDate(s.startDate)} au ${formatDate(end)}"
        s.startDate > today -> "À partir du ${formatDate(s.startDate)}"
        else -> "Depuis le ${formatDate(s.startDate)}"
    }
}

/** "1 jour", "12 jours". */
fun formatDays(n: Int): String = if (n <= 1) "$n jour" else "$n jours"

/** "21/11/2026 à 14:05" for an instant (epoch ms), in the phone's time zone. */
fun formatInstant(epochMs: Long): String {
    val dt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), java.time.ZoneId.systemDefault())
    return formatDate(dt.toLocalDate()) + " à " + formatTime(dt.toLocalTime())
}
