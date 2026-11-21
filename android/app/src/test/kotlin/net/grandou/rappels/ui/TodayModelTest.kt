package net.grandou.rappels.ui

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Occurrence
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.data.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class TodayModelTest {
    private val date = LocalDate.parse("2026-03-10")
    private fun at(t: String) = LocalDateTime.of(date, LocalTime.parse(t))
    private val item = Item(id = 1, name = "Test")
    private val schedule = ScheduleWithTimes(
        Schedule(id = 1, itemId = 1, recurrence = Recurrence.PERIODIC, startDate = LocalDate.parse("2026-03-01")),
        listOf(
            ScheduleTime(id = 10, scheduleId = 1, time = LocalTime.of(8, 0), quantity = 1.0),
            ScheduleTime(id = 11, scheduleId = 1, time = LocalTime.of(20, 0), quantity = 1.0),
        ),
    )

    private fun row(timeId: Long, status: Status, snoozedUntil: Long? = null) = Occurrence(
        itemId = 1, scheduleTimeId = timeId, scheduledDate = date, scheduledTime = LocalTime.of(8, 0),
        status = status, timestamp = 0, snoozedUntil = snoozedUntil,
    )

    private fun states(now: String, vararg rows: Occurrence) =
        buildToday(listOf(item), listOf(schedule), rows.toList(), at(now), ZoneOffset.UTC).map { it.state }

    @Test fun upcomingThenDueAllDay() {
        assertEquals(listOf(EntryState.UPCOMING, EntryState.UPCOMING), states("07:00"))
        assertEquals(listOf(EntryState.DUE, EntryState.UPCOMING), states("08:00"))
        // A late occurrence stays due until the end of the day: it is not "missed" before the next day.
        assertEquals(listOf(EntryState.DUE, EntryState.UPCOMING), states("19:59"))
        assertEquals(listOf(EntryState.DUE, EntryState.DUE), states("23:59"))
    }

    @Test fun answersFromTheJournal() {
        assertEquals(listOf(EntryState.DONE, EntryState.UPCOMING), states("07:00", row(10, Status.DONE)))
        assertEquals(listOf(EntryState.SKIPPED, EntryState.DUE), states("20:30", row(10, Status.SKIPPED)))
        assertEquals(listOf(EntryState.MISSED, EntryState.UPCOMING), states("09:00", row(10, Status.MISSED)))
    }

    @Test fun snoozeEndsAtItsInstant() {
        val until = at("08:30").toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(EntryState.SNOOZED, states("08:10", row(10, Status.SNOOZED, until))[0])
        assertEquals(EntryState.DUE, states("08:30", row(10, Status.SNOOZED, until))[0])
    }

    @Test fun orderedByTimeAndLinkedToItem() {
        val list = buildToday(listOf(item), listOf(schedule), emptyList(), at("07:00"), ZoneOffset.UTC)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), list.map { it.planned.time })
        assertTrue(list.all { it.item == item && it.row == null })
    }

    @Test fun unknownOrArchivedItemIsIgnored() {
        assertTrue(buildToday(emptyList(), listOf(schedule), emptyList(), at("07:00"), ZoneOffset.UTC).isEmpty())
    }
}
