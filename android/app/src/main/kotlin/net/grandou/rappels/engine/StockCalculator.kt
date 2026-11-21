package net.grandou.rappels.engine

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.ScheduleWithTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** How long the stock lasts. [days] and [runOutDate] are null when it lasts beyond the search horizon. */
data class Coverage(
    /** Day of the first planned occurrence that the stock can no longer cover. */
    val runOutDate: LocalDate?,
    /** Number of days from today until [runOutDate]: 0 = the next dose is already missing. */
    val days: Int?,
)

enum class StockLevel {
    /** The item does not track stock. */
    NOT_TRACKED,
    OK,
    /** Amber alert: running out soon. */
    LOW,
    /** Nothing left (or not enough for the next dose). */
    OUT,
}

/**
 * Stock computations: pure functions, no Android dependency. The stock itself is never stored:
 * it is the sum of the movements (`SUM(delta)`), passed here as a number. Rules in docs/schema.md.
 */
object StockCalculator {
    private const val EPSILON = 1e-9

    /** Movement to record when [quantity] is taken: null if the item does not track stock or there is no quantity. */
    fun consumptionDelta(item: Item, quantity: Double?): Double? =
        if (item.trackStock && quantity != null && quantity > 0) -quantity else null

    /**
     * Simulates the planned doses from [from] on and finds when [stock] no longer covers one.
     * Only occurrences at or after [from] are consumed: earlier ones already have their answer
     * (and movement) or are handled by the occurrence flow.
     * Returns null if no dose with a quantity is planned within [horizonDays] (nothing to simulate).
     */
    fun coverage(
        stock: Double,
        schedules: List<ScheduleWithTimes>,
        from: LocalDateTime,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): Coverage? {
        val today = from.toLocalDate()
        val doses = RecurrenceEngine
            .occurrencesBetween(schedules, today, today.plusDays(horizonDays.toLong()))
            .filter { it.dateTime >= from && (it.quantity ?: 0.0) > 0 }
        if (doses.isEmpty()) return null
        var remaining = stock
        for (dose in doses) {
            val q = dose.quantity!!
            if (remaining + EPSILON < q) {
                return Coverage(dose.date, ChronoUnit.DAYS.between(today, dose.date).toInt())
            }
            remaining -= q
        }
        return Coverage(null, null)
    }

    /**
     * Level of an item: out of stock, low (the stock covers [Item.thresholdDays] days or fewer of its
     * schedules), or OK. Without any planned dose, falls back on [Item.thresholdUnits].
     */
    fun level(
        item: Item,
        stock: Double,
        schedules: List<ScheduleWithTimes>,
        from: LocalDateTime,
    ): StockLevel {
        if (!item.trackStock) return StockLevel.NOT_TRACKED
        if (stock <= EPSILON) return StockLevel.OUT
        val cov = coverage(stock, schedules.filter { it.schedule.itemId == item.id }, from)
        if (cov == null) {
            val threshold = item.thresholdUnits
            return if (threshold != null && stock < threshold) StockLevel.LOW else StockLevel.OK
        }
        val days = cov.days ?: return StockLevel.OK
        return when {
            days == 0 -> StockLevel.OUT
            days <= item.thresholdDays -> StockLevel.LOW
            else -> StockLevel.OK
        }
    }

    const val DEFAULT_HORIZON_DAYS = 366
}
