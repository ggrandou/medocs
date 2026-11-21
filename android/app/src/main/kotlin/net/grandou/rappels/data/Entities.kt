package net.grandou.rappels.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalTime

// Entities of the user database. See docs/schema.md: keep it in sync.

/** What is tracked (medication, filter, contact lenses, kibble…). External info is copied, with no foreign key. */
@Entity(tableName = "item")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Free field: the database does not constrain its values. */
    val type: String = "",
    /** Dose and stock unit: "comprimé", "ml", "paire"… */
    val unit: String = "unité",
    val packaging: String? = null,
    @ColumnInfo(name = "units_per_package") val unitsPerPackage: Double? = null,
    @ColumnInfo(name = "track_stock") val trackStock: Boolean = true,
    @ColumnInfo(name = "threshold_days") val thresholdDays: Int = 7,
    @ColumnInfo(name = "threshold_units") val thresholdUnits: Double? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    val reference: String? = null,
    val composition: String? = null,
    val form: String? = null,
    val archived: Boolean = false,
)

enum class Recurrence { PERIODIC, WEEKDAYS, RANK_IN_MONTH, CYCLE }

enum class IntervalUnit { DAY, WEEK, MONTH, YEAR }

/** Recurrence rule of an item; times and quantities are in [ScheduleTime]. */
@Entity(
    tableName = "schedule",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["item_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("item_id")],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val recurrence: Recurrence = Recurrence.PERIODIC,
    val interval: Int = 1,
    @ColumnInfo(name = "interval_unit") val intervalUnit: IntervalUnit = IntervalUnit.DAY,
    /** Bit mask: bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(name = "weekdays") val weekdays: Int = 0,
    /** 1 to 4, or -1 for the last one. */
    val rank: Int? = null,
    @ColumnInfo(name = "active_days") val activeDays: Int? = null,
    @ColumnInfo(name = "pause_days") val pauseDays: Int? = null,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    /** Last day included; null = no end. */
    @ColumnInfo(name = "end_date") val endDate: LocalDate? = null,
    val active: Boolean = true,
)

/** A time of day of a schedule, with its quantity. Local "wall-clock" time. */
@Entity(
    tableName = "schedule_time",
    foreignKeys = [ForeignKey(Schedule::class, ["id"], ["schedule_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("schedule_id")],
)
data class ScheduleTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    val time: LocalTime,
    /** Null = nothing to deduct from stock. */
    val quantity: Double? = null,
)

enum class Status { SNOOZED, DONE, SKIPPED, MISSED }

/**
 * Journal: a row exists as soon as the user acts on a planned occurrence, the engine marks it
 * "missed", or a one-off occurrence is created (scheduleTimeId null).
 * Future occurrences are NOT stored: they are computed from the schedules.
 */
@Entity(
    tableName = "occurrence",
    foreignKeys = [
        ForeignKey(Item::class, ["id"], ["item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ScheduleTime::class, ["id"], ["schedule_time_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("item_id"),
        Index(value = ["schedule_time_id", "scheduled_date"], unique = true),
    ],
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "schedule_time_id") val scheduleTimeId: Long? = null,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: LocalDate? = null,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: LocalTime? = null,
    val status: Status,
    val quantity: Double? = null,
    /** Instant (epoch ms) of the action, or of the decision for SKIPPED / MISSED. */
    val timestamp: Long,
    /** For SNOOZED: instant (epoch ms) of the new reminder. */
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long? = null,
)

enum class MovementType { PURCHASE, CONSUMPTION, CORRECTION }

/** Stock journal: current stock = SUM(delta), never a stored value. */
@Entity(
    tableName = "stock_movement",
    foreignKeys = [
        ForeignKey(Item::class, ["id"], ["item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Occurrence::class, ["id"], ["occurrence_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("item_id"), Index("occurrence_id")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val delta: Double,
    val type: MovementType,
    val timestamp: Long,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long? = null,
)

data class ScheduleWithTimes(
    @Embedded val schedule: Schedule,
    @Relation(parentColumn = "id", entityColumn = "schedule_id") val times: List<ScheduleTime>,
)

data class ItemWithStock(
    @Embedded val item: Item,
    val stock: Double,
)
