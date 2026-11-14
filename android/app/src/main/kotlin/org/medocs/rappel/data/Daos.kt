package org.medocs.rappel.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MedicamentDao {
    @Insert suspend fun inserer(m: Medicament): Long
    @Update suspend fun modifier(m: Medicament)
    @Delete suspend fun supprimer(m: Medicament)

    @Query("SELECT * FROM medicament WHERE archive = 0")
    suspend fun tousActifs(): List<Medicament>

    @Query("SELECT * FROM medicament WHERE id = :id")
    suspend fun parId(id: Long): Medicament?

    @Query(
        """SELECT m.*, COALESCE((SELECT SUM(delta) FROM stock_mouvement WHERE medicamentId = m.id), 0) AS stock
           FROM medicament m WHERE archive = 0 ORDER BY nom COLLATE NOCASE"""
    )
    fun observerAvecStock(): Flow<List<MedicamentAvecStock>>
}

@Dao
interface PlanningDao {
    @Insert suspend fun inserer(p: Planning): Long
    @Insert suspend fun insererHeures(h: List<PlanningHeure>)
    @Update suspend fun modifier(p: Planning)
    @Delete suspend fun supprimer(p: Planning)

    @Query("DELETE FROM planning_heure WHERE planningId = :planningId")
    suspend fun supprimerHeures(planningId: Long)

    @Transaction
    @Query("SELECT * FROM planning WHERE medicamentId = :medicamentId")
    fun observerDuMedicament(medicamentId: Long): Flow<List<PlanningAvecHeures>>

    @Transaction
    @Query("SELECT * FROM planning WHERE actif = 1")
    fun observerTousLesActifs(): Flow<List<PlanningAvecHeures>>

    @Transaction
    @Query("SELECT * FROM planning WHERE actif = 1")
    suspend fun tousLesActifs(): List<PlanningAvecHeures>

    @Transaction
    @Query("SELECT * FROM planning WHERE actif = 1 AND medicamentId = :medicamentId")
    suspend fun actifsDuMedicament(medicamentId: Long): List<PlanningAvecHeures>
}

@Dao
interface PriseDao {
    @Insert suspend fun inserer(p: Prise): Long
    @Update suspend fun modifier(p: Prise)

    @Query("DELETE FROM prise WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("SELECT * FROM prise WHERE planningHeureId = :planningHeureId AND datePrevue = :date")
    suspend fun occurrence(planningHeureId: Long, date: LocalDate): Prise?

    /** Occurrences planifiées déjà traitées (prise, sautée, manquée) : exclues des projections de stock. */
    @Query(
        """SELECT * FROM prise WHERE planningHeureId IS NOT NULL
           AND statut IN ('PRISE', 'SAUTEE', 'MANQUEE') AND datePrevue >= :depuis"""
    )
    suspend fun traiteesDepuis(depuis: LocalDate): List<Prise>

    @Query(
        """SELECT * FROM prise WHERE planningHeureId IS NOT NULL
           AND statut IN ('PRISE', 'SAUTEE', 'MANQUEE') AND datePrevue >= :depuis"""
    )
    fun observerTraiteesDepuis(depuis: LocalDate): Flow<List<Prise>>

    @Query("SELECT * FROM prise WHERE medicamentId = :medicamentId ORDER BY horodatage DESC LIMIT :limite")
    fun observerJournal(medicamentId: Long, limite: Int = 200): Flow<List<Prise>>

    /** Occurrences planifiées d'un jour, quel que soit leur statut. */
    @Query("SELECT * FROM prise WHERE planningHeureId IS NOT NULL AND datePrevue = :date")
    fun observerPlanifieesDuJour(date: LocalDate): Flow<List<Prise>>

    /** Prises hors planning dans l'intervalle [debut, fin[ (epoch ms). */
    @Query("SELECT * FROM prise WHERE planningHeureId IS NULL AND horodatage >= :debut AND horodatage < :fin ORDER BY horodatage")
    fun observerPonctuellesEntre(debut: Long, fin: Long): Flow<List<Prise>>

    /** Journal utile au moteur de rappels : occurrences récentes + toutes les prises reportées. */
    @Query("SELECT * FROM prise WHERE planningHeureId IS NOT NULL AND (datePrevue >= :depuis OR statut = 'REPORTEE')")
    suspend fun pourMoteur(depuis: LocalDate): List<Prise>

    @Query("SELECT * FROM prise WHERE statut = 'REPORTEE'")
    suspend fun reportees(): List<Prise>
}

@Dao
interface StockDao {
    @Insert suspend fun inserer(m: StockMouvement): Long

    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_mouvement WHERE medicamentId = :medicamentId")
    suspend fun stock(medicamentId: Long): Double

    @Query("DELETE FROM stock_mouvement WHERE priseId = :priseId")
    suspend fun supprimerDeLaPrise(priseId: Long)
}
