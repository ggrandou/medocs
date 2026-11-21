package net.grandou.rappels.engine

import net.grandou.rappels.data.IntervalUnit
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleWithTimes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** An occurrence planned by a schedule (not stored: computed). Local "wall-clock" date and time. */
data class PlannedOccurrence(
    val itemId: Long,
    val scheduleId: Long,
    val scheduleTimeId: Long,
    val date: LocalDate,
    val time: LocalTime,
    val quantity: Double?,
) {
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, time)
}

/**
 * Recurrence computations: pure functions, no Android dependency. Rules in docs/schema.md.
 * Everything is in local "wall-clock" time ([LocalDate], [LocalTime]).
 */
object RecurrenceEngine {
    /** Default search horizon for [nextDate] / [next]: enough for a yearly rule with a step of several years. */
    const val DEFAULT_HORIZON_DAYS = 366 * 10

    /** True if [schedule] plans a day on [date] (before looking at its times). */
    fun occursOn(schedule: Schedule, date: LocalDate): Boolean {
        if (!schedule.active) return false
        if (date < schedule.startDate) return false
        if (schedule.endDate != null && date > schedule.endDate) return false
        val step = schedule.interval.coerceAtLeast(1)
        return when (schedule.recurrence) {
            Recurrence.PERIODIC -> periodic(schedule, date, step)
            Recurrence.WEEKDAYS -> weekdays(schedule, date, step)
            Recurrence.RANK_IN_MONTH -> rankInMonth(schedule, date, step)
            Recurrence.CYCLE -> cycle(schedule, date)
        }
    }

    /** Days planned by [schedule] between [from] and [to], both included, in ascending order. */
    fun datesBetween(schedule: Schedule, from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = maxOf(from, schedule.startDate)
        val last = schedule.endDate?.let { minOf(to, it) } ?: to
        while (d <= last) {
            if (occursOn(schedule, d)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    /** First day planned on or after [from], or null if none within [horizonDays] (or before the end date). */
    fun nextDate(schedule: Schedule, from: LocalDate, horizonDays: Int = DEFAULT_HORIZON_DAYS): LocalDate? =
        datesBetween(schedule, from, from.plusDays(horizonDays.toLong())).firstOrNull()

    /** Occurrences (day × time) of several schedules for the days from [from] to [to], sorted by date then time. */
    fun occurrencesBetween(schedules: List<ScheduleWithTimes>, from: LocalDate, to: LocalDate): List<PlannedOccurrence> =
        schedules.flatMap { sw ->
            datesBetween(sw.schedule, from, to).flatMap { date ->
                sw.times.map { t ->
                    PlannedOccurrence(sw.schedule.itemId, sw.schedule.id, t.id, date, t.time, t.quantity)
                }
            }
        }.sortedWith(compareBy({ it.date }, { it.time }, { it.scheduleTimeId }))

    fun occurrencesOn(schedules: List<ScheduleWithTimes>, date: LocalDate): List<PlannedOccurrence> =
        occurrencesBetween(schedules, date, date)

    /**
     * First occurrence strictly after [after], or null if none within [horizonDays].
     * Occurrences for which [exclude] is true (already answered) are skipped.
     */
    fun next(
        schedules: List<ScheduleWithTimes>,
        after: LocalDateTime,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
        exclude: (PlannedOccurrence) -> Boolean = { false },
    ): PlannedOccurrence? {
        // Day by day over the window, not per schedule: stops at the first day that has a candidate.
        var date = after.toLocalDate()
        val last = date.plusDays(horizonDays.toLong())
        while (date <= last) {
            val found = occurrencesOn(schedules, date).firstOrNull { it.dateTime > after && !exclude(it) }
            if (found != null) return found
            date = date.plusDays(1)
        }
        return null
    }

    // --- rules ---

    private fun periodic(s: Schedule, date: LocalDate, step: Int): Boolean = when (s.intervalUnit) {
        IntervalUnit.DAY -> ChronoUnit.DAYS.between(s.startDate, date) % step == 0L
        IntervalUnit.WEEK -> ChronoUnit.DAYS.between(s.startDate, date) % (7L * step) == 0L
        IntervalUnit.MONTH -> monthlyMatch(s.startDate, date, step)
        IntervalUnit.YEAR -> monthlyMatch(s.startDate, date, 12 * step)
    }

    /** Every [stepMonths] months; the day of the month is brought back to the last day of a shorter month. */
    private fun monthlyMatch(start: LocalDate, date: LocalDate, stepMonths: Int): Boolean {
        val months = monthsBetween(start, date)
        if (months < 0 || months % stepMonths != 0L) return false
        return date.dayOfMonth == minOf(start.dayOfMonth, YearMonth.from(date).lengthOfMonth())
    }

    private fun monthsBetween(a: LocalDate, b: LocalDate): Long =
        (b.year - a.year) * 12L + (b.monthValue - a.monthValue)

    private fun weekdays(s: Schedule, date: LocalDate, step: Int): Boolean {
        if (s.weekdays and (1 shl (date.dayOfWeek.value - 1)) == 0) return false
        val weeks = ChronoUnit.WEEKS.between(mondayOf(s.startDate), mondayOf(date))
        return weeks % step == 0L
    }

    private fun mondayOf(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)

    private fun rankInMonth(s: Schedule, date: LocalDate, step: Int): Boolean {
        val bit = Integer.numberOfTrailingZeros(s.weekdays)
        if (s.weekdays == 0 || bit > 6) return false
        if (date.dayOfWeek.value - 1 != bit) return false
        if (monthsBetween(s.startDate, date) % step != 0L) return false
        return when (val rank = s.rank) {
            null -> false
            -1 -> date.plusDays(7).month != date.month
            else -> (date.dayOfMonth - 1) / 7 + 1 == rank
        }
    }

    private fun cycle(s: Schedule, date: LocalDate): Boolean {
        val active = s.activeDays ?: return false
        val pause = s.pauseDays ?: 0
        if (active <= 0 || pause < 0) return false
        return ChronoUnit.DAYS.between(s.startDate, date) % (active + pause) < active
    }
}
