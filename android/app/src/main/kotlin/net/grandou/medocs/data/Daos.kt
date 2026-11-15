package net.grandou.medocs.data

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

    // --- Sauvegarde : les clés étrangères en cascade vident aussi plannings, prises et mouvements ---
    @Query("SELECT * FROM medicament ORDER BY id") suspend fun tous(): List<Medicament>
    @Query("DELETE FROM medicament") suspend fun toutSupprimer()
    @Insert suspend fun insererTous(l: List<Medicament>)

    @Query("SELECT * FROM medicament WHERE archive = 1 ORDER BY nom COLLATE NOCASE")
    fun observerArchives(): Flow<List<Medicament>>

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

    @Query("SELECT * FROM planning ORDER BY id") suspend fun tousLesPlannings(): List<Planning>
    @Query("SELECT * FROM planning_heure ORDER BY id") suspend fun toutesLesHeures(): List<PlanningHeure>
    @Insert suspend fun insererPlannings(l: List<Planning>)

    @Query("DELETE FROM planning_heure WHERE planningId = :planningId")
    suspend fun supprimerHeures(planningId: Long)

    @Update suspend fun modifierHeure(h: PlanningHeure)

    @Query("DELETE FROM planning_heure WHERE id = :id")
    suspend fun supprimerHeure(id: Long)

    @Transaction
    @Query("SELECT * FROM planning WHERE id = :id")
    suspend fun parId(id: Long): PlanningAvecHeures?

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

    @Query("SELECT * FROM prise WHERE id = :id")
    suspend fun parId(id: Long): Prise?

    @Query("SELECT * FROM prise ORDER BY id") suspend fun toutes(): List<Prise>
    @Insert suspend fun insererToutes(l: List<Prise>)

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

    /**
     * Lignes du journal dont la date prévue tombe dans [debut, fin] inclus (calendrier mensuel), qu'elles soient encore reliées
     * à un planning ou non : le passé se lit dans le journal, pas dans les plannings (qu'on peut modifier ou supprimer).
     */
    @Query("SELECT * FROM prise WHERE datePrevue >= :debut AND datePrevue <= :fin")
    fun observerPrevuesEntre(debut: LocalDate, fin: LocalDate): Flow<List<Prise>>

    /** Toutes les lignes d'un jour passé (reliées ou non à un planning), par heure prévue. */
    @Query("SELECT * FROM prise WHERE datePrevue = :date ORDER BY heurePrevue")
    fun observerPrevuesLe(date: LocalDate): Flow<List<Prise>>

    /** Occurrences manquées des derniers jours (strictement avant [avant]), pour les signaler à l'utilisateur. */
    @Query("SELECT * FROM prise WHERE statut = 'MANQUEE' AND datePrevue >= :depuis AND datePrevue < :avant ORDER BY datePrevue DESC, heurePrevue DESC")
    fun observerManquees(depuis: LocalDate, avant: LocalDate): Flow<List<Prise>>

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

    @Query("SELECT * FROM stock_mouvement ORDER BY id") suspend fun tous(): List<StockMouvement>
    @Insert suspend fun insererTous(l: List<StockMouvement>)

    /** Achats et corrections (les mouvements PRISE se lisent dans le journal des prises). */
    @Query("SELECT * FROM stock_mouvement WHERE medicamentId = :medicamentId AND type != 'PRISE' ORDER BY horodatage DESC")
    fun observerMouvements(medicamentId: Long): Flow<List<StockMouvement>>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_mouvement WHERE medicamentId = :medicamentId")
    suspend fun stock(medicamentId: Long): Double

    @Query("DELETE FROM stock_mouvement WHERE priseId = :priseId")
    suspend fun supprimerDeLaPrise(priseId: Long)
}
