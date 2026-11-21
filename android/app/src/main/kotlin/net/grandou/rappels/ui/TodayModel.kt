package net.grandou.rappels.ui

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Occurrence
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.data.Status
import net.grandou.rappels.engine.PlannedOccurrence
import net.grandou.rappels.engine.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** "Postpone" delays the reminder by this much. */
const val SNOOZE_MINUTES = 30L

/** Where a planned occurrence of today stands. */
enum class EntryState {
    /** Planned later today. */
    UPCOMING,
    /** Time has come (or a postponement has ended): waiting for an answer. Stays so, shown as late, until the end of the day. */
    DUE,
    /** Postponed, the new reminder is still ahead. */
    SNOOZED,
    DONE,
    SKIPPED,
    /** Left without answer past its day: a row written by the engine, never derived for today. */
    MISSED,
}

/** One line of the "Aujourd'hui" screen: a planned occurrence, its item and the journal row if the user acted. */
data class TodayEntry(
    val planned: PlannedOccurrence,
    val item: Item,
    val row: Occurrence?,
    val state: EntryState,
) {
    val key: String get() = "${planned.scheduleTimeId}-${planned.date}"

    /** Can still be answered "done" or "skipped". */
    val actionable: Boolean get() = state != EntryState.DONE && state != EntryState.SKIPPED
}

/**
 * Today's planned occurrences of the given items, in time order, with their state at [now].
 * Pure function, tested on the JVM. [rows] are the journal rows of today's planned occurrences.
 */
fun buildToday(
    items: List<Item>,
    schedules: List<ScheduleWithTimes>,
    rows: List<Occurrence>,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TodayEntry> = buildDay(items, schedules, rows, now.toLocalDate(), now, zone)

/**
 * Planned occurrences of any [date], with their state at [now]. Same rules as today for the current day;
 * on a past day, whatever was not answered counts as missed (the alarm engine writes the row later, or never
 * for old days); on a future day, nothing is late. [rows] are the journal rows of that day.
 */
fun buildDay(
    items: List<Item>,
    schedules: List<ScheduleWithTimes>,
    rows: List<Occurrence>,
    date: LocalDate,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TodayEntry> {
    val byId = items.associateBy { it.id }
    val rowByKey = rows.filter { it.scheduleTimeId != null && it.scheduledDate != null }
        .associateBy { it.scheduleTimeId!! to it.scheduledDate!! }
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val past = date < now.toLocalDate()
    return RecurrenceEngine.occurrencesOn(schedules, date).mapNotNull { planned ->
        val item = byId[planned.itemId] ?: return@mapNotNull null
        val row = rowByKey[planned.scheduleTimeId to planned.date]
        val state = when (row?.status) {
            Status.DONE -> EntryState.DONE
            Status.SKIPPED -> EntryState.SKIPPED
            Status.MISSED -> EntryState.MISSED
            Status.SNOOZED -> when {
                past -> EntryState.MISSED
                (row.snoozedUntil ?: 0L) <= nowMillis -> EntryState.DUE
                else -> EntryState.SNOOZED
            }
            null -> when {
                past -> EntryState.MISSED
                planned.dateTime > now -> EntryState.UPCOMING
                else -> EntryState.DUE
            }
        }
        TodayEntry(planned, item, row, state)
    }
}
