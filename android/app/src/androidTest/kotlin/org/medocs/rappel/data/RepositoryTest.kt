package org.medocs.rappel.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val jour = LocalDate.of(2026, 11, 16)
    private val heure = LocalTime.of(8, 0)
    private var medId = 0L
    private var phId = 0L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = Repository(db)
        medId = db.medicaments().inserer(Medicament(nom = "Test", unite = "comprimé"))
        val planningId = repo.enregistrerPlanning(
            Planning(medicamentId = medId, dateDebut = jour),
            listOf(PlanningHeure(planningId = 0, heure = heure, quantite = 1.0)),
        )
        phId = db.plannings().actifsDuMedicament(medId).single().heures.single().id
        repo.ajouterStock(medId, 30.0, 0)
        check(planningId > 0)
    }

    @After fun tearDown() = db.close()

    private suspend fun stock() = db.stock().stock(medId)

    @Test fun priseDecrementeLeStock() = runBlocking {
        repo.prendre(medId, 1.0, 1000, phId, jour, heure)
        assertEquals(29.0, stock(), 0.0)
    }

    @Test fun priseEstIdempotente() = runBlocking {
        val a = repo.prendre(medId, 1.0, 1000, phId, jour, heure)
        val b = repo.prendre(medId, 1.0, 2000, phId, jour, heure)
        assertEquals(a, b)
        assertEquals(29.0, stock(), 0.0)
    }

    @Test fun annulerUnePriseRestitueLeStock() = runBlocking {
        val id = repo.prendre(medId, 2.0, 1000, phId, jour, heure)
        assertEquals(28.0, stock(), 0.0)
        repo.annulerPrise(id)
        assertEquals(30.0, stock(), 0.0)
        assertNull(db.prises().occurrence(phId, jour))
    }

    @Test fun sauterApresPriseRestitueLeStock() = runBlocking {
        repo.prendre(medId, 1.0, 1000, phId, jour, heure)
        repo.sauter(medId, 1.0, 2000, phId, jour, heure)
        assertEquals(30.0, stock(), 0.0)
        assertEquals(StatutPrise.SAUTEE, db.prises().occurrence(phId, jour)!!.statut)
    }

    @Test fun reporterPuisPrendreMetAJourLaMemeLigne() = runBlocking {
        repo.reporter(medId, 1.0, 1000, reporteJusqua = 5000, phId, jour, heure)
        assertEquals(StatutPrise.REPORTEE, db.prises().occurrence(phId, jour)!!.statut)
        assertEquals(30.0, stock(), 0.0)
        repo.prendre(medId, 1.0, 6000, phId, jour, heure)
        val p = db.prises().occurrence(phId, jour)!!
        assertEquals(StatutPrise.PRISE, p.statut)
        assertNull(p.reporteJusqua)
        assertEquals(29.0, stock(), 0.0)
    }

    @Test fun priseHorsPlanningDecrementeSansOccurrence() = runBlocking {
        val id = repo.prendre(medId, 0.5, 1000)
        assertNotNull(id)
        assertEquals(29.5, stock(), 0.0)
        // Deux prises ponctuelles distinctes sont autorisées (pas de contrainte d'unicité sur NULL)
        repo.prendre(medId, 0.5, 2000)
        assertEquals(29.0, stock(), 0.0)
    }

    @Test fun corrigerLeStockEnregistreLEcart() = runBlocking {
        repo.corrigerStock(medId, 12.0, 1000)
        assertEquals(12.0, stock(), 0.0)
        repo.corrigerStock(medId, 12.0, 2000) // sans écart : aucun mouvement
        assertEquals(12.0, stock(), 0.0)
    }

    @Test fun traiteesExclutReporteesEtPrisesPonctuelles() = runBlocking {
        repo.prendre(medId, 1.0, 1000, phId, jour, heure)
        repo.prendre(medId, 1.0, 1000) // ponctuelle
        assertEquals(1, db.prises().traiteesDepuis(jour).size)
    }

    @Test fun supprimerUnMedicamentSupprimeToutEnCascade() = runBlocking {
        repo.prendre(medId, 1.0, 1000, phId, jour, heure)
        db.medicaments().supprimer(db.medicaments().parId(medId)!!)
        assertEquals(0.0, stock(), 0.0)
        assertEquals(0, db.plannings().tousLesActifs().size)
        assertEquals(0, db.prises().traiteesDepuis(jour).size)
    }

    @Test fun modifierUnPlanningRemplaceSesHeures() = runBlocking {
        val p = db.plannings().actifsDuMedicament(medId).single().planning
        repo.enregistrerPlanning(p, listOf(
            PlanningHeure(planningId = 0, heure = LocalTime.of(9, 0), quantite = 2.0),
            PlanningHeure(planningId = 0, heure = LocalTime.of(21, 0), quantite = 1.0),
        ))
        val heures = db.plannings().actifsDuMedicament(medId).single().heures
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), heures.map { it.heure }.sorted())
    }
}
