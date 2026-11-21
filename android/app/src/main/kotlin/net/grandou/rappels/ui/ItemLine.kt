package net.grandou.rappels.ui

import net.grandou.rappels.data.Item
import net.grandou.rappels.data.ItemWithStock
import net.grandou.rappels.data.ScheduleWithTimes
import net.grandou.rappels.engine.Coverage
import net.grandou.rappels.engine.PlannedOccurrence
import net.grandou.rappels.engine.RecurrenceEngine
import net.grandou.rappels.engine.StockCalculator
import net.grandou.rappels.engine.StockLevel
import java.time.LocalDateTime

/** Everything a list row or the detail screen shows about an item, computed once from the database. */
data class ItemLine(
    val item: Item,
    val stock: Double,
    val level: StockLevel,
    val coverage: Coverage?,
    val schedules: List<ScheduleWithTimes>,
    /** Next planned occurrence strictly after "now", all schedules together; null if none. */
    val next: PlannedOccurrence?,
) {
    /** Low or out of stock: shown first, flagged in amber. */
    val needsAttention: Boolean get() = level == StockLevel.LOW || level == StockLevel.OUT
}

fun buildLine(row: ItemWithStock, allSchedules: List<ScheduleWithTimes>, now: LocalDateTime): ItemLine {
    val mine = allSchedules.filter { it.schedule.itemId == row.item.id }.sortedBy { it.schedule.id }
    val cov = if (row.item.trackStock) StockCalculator.coverage(row.stock, mine, now) else null
    return ItemLine(
        item = row.item,
        stock = row.stock,
        level = StockCalculator.level(row.item, row.stock, mine, now),
        coverage = cov,
        schedules = mine,
        next = RecurrenceEngine.next(mine, now),
    )
}
