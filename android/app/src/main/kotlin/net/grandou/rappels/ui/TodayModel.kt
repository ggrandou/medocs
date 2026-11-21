package net.grandou.rappels.ui

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Occurrence
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.data.Status
import net.grandou.rappels.engine.PlannedOccurrence
import net.grandou.rappels.engine.RecurrenceEngine
import java.time.LocalDateTime
import java.time.ZoneId

/** Without an answer after this delay, an occurrence is "missed". */
const val MISSED_AFTER_MINUTES = 120L

/** "Postpone" delays the reminder by this much. */
const val SNOOZE_MINUTES = 30L

/** Where a planned occurrence of today stands. */
enum class EntryState {
    /** Planned later today. */
    UPCOMING,
    /** Time has come (or a postponement has ended): waiting for an answer. */
    DUE,
    /** Postponed, the new reminder is still ahead. */
    SNOOZED,
    DONE,
    SKIPPED,
    /** No answer long after the time (row written by the engine, or just derived from the clock). */
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
): List<TodayEntry> {
    val byId = items.associateBy { it.id }
    val rowByKey = rows.filter { it.scheduleTimeId != null && it.scheduledDate != null }
        .associateBy { it.scheduleTimeId!! to it.scheduledDate!! }
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    return RecurrenceEngine.occurrencesOn(schedules, now.toLocalDate()).mapNotNull { planned ->
        val item = byId[planned.itemId] ?: return@mapNotNull null
        val row = rowByKey[planned.scheduleTimeId to planned.date]
        val late = now >= planned.dateTime.plusMinutes(MISSED_AFTER_MINUTES)
        val state = when (row?.status) {
            Status.DONE -> EntryState.DONE
            Status.SKIPPED -> EntryState.SKIPPED
            Status.MISSED -> EntryState.MISSED
            Status.SNOOZED -> if ((row.snoozedUntil ?: 0L) <= nowMillis) EntryState.DUE else EntryState.SNOOZED
            null -> when {
                planned.dateTime > now -> EntryState.UPCOMING
                late -> EntryState.MISSED
                else -> EntryState.DUE
            }
        }
        TodayEntry(planned, item, row, state)
    }
}
