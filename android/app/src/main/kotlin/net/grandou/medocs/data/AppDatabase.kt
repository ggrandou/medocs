package net.grandou.medocs.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateVersString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringVersDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun heureVersString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringVersHeure(s: String?): LocalTime? = s?.let(LocalTime::parse)
}

/** Base des données de l'utilisateur. Distincte de la base BDPM (lecture seule, embarquée). */
@Database(
    entities = [Medicament::class, Planning::class, PlanningHeure::class, Prise::class, StockMouvement::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicaments(): MedicamentDao
    abstract fun plannings(): PlanningDao
    abstract fun prises(): PriseDao
    abstract fun stock(): StockDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "utilisateur.db")
                .build().also { instance = it }
        }
    }
}
