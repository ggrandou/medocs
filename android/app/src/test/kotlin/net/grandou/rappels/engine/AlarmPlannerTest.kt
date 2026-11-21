package net.grandou.rappels.engine

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

class AlarmPlannerTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-03-10")
    private val schedule = ScheduleWithTimes(
        Schedule(id = 1, itemId = 1, recurrence = Recurrence.PERIODIC, startDate = LocalDate.parse("2026-03-01")),
        listOf(
            ScheduleTime(id = 10, scheduleId = 1, time = LocalTime.of(8, 0), quantity = 1.0),
            ScheduleTime(id = 11, scheduleId = 1, time = LocalTime.of(20, 0), quantity = 1.0),
        ),
    )

    private fun at(t: String, d: LocalDate = today) = LocalDateTime.of(d, LocalTime.parse(t))
    private fun ms(t: String, d: LocalDate = today) = at(t, d).atZone(zone).toInstant().toEpochMilli()

    private fun row(timeId: Long, status: Status, date: LocalDate = today, snoozedUntil: Long? = null) = Occurrence(
        itemId = 1, scheduleTimeId = timeId, scheduledDate = date, status = status, timestamp = 0, snoozedUntil = snoozedUntil,
    )

    // --- nextWake ---

    @Test fun wakesAtTheNextOccurrenceOfTheDay() {
        assertEquals(ms("08:00"), AlarmPlanner.nextWake(listOf(schedule), emptyList(), at("06:00"), zone))
        assertEquals(ms("20:00"), AlarmPlanner.nextWake(listOf(schedule), emptyList(), at("08:00"), zone))
    }

    @Test fun wakesAtMidnightWhenNothingIsLeftToday() {
        val midnight = ms("00:00", today.plusDays(1))
        assertEquals(midnight, AlarmPlanner.nextWake(listOf(schedule), emptyList(), at("20:00"), zone))
        // Without any schedule the day change still comes first: the caller decides not to arm anything
        assertEquals(midnight, AlarmPlanner.nextWake(emptyList(), emptyList(), at("12:00"), zone))
    }

    @Test fun skipsOccurrencesAlreadyAnswered() {
        val rows = listOf(row(10, Status.DONE))
        assertEquals(ms("20:00"), AlarmPlanner.nextWake(listOf(schedule), rows, at("07:00"), zone))
    }

    @Test fun wakesAtTheEndOfAPostponement() {
        val rows = listOf(row(10, Status.SNOOZED, snoozedUntil = ms("08:30")))
        assertEquals(ms("08:30"), AlarmPlanner.nextWake(listOf(schedule), rows, at("08:05"), zone))
        // Postponement over: it is due now, nothing to wait for but the evening
        assertEquals(ms("20:00"), AlarmPlanner.nextWake(listOf(schedule), rows, at("08:31"), zone))
    }

    @Test fun aPostponementCrossingMidnightDoesNotDelayTheDayChange() {
        val rows = listOf(row(11, Status.SNOOZED, snoozedUntil = ms("00:20", today.plusDays(1))))
        assertEquals(ms("00:00", today.plusDays(1)), AlarmPlanner.nextWake(listOf(schedule), rows, at("23:50"), zone))
    }

    // --- toMarkMissed ---

    @Test fun unansweredAndPostponedOfPastDaysAreMissed() {
        val yesterday = today.minusDays(1)
        val rows = listOf(
            row(10, Status.DONE, yesterday),
            row(11, Status.SNOOZED, yesterday, snoozedUntil = 1),
        )
        val missed = AlarmPlanner.toMarkMissed(listOf(schedule), rows, yesterday, today)
        assertEquals(listOf(11L), missed.map { it.scheduleTimeId })
    }

    @Test fun answeredOrAlreadyMissedAreLeftAlone() {
        val yesterday = today.minusDays(1)
        val rows = listOf(row(10, Status.SKIPPED, yesterday), row(11, Status.MISSED, yesterday))
        assertTrue(AlarmPlanner.toMarkMissed(listOf(schedule), rows, yesterday, today).isEmpty())
    }

    @Test fun nothingIsMissedForTodayNorBeforeTheWindow() {
        assertTrue(AlarmPlanner.toMarkMissed(listOf(schedule), emptyList(), today, today).isEmpty())
        // Several days away: every unanswered occurrence of every day of the window
        val missed = AlarmPlanner.toMarkMissed(listOf(schedule), emptyList(), today.minusDays(3), today)
        assertEquals(6, missed.size)
        assertEquals(today.minusDays(3), missed.first().date)
        assertEquals(today.minusDays(1), missed.last().date)
    }

    @Test fun oneOffRowsDoNotHideAPlannedOccurrence() {
        val yesterday = today.minusDays(1)
        val oneOff = Occurrence(itemId = 1, status = Status.DONE, timestamp = 0)
        assertEquals(2, AlarmPlanner.toMarkMissed(listOf(schedule), listOf(oneOff), yesterday, today).size)
    }
}
