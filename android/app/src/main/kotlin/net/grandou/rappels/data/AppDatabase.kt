package net.grandou.rappels.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import java.time.LocalTime

/** "Wall-clock" dates and times stored as ISO 8601 text. Enumerations are stored as text by Room. */
class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun timeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)
}

/** The user's data database. Separate from the future BDPM database (read-only, embedded). */
@Database(
    entities = [Item::class, Schedule::class, ScheduleTime::class, Occurrence::class, StockMovement::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun schedules(): ScheduleDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun stock(): StockDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "user.db")
                .build().also { instance = it }
        }
    }
}
