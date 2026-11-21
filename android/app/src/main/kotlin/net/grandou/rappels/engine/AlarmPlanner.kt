package net.grandou.rappels.engine

import net.grandou.rappels.data.Occurrence
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.data.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What the alarm engine has to decide, as pure functions (no Android dependency, tested on the JVM).
 * The Android side (`reminders/ReminderEngine`) only reads the database, calls these and acts.
 */
object AlarmPlanner {
    /**
     * Planned occurrences of the days from [from] to the day before [today] that never got a final answer
     * (no journal row, or still postponed): they become "missed" the day after. [schedules] are the active ones.
     */
    fun toMarkMissed(
        schedules: List<ScheduleWithTimes>,
        rows: List<Occurrence>,
        from: LocalDate,
        today: LocalDate,
    ): List<PlannedOccurrence> {
        if (from >= today) return emptyList()
        val status = rows.filter { it.scheduleTimeId != null && it.scheduledDate != null }
            .associate { (it.scheduleTimeId!! to it.scheduledDate!!) to it.status }
        return RecurrenceEngine.occurrencesBetween(schedules, from, today.minusDays(1)).filter {
            val s = status[it.scheduleTimeId to it.date]
            s == null || s == Status.SNOOZED
        }
    }

    /**
     * Instant (epoch ms) of the next time the engine has to run after [now]: the earliest of the next unanswered
     * occurrence of today, the end of a postponement, and midnight (day change: unanswered occurrences become
     * missed). [todayRows] are the journal rows of today's planned occurrences.
     */
    fun nextWake(
        schedules: List<ScheduleWithTimes>,
        todayRows: List<Occurrence>,
        now: LocalDateTime,
        zone: ZoneId,
    ): Long {
        val today = now.toLocalDate()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rowByKey = todayRows.filter { it.scheduleTimeId != null && it.scheduledDate != null }
            .associateBy { it.scheduleTimeId!! to it.scheduledDate!! }

        val candidates = ArrayList<Long>()
        candidates += midnight
        RecurrenceEngine.occurrencesOn(schedules, today)
            .filter { it.dateTime > now && (it.scheduleTimeId to it.date) !in rowByKey }
            .forEach { candidates += it.dateTime.atZone(zone).toInstant().toEpochMilli() }
        todayRows.filter { it.status == Status.SNOOZED }
            .mapNotNull { it.snoozedUntil }
            .filter { it > nowMs }
            .forEach { candidates += it }
        return candidates.filter { it <= midnight }.min()
    }
}
