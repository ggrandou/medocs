package net.grandou.rappels.engine

import net.grandou.rappels.data.IntervalUnit
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import net.grandou.rappels.data.ScheduleWithTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceEngineTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun sched(
        start: String,
        recurrence: Recurrence = Recurrence.PERIODIC,
        interval: Int = 1,
        unit: IntervalUnit = IntervalUnit.DAY,
        weekdays: Int = 0,
        rank: Int? = null,
        active: Int? = null,
        pause: Int? = null,
        end: String? = null,
        isActive: Boolean = true,
    ) = Schedule(
        id = 1, itemId = 1, recurrence = recurrence, interval = interval, intervalUnit = unit,
        weekdays = weekdays, rank = rank, activeDays = active, pauseDays = pause,
        startDate = d(start), endDate = end?.let(::d), active = isActive,
    )

    private fun dates(s: Schedule, from: String, to: String) =
        RecurrenceEngine.datesBetween(s, d(from), d(to)).map { it.toString() }

    // Monday = bit 0 … Sunday = bit 6
    private val mon = 1
    private val wed = 1 shl 2
    private val fri = 1 shl 4
    private val sat = 1 shl 5

    @Test fun everyDay() {
        assertEquals(listOf("2026-03-01", "2026-03-02", "2026-03-03"), dates(sched("2026-03-01"), "2026-02-20", "2026-03-03"))
    }

    @Test fun everyThreeDays() {
        assertEquals(listOf("2026-03-01", "2026-03-04", "2026-03-07"), dates(sched("2026-03-01", interval = 3), "2026-03-01", "2026-03-08"))
    }

    @Test fun everyTwoWeeks() {
        val s = sched("2026-03-02", interval = 2, unit = IntervalUnit.WEEK)
        assertEquals(listOf("2026-03-02", "2026-03-16", "2026-03-30"), dates(s, "2026-03-01", "2026-03-31"))
    }

    @Test fun monthlyClampsToLastDay() {
        val s = sched("2026-01-31", unit = IntervalUnit.MONTH)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            dates(s, "2026-01-01", "2026-04-30"),
        )
    }

    @Test fun monthlyLeapYearFebruary() {
        val s = sched("2027-12-31", unit = IntervalUnit.MONTH)
        assertEquals(listOf("2028-01-31", "2028-02-29"), dates(s, "2028-01-01", "2028-02-29"))
    }

    @Test fun everyThreeMonths() {
        val s = sched("2026-01-15", interval = 3, unit = IntervalUnit.MONTH)
        assertEquals(listOf("2026-01-15", "2026-04-15", "2026-07-15"), dates(s, "2026-01-01", "2026-08-01"))
    }

    @Test fun yearlyFeb29() {
        val s = sched("2024-02-29", unit = IntervalUnit.YEAR)
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), dates(s, "2024-01-01", "2028-12-31"))
    }

    @Test fun weekdaysEveryWeek() {
        val s = sched("2026-03-02", Recurrence.WEEKDAYS, weekdays = mon or wed or fri)
        assertEquals(
            listOf("2026-03-02", "2026-03-04", "2026-03-06", "2026-03-09"),
            dates(s, "2026-03-01", "2026-03-09"),
        )
    }

    @Test fun weekdaysNotBeforeStartDate() {
        // Start on Wednesday: the Monday of the same week is before the start.
        val s = sched("2026-03-04", Recurrence.WEEKDAYS, weekdays = mon or wed)
        assertEquals(listOf("2026-03-04", "2026-03-09"), dates(s, "2026-03-01", "2026-03-09"))
    }

    @Test fun weekdaysEveryOtherWeek() {
        val s = sched("2026-03-07", Recurrence.WEEKDAYS, interval = 2, weekdays = sat) // Saturday
        assertEquals(listOf("2026-03-07", "2026-03-21", "2026-04-04"), dates(s, "2026-03-01", "2026-04-05"))
    }

    @Test fun weekdaysAnchorWeekStartsOnMonday() {
        // Start on Sunday 8 March: its week began Monday 2 March, so matching weeks are those of
        // 2, 16 and 30 March. Monday 9 and Sunday 15 are in a skipped week.
        val s = sched("2026-03-08", Recurrence.WEEKDAYS, interval = 2, weekdays = (1 shl 6) or mon)
        assertEquals(
            listOf("2026-03-08", "2026-03-16", "2026-03-22", "2026-03-30"),
            dates(s, "2026-03-01", "2026-03-31"),
        )
    }

    @Test fun weekdaysEmptyMaskNeverOccurs() {
        assertTrue(dates(sched("2026-03-01", Recurrence.WEEKDAYS), "2026-03-01", "2026-04-01").isEmpty())
    }

    @Test fun firstMondayOfMonth() {
        val s = sched("2026-03-01", Recurrence.RANK_IN_MONTH, weekdays = mon, rank = 1)
        assertEquals(listOf("2026-03-02", "2026-04-06", "2026-05-04"), dates(s, "2026-03-01", "2026-05-31"))
    }

    @Test fun lastFridayEveryTwoMonths() {
        val s = sched("2026-01-01", Recurrence.RANK_IN_MONTH, interval = 2, weekdays = fri, rank = -1)
        assertEquals(listOf("2026-01-30", "2026-03-27", "2026-05-29"), dates(s, "2026-01-01", "2026-06-30"))
    }

    @Test fun lastWeekdayWhenMonthEndsOnIt() {
        // 28 Feb 2026 is a Saturday and the last one; 31 Jan 2026 is a Saturday too.
        val s = sched("2026-01-01", Recurrence.RANK_IN_MONTH, weekdays = sat, rank = -1)
        assertEquals(listOf("2026-01-31", "2026-02-28"), dates(s, "2026-01-01", "2026-02-28"))
    }

    @Test fun fifthWeekdaySkipsMonthsWithoutIt() {
        // Mondays in March 2026: 2, 9, 16, 23, 30 (fifth = 30); April 2026 has only four.
        val s = sched("2026-03-01", Recurrence.RANK_IN_MONTH, weekdays = mon, rank = 5)
        assertEquals(listOf("2026-03-30"), dates(s, "2026-03-01", "2026-04-30"))
    }

    @Test fun cycle21On7Off() {
        val s = sched("2026-03-01", Recurrence.CYCLE, active = 21, pause = 7)
        val all = dates(s, "2026-03-01", "2026-04-30")
        // 61 days = two full 28-day cycles (21 active each) + 5 days of the third
        assertEquals(47, all.size)
        assertTrue("2026-03-21" in all) // day 21 of the active phase
        assertFalse("2026-03-22" in all) // pause starts
        assertFalse("2026-03-28" in all)
        assertTrue("2026-03-29" in all) // new cycle
    }

    @Test fun cycleWithoutPause() {
        val s = sched("2026-03-01", Recurrence.CYCLE, active = 3, pause = null)
        assertEquals(10, dates(s, "2026-03-01", "2026-03-10").size)
    }

    @Test fun cycleIncomplete() {
        assertTrue(dates(sched("2026-03-01", Recurrence.CYCLE), "2026-03-01", "2026-03-10").isEmpty())
    }

    @Test fun endDateIncluded() {
        val s = sched("2026-03-01", end = "2026-03-03")
        assertEquals(listOf("2026-03-01", "2026-03-02", "2026-03-03"), dates(s, "2026-03-01", "2026-03-10"))
    }

    @Test fun oneTimeReminder() {
        val s = sched("2026-03-05", end = "2026-03-05")
        assertEquals(listOf("2026-03-05"), dates(s, "2026-01-01", "2026-12-31"))
    }

    @Test fun suspendedScheduleNeverOccurs() {
        assertTrue(dates(sched("2026-03-01", isActive = false), "2026-03-01", "2026-03-10").isEmpty())
    }

    @Test fun nextDateAndNone() {
        val s = sched("2026-03-01", interval = 10, end = "2026-03-21")
        assertEquals(d("2026-03-11"), RecurrenceEngine.nextDate(s, d("2026-03-02")))
        assertNull(RecurrenceEngine.nextDate(s, d("2026-03-22")))
    }

    // --- times and next occurrence ---

    private fun withTimes(s: Schedule, vararg times: Pair<String, Double?>) = ScheduleWithTimes(
        s, times.mapIndexed { i, (t, q) -> ScheduleTime(id = (i + 1).toLong(), scheduleId = s.id, time = LocalTime.parse(t), quantity = q) },
    )

    @Test fun occurrencesAreSortedByTime() {
        val sw = withTimes(sched("2026-03-01"), "20:00" to 1.0, "08:00" to 2.0)
        val occ = RecurrenceEngine.occurrencesOn(listOf(sw), d("2026-03-05"))
        assertEquals(listOf("08:00", "20:00"), occ.map { it.time.toString() })
        assertEquals(listOf(2.0, 1.0), occ.map { it.quantity })
    }

    @Test fun nextIsStrictlyAfter() {
        val sw = withTimes(sched("2026-03-01"), "08:00" to 1.0, "20:00" to 1.0)
        val at8 = LocalDateTime.parse("2026-03-05T08:00")
        assertEquals(LocalDateTime.parse("2026-03-05T20:00"), RecurrenceEngine.next(listOf(sw), at8)?.dateTime)
        val late = LocalDateTime.parse("2026-03-05T21:00")
        assertEquals(LocalDateTime.parse("2026-03-06T08:00"), RecurrenceEngine.next(listOf(sw), late)?.dateTime)
    }

    @Test fun nextAcrossSchedulesAndMonths() {
        val a = withTimes(sched("2026-01-31", unit = IntervalUnit.MONTH), "09:00" to null)
        val b = withTimes(sched("2026-03-01", interval = 7).copy(id = 2), "07:30" to 1.0)
        val n = RecurrenceEngine.next(listOf(a, b), LocalDateTime.parse("2026-03-02T00:00"))
        assertEquals(LocalDateTime.parse("2026-03-08T07:30"), n?.dateTime)
        assertEquals(2L, n?.scheduleId)
    }

    @Test fun nextIsNullWhenNothingLeft() {
        val sw = withTimes(sched("2026-03-01", end = "2026-03-02"), "08:00" to 1.0)
        assertNull(RecurrenceEngine.next(listOf(sw), LocalDateTime.parse("2026-03-02T09:00")))
    }

    @Test fun nextSkipsExcludedOccurrences() {
        val sw = withTimes(sched("2026-03-01"), "08:00" to 1.0, "20:00" to 1.0)
        val from = LocalDateTime.parse("2026-03-05T07:00")
        val answered = { o: PlannedOccurrence -> o.date == d("2026-03-05") && o.time == LocalTime.of(8, 0) }
        assertEquals(LocalDateTime.parse("2026-03-05T20:00"), RecurrenceEngine.next(listOf(sw), from, exclude = answered)?.dateTime)
    }
}
