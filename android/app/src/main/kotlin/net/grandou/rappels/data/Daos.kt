package net.grandou.rappels.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ItemDao {
    @Insert suspend fun insert(e: Item): Long
    @Update suspend fun update(e: Item)
    @Delete suspend fun delete(e: Item)

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun byId(id: Long): Item?

    @Query(
        """SELECT e.*, COALESCE((SELECT SUM(delta) FROM stock_movement WHERE item_id = e.id), 0) AS stock
           FROM item e WHERE archived = 0 ORDER BY name COLLATE NOCASE"""
    )
    fun observeWithStock(): Flow<List<ItemWithStock>>

    @Query("SELECT * FROM item WHERE archived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<Item>>

    // Backup: the cascade also empties schedules, occurrences and movements.
    @Query("SELECT * FROM item ORDER BY id") suspend fun all(): List<Item>
    @Query("DELETE FROM item") suspend fun deleteAll()
    @Insert suspend fun insertAll(l: List<Item>)
}

@Dao
interface ScheduleDao {
    @Insert suspend fun insert(p: Schedule): Long
    @Insert suspend fun insertTime(h: ScheduleTime): Long
    @Insert suspend fun insertTimes(h: List<ScheduleTime>)
    @Update suspend fun update(p: Schedule)
    @Update suspend fun updateTime(h: ScheduleTime)
    @Delete suspend fun delete(p: Schedule)

    @Query("DELETE FROM schedule_time WHERE id = :id")
    suspend fun deleteTime(id: Long)

    @Query("SELECT * FROM schedule_time WHERE id = :id")
    suspend fun timeById(id: Long): ScheduleTime?

    @Query("SELECT * FROM schedule_time WHERE schedule_id = :scheduleId")
    suspend fun timesOfSchedule(scheduleId: Long): List<ScheduleTime>

    @Transaction
    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun byId(id: Long): ScheduleWithTimes?

    @Transaction
    @Query("SELECT * FROM schedule WHERE item_id = :itemId")
    fun observeForItem(itemId: Long): Flow<List<ScheduleWithTimes>>

    @Transaction
    @Query("SELECT * FROM schedule WHERE active = 1")
    suspend fun allActive(): List<ScheduleWithTimes>

    @Transaction
    @Query("SELECT * FROM schedule WHERE active = 1")
    fun observeAllActive(): Flow<List<ScheduleWithTimes>>

    // Backup
    @Query("SELECT * FROM schedule ORDER BY id") suspend fun allSchedules(): List<Schedule>
    @Query("SELECT * FROM schedule_time ORDER BY id") suspend fun allTimes(): List<ScheduleTime>
    @Insert suspend fun insertSchedules(l: List<Schedule>)
}

@Dao
interface OccurrenceDao {
    @Insert suspend fun insert(o: Occurrence): Long
    @Update suspend fun update(o: Occurrence)

    @Query("DELETE FROM occurrence WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun byId(id: Long): Occurrence?

    @Query("SELECT * FROM occurrence WHERE schedule_time_id = :scheduleTimeId AND scheduled_date = :date")
    suspend fun findScheduled(scheduleTimeId: Long, date: LocalDate): Occurrence?

    /** Planned occurrences already handled: excluded from stock projections. */
    @Query(
        """SELECT * FROM occurrence WHERE schedule_time_id IS NOT NULL
           AND status IN ('DONE', 'SKIPPED', 'MISSED') AND scheduled_date >= :since"""
    )
    suspend fun handledSince(since: LocalDate): List<Occurrence>

    @Query("SELECT * FROM occurrence WHERE schedule_time_id IS NOT NULL AND scheduled_date = :date")
    fun observeScheduledOn(date: LocalDate): Flow<List<Occurrence>>

    /** Journal of a month (calendar): the past is read here, not in the schedules. */
    @Query("SELECT * FROM occurrence WHERE scheduled_date >= :rangeStart AND scheduled_date <= :rangeEnd")
    fun observeBetween(rangeStart: LocalDate, rangeEnd: LocalDate): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrence WHERE item_id = :itemId ORDER BY timestamp DESC LIMIT :maxRows")
    fun observeJournal(itemId: Long, maxRows: Int = 200): Flow<List<Occurrence>>

    // Backup
    @Query("SELECT * FROM occurrence ORDER BY id") suspend fun all(): List<Occurrence>
    @Insert suspend fun insertAll(l: List<Occurrence>)
}

@Dao
interface StockDao {
    @Insert suspend fun insert(m: StockMovement): Long

    @Query("DELETE FROM stock_movement WHERE occurrence_id = :occurrenceId")
    suspend fun deleteForOccurrence(occurrenceId: Long)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_movement WHERE item_id = :itemId")
    suspend fun stockOf(itemId: Long): Double

    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_movement WHERE item_id = :itemId")
    fun observeStockOf(itemId: Long): Flow<Double>

    // Backup
    @Query("SELECT * FROM stock_movement ORDER BY id") suspend fun all(): List<StockMovement>
    @Insert suspend fun insertAll(l: List<StockMovement>)
}
