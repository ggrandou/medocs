package net.grandou.rappels.data

import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Single entry point to modify the user database. Every multi-table operation is a transaction,
 * so that the stock (sum of movements) stays consistent with the occurrence journal.
 *
 * [now] provides the current instant (epoch ms); injectable for tests.
 */
class Repository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val items = db.items()
    private val schedules = db.schedules()
    private val occurrences = db.occurrences()
    private val stock = db.stock()

    // --- Items ---

    fun itemsWithStock() = items.observeWithStock()
    fun archivedItems() = items.observeArchived()
    suspend fun item(id: Long) = items.byId(id)

    suspend fun createItem(e: Item): Long = items.insert(e)
    suspend fun updateItem(e: Item) = items.update(e)

    suspend fun archiveItem(id: Long, archived: Boolean = true) {
        items.byId(id)?.let { items.update(it.copy(archived = archived)) }
    }

    /** Also erases schedules, history and stock (cascade). */
    suspend fun deleteItem(e: Item) = items.delete(e)

    // --- Schedules ---

    fun schedulesOfItem(itemId: Long) = schedules.observeForItem(itemId)
    fun observeSchedules() = schedules.observeAllWithTimes()
    suspend fun activeSchedules() = schedules.allActive()

    /**
     * Creates or updates a schedule and its times ([schedule].id == 0: creation). A time with an id is updated
     * (its history stays linked), a time without an id is created, a time missing from [times] is deleted (its
     * occurrences are detached, not erased). The engine must have written the elapsed occurrences before the call.
     */
    suspend fun saveSchedule(schedule: Schedule, times: List<ScheduleTime>): Long = db.withTransaction {
        val id = if (schedule.id == 0L) schedules.insert(schedule) else schedule.id.also { schedules.update(schedule) }
        val kept = times.mapNotNull { it.id.takeIf { i -> i != 0L } }.toSet()
        schedules.timesOfSchedule(id).filter { it.id !in kept }.forEach { schedules.deleteTime(it.id) }
        times.forEach {
            if (it.id == 0L) schedules.insertTime(it.copy(scheduleId = id))
            else schedules.updateTime(it.copy(scheduleId = id))
        }
        id
    }

    suspend fun deleteSchedule(p: Schedule) = schedules.delete(p)

    // --- Occurrences ---

    fun occurrencesOfDay(date: LocalDate) = occurrences.observeScheduledOn(date)
    /** Journal rows of the planned occurrences of a day range, read once (alarm engine). */
    suspend fun scheduledRows(rangeStart: LocalDate, rangeEnd: LocalDate) = occurrences.scheduledBetween(rangeStart, rangeEnd)
    fun occurrencesBetween(rangeStart: LocalDate, rangeEnd: LocalDate) = occurrences.observeBetween(rangeStart, rangeEnd)
    fun journal(itemId: Long) = occurrences.observeJournal(itemId)

    /**
     * Records the answer to a planned occurrence (creates it or updates the existing row) and aligns the stock:
     * only `DONE` consumes; `SNOOZED`, `SKIPPED` and `MISSED` give back any previous consumption.
     * [quantity] defaults to the schedule time's.
     */
    suspend fun respond(
        scheduleTimeId: Long,
        scheduledDate: LocalDate,
        status: Status,
        quantity: Double? = null,
        snoozedUntil: Long? = null,
    ): Long = db.withTransaction {
        val time = schedules.timeById(scheduleTimeId) ?: error("Unknown schedule time: $scheduleTimeId")
        val itemId = schedules.byId(time.scheduleId)!!.schedule.itemId
        val existing = occurrences.findScheduled(scheduleTimeId, scheduledDate)
        val occ = Occurrence(
            id = existing?.id ?: 0,
            itemId = itemId,
            scheduleTimeId = scheduleTimeId,
            scheduledDate = scheduledDate,
            scheduledTime = existing?.scheduledTime ?: time.time,
            status = status,
            quantity = quantity ?: time.quantity,
            timestamp = now(),
            snoozedUntil = snoozedUntil.takeIf { status == Status.SNOOZED },
        )
        val id = if (existing != null) occurrences.update(occ).let { occ.id } else occurrences.insert(occ)
        align(id, occ.copy(id = id))
        id
    }

    /**
     * Writes "missed" for a planned occurrence of a past day, unless the user answered in the meantime
     * (a missing row or a postponed one is the only state it replaces). Returns whether it was written.
     */
    suspend fun markMissed(scheduleTimeId: Long, scheduledDate: LocalDate): Boolean = db.withTransaction {
        val existing = occurrences.findScheduled(scheduleTimeId, scheduledDate)
        if (existing != null && existing.status != Status.SNOOZED) return@withTransaction false
        if (schedules.timeById(scheduleTimeId) == null) return@withTransaction false
        respond(scheduleTimeId, scheduledDate, Status.MISSED)
        true
    }

    fun oneOffsBetween(fromMs: Long, toMs: Long) = occurrences.observeOneOffs(fromMs, toMs)

    /** One-off occurrence, outside any schedule (an "extra" intake), taken at [at] (epoch ms, default now). */
    suspend fun recordOneOff(itemId: Long, quantity: Double?, status: Status = Status.DONE, at: Long? = null): Long =
        db.withTransaction {
            val t = at ?: now()
            val occ = Occurrence(
                itemId = itemId, status = status, quantity = quantity, timestamp = t,
            )
            val id = occurrences.insert(occ)
            align(id, occ.copy(id = id))
            id
        }

    /**
     * Removes the `MISSED` rows of [today] and later: the engine only writes them for past days, so these come
     * from a clock that was set forward and then back. Returns how many were removed.
     */
    suspend fun clearFutureMissed(today: LocalDate): Int = occurrences.deleteMissedFrom(today)

    /** Cancels an occurrence: the row disappears and its stock movement with it (cascade). */
    suspend fun cancelOccurrence(id: Long) = occurrences.delete(id)

    /** Replaces the occurrence's `CONSUMPTION` movement by the one its current state justifies. */
    private suspend fun align(id: Long, occ: Occurrence) {
        stock.deleteForOccurrence(id)
        val q = occ.quantity
        if (occ.status == Status.DONE && q != null && q != 0.0 && items.byId(occ.itemId)?.trackStock == true) {
            stock.insert(
                StockMovement(
                    itemId = occ.itemId, delta = -q, type = MovementType.CONSUMPTION,
                    timestamp = occ.timestamp, occurrenceId = id,
                )
            )
        }
    }

    // --- Stock ---

    fun observeStock(itemId: Long) = stock.observeStockOf(itemId)
    suspend fun stockOf(itemId: Long) = stock.stockOf(itemId)

    suspend fun purchase(itemId: Long, unites: Double) {
        stock.insert(StockMovement(itemId = itemId, delta = unites, type = MovementType.PURCHASE, timestamp = now()))
    }

    /** Adds a lot (box, pack…) according to `unitsPerPackage`; no effect if the value is unknown. */
    suspend fun addPackage(itemId: Long, count: Int = 1) {
        val u = items.byId(itemId)?.unitsPerPackage ?: return
        purchase(itemId, u * count)
    }

    /** Sets the current stock to [target] through a correction movement (nothing if already equal). */
    suspend fun correctStock(itemId: Long, target: Double) = db.withTransaction {
        val delta = target - stock.stockOf(itemId)
        if (delta != 0.0) {
            stock.insert(StockMovement(itemId = itemId, delta = delta, type = MovementType.CORRECTION, timestamp = now()))
        }
    }

    // --- Backup ---

    /** Consistent snapshot of all the user's data (a single read transaction). Photos are added by the caller. */
    suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(items.all(), schedules.allSchedules(), schedules.allTimes(), occurrences.all(), stock.all())
    }

    /** Replaces ALL the data by the backup's, atomically (all or nothing). */
    suspend fun replaceAll(d: BackupData) = db.withTransaction {
        items.deleteAll() // cascade: schedules, times, occurrences, movements
        items.insertAll(d.items)
        schedules.insertSchedules(d.schedules)
        schedules.insertTimes(d.scheduleTimes)
        occurrences.insertAll(d.occurrences)
        stock.insertAll(d.stockMovements)
    }
}
