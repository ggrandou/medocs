package net.grandou.rappels.engine

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import net.grandou.rappels.data.ScheduleWithTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class StockCalculatorTest {
    private fun at(s: String) = LocalDateTime.parse(s)
    private val now = at("2026-03-01T07:00")

    private fun item(trackStock: Boolean = true, thresholdDays: Int = 7, thresholdUnits: Double? = null) =
        Item(id = 1, name = "Test", trackStock = trackStock, thresholdDays = thresholdDays, thresholdUnits = thresholdUnits)

    /** Daily schedule from 1 March 2026 with the given (time, quantity) doses. */
    private fun daily(vararg doses: Pair<String, Double?>, start: String = "2026-03-01", itemId: Long = 1) =
        ScheduleWithTimes(
            Schedule(id = itemId, itemId = itemId, recurrence = Recurrence.PERIODIC, startDate = LocalDate.parse(start)),
            doses.mapIndexed { i, (t, q) -> ScheduleTime(id = itemId * 10 + i, scheduleId = itemId, time = LocalTime.parse(t), quantity = q) },
        )

    @Test fun consumptionDelta() {
        assertEquals(-2.0, StockCalculator.consumptionDelta(item(), 2.0)!!, 0.0)
        assertEquals(-0.5, StockCalculator.consumptionDelta(item(), 0.5)!!, 0.0)
        assertNull(StockCalculator.consumptionDelta(item(), null))
        assertNull(StockCalculator.consumptionDelta(item(), 0.0))
        assertNull(StockCalculator.consumptionDelta(item(trackStock = false), 1.0))
    }

    @Test fun coverageOneDosePerDay() {
        // 10 units, 1 per day from today 08:00: days 1–10 March covered, 11 March is the first missing.
        val c = StockCalculator.coverage(10.0, listOf(daily("08:00" to 1.0)), now)!!
        assertEquals(LocalDate.parse("2026-03-11"), c.runOutDate)
        assertEquals(10, c.days)
    }

    @Test fun coverageStartsAtNow() {
        // At 09:00 the 08:00 dose of the day is behind us: 10 units last until 10 March included.
        val c = StockCalculator.coverage(10.0, listOf(daily("08:00" to 1.0)), at("2026-03-01T09:00"))!!
        assertEquals(LocalDate.parse("2026-03-12"), c.runOutDate)
        assertEquals(11, c.days)
    }

    @Test fun coverageTwoDosesAndPartialDay() {
        // 2 doses of 1 per day, 5 units: 3 days = 6 units needed; the 2nd dose of day 3 is missing.
        val c = StockCalculator.coverage(5.0, listOf(daily("08:00" to 1.0, "20:00" to 1.0)), now)!!
        assertEquals(LocalDate.parse("2026-03-03"), c.runOutDate)
        assertEquals(2, c.days)
    }

    @Test fun coverageDecimalQuantities() {
        // 0.5 per dose: 1.5 units cover 3 doses (1–3 March), the 4th is missing.
        val c = StockCalculator.coverage(1.5, listOf(daily("08:00" to 0.5)), now)!!
        assertEquals(LocalDate.parse("2026-03-04"), c.runOutDate)
        assertEquals(3, c.days)
    }

    @Test fun coverageZeroStock() {
        val c = StockCalculator.coverage(0.0, listOf(daily("08:00" to 1.0)), now)!!
        assertEquals(0, c.days)
    }

    @Test fun coverageNullWithoutQuantityOrSchedule() {
        assertNull(StockCalculator.coverage(10.0, listOf(daily("08:00" to null)), now))
        assertNull(StockCalculator.coverage(10.0, emptyList(), now))
    }

    @Test fun coverageBeyondHorizon() {
        val c = StockCalculator.coverage(10_000.0, listOf(daily("08:00" to 1.0)), now)!!
        assertNull(c.runOutDate)
        assertNull(c.days)
    }

    @Test fun levelNotTracked() {
        assertEquals(StockLevel.NOT_TRACKED, StockCalculator.level(item(trackStock = false), 0.0, emptyList(), now))
    }

    @Test fun levelOkLowOut() {
        val s = listOf(daily("08:00" to 1.0))
        assertEquals(StockLevel.OK, StockCalculator.level(item(), 30.0, s, now))
        assertEquals(StockLevel.LOW, StockCalculator.level(item(), 7.0, s, now)) // covers exactly 7 days
        assertEquals(StockLevel.OK, StockCalculator.level(item(), 8.0, s, now))
        assertEquals(StockLevel.LOW, StockCalculator.level(item(), 3.0, s, now))
        assertEquals(StockLevel.OUT, StockCalculator.level(item(), 0.0, s, now))
    }

    @Test fun levelOutWhenNotEnoughForNextDose() {
        val s = listOf(daily("08:00" to 2.0))
        assertEquals(StockLevel.OUT, StockCalculator.level(item(), 1.0, s, now))
    }

    @Test fun levelWithoutScheduleUsesThresholdUnits() {
        assertEquals(StockLevel.LOW, StockCalculator.level(item(thresholdUnits = 5.0), 4.0, emptyList(), now))
        assertEquals(StockLevel.OK, StockCalculator.level(item(thresholdUnits = 5.0), 5.0, emptyList(), now))
        assertEquals(StockLevel.OK, StockCalculator.level(item(), 4.0, emptyList(), now))
        assertEquals(StockLevel.OUT, StockCalculator.level(item(thresholdUnits = 5.0), 0.0, emptyList(), now))
    }

    @Test fun levelIgnoresOtherItemsSchedules() {
        val other = daily("08:00" to 5.0, itemId = 2)
        assertEquals(StockLevel.OK, StockCalculator.level(item(), 3.0, listOf(other), now))
    }

    @Test fun excludedOccurrencesAreNotConsumedAgain() {
        // Start of day; the 08:00 dose is answered (its movement is already in the stock of 9 units).
        // Remaining doses: 20:00 on day 1, then 2 per day: 9 units last until 5 March evening included.
        val s = listOf(daily("08:00" to 1.0, "20:00" to 1.0))
        val startOfDay = at("2026-03-01T00:00")
        val answered = { o: PlannedOccurrence -> o.date == LocalDate.parse("2026-03-01") && o.time == LocalTime.of(8, 0) }
        assertEquals(LocalDate.parse("2026-03-06"), StockCalculator.coverage(9.0, s, startOfDay, exclude = answered)!!.runOutDate)
        // Without the exclusion the answered dose would be counted a second time.
        assertEquals(LocalDate.parse("2026-03-05"), StockCalculator.coverage(9.0, s, startOfDay)!!.runOutDate)
    }

    @Test fun pendingEarlierDoseOfTodayIsCounted() {
        // 08:00 dose not answered yet at 09:00: counted, because the day starts at midnight.
        val s = listOf(daily("08:00" to 1.0))
        val c = StockCalculator.coverage(2.0, s, at("2026-03-01T00:00"))!!
        assertEquals(LocalDate.parse("2026-03-03"), c.runOutDate)
    }
}
