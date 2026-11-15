package net.grandou.medocs.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.grandou.medocs.domain.StatutJour
import net.grandou.medocs.domain.occurrencesDuJour
import net.grandou.medocs.domain.statutsDuMois
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * L'historique passé vit dans le journal des prises : modifier ou supprimer un planning ne doit rien y changer.
 * (Le moteur écrit une ligne « manquée » pour chaque occurrence sans réponse : ici on les écrit comme lui.)
 */
@RunWith(AndroidJUnit4::class)
class ModificationPlanningTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val aujourdhui = LocalDate.of(2026, 11, 15)
    private val midi = LocalDateTime.of(aujourdhui, LocalTime.of(12, 0))
    private val huit = LocalTime.of(8, 0)
    private var medId = 0L
    private var planningId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        repo = Repository(db)
        medId = db.medicaments().inserer(Medicament(nom = "Test", unite = "comprimé"))
        repo.ajouterStock(medId, 1000.0, 0)
        planningId = repo.enregistrerPlanning(
            Planning(medicamentId = medId, dateDebut = aujourdhui.minusDays(10)),
            listOf(PlanningHeure(planningId = 0, heure = huit, quantite = 1.0)),
        )
        val h = heure8().id
        repo.prendre(medId, 1.0, 1000, h, aujourdhui.minusDays(1), huit)            // hier : pris -> vert
        repo.sauter(medId, 1.0, 1000, h, aujourdhui.minusDays(2), huit)             // avant-hier : sauté -> rouge
        repo.sauter(medId, 1.0, 1000, h, aujourdhui.minusDays(3), huit, StatutPrise.MANQUEE) // manqué -> rouge
        repo.prendre(medId, 1.0, 1000, h, aujourdhui.minusDays(4), huit)            // pris -> vert
        Unit
    }

    @After fun tearDown() = db.close()

    private suspend fun heure8() = db.plannings().parId(planningId)!!.heures.single { it.heure == huit }

    /** Statut de chaque jour des 6 derniers jours tel que le calendrier l'affiche. */
    private suspend fun passe(): Map<LocalDate, StatutJour> {
        val plannings = db.plannings().tousLesActifs()
        val jours = (1..6).map { aujourdhui.minusDays(it.toLong()) }
        return jours.map { YearMonth.from(it) }.distinct().flatMap { m ->
            statutsDuMois(m, midi, plannings, db.prises().toutes(), mapOf(medId to db.stock().stock(medId)), emptySet()).entries
        }.associate { it.key to it.value }.filterKeys { it in jours }
    }

    private suspend fun modifier(f: (Planning, List<PlanningHeure>) -> Pair<Planning, List<PlanningHeure>>): Long {
        val p = db.plannings().parId(planningId)!!
        val (nouveau, heures) = f(p.planning, p.heures)
        return repo.enregistrerPlanning(nouveau, heures)
    }

    @Test fun lHistoriqueEstCeluiDuJournal() = runBlocking {
        val p = passe()
        assertEquals(StatutJour.TOUT_PRIS, p[aujourdhui.minusDays(1)])
        assertEquals(StatutJour.AUCUNE_PRISE, p[aujourdhui.minusDays(2)])
        assertEquals(StatutJour.AUCUNE_PRISE, p[aujourdhui.minusDays(3)])
        assertEquals(StatutJour.TOUT_PRIS, p[aujourdhui.minusDays(4)])
        assertEquals(StatutJour.SANS_PRESCRIPTION, p[aujourdhui.minusDays(5)]) // aucune ligne : rien n'a été suivi
    }

    @Test fun changerLHeureEtLaDoseNeChangeAucunJourPasse() = runBlocking {
        val avant = passe()
        modifier { p, h -> p to listOf(h.single().copy(heure = LocalTime.of(21, 30), quantite = 3.0)) }
        assertEquals(avant, passe())
        // Le futur suit le nouveau planning : aujourd'hui 21 h 30, trois comprimés
        val occ = occurrencesDuJour(db.plannings().tousLesActifs(), aujourdhui).single()
        assertEquals(LocalTime.of(21, 30), occ.heure); assertEquals(3.0, occ.quantite, 0.0)
        // La ligne d'hier garde l'heure et la dose d'origine
        val hier = db.prises().toutes().single { it.datePrevue == aujourdhui.minusDays(1) }
        assertEquals(huit, hier.heurePrevue); assertEquals(1.0, hier.quantite, 0.0)
        assertEquals(StatutPrise.PRISE, hier.statut)
    }

    @Test fun changerLeRythmeOuMettreEnPauseNeChangePasLePasse() = runBlocking {
        val avant = passe()
        modifier { p, h -> p.copy(recurrence = TypeRecurrence.TOUS_LES_N_JOURS, intervalleJours = 3) to h }
        assertEquals(avant, passe())
        modifier { p, h -> p.copy(actif = false) to h }
        assertEquals(avant, passe())
    }

    @Test fun supprimerLePlanningGardeLHistorique() = runBlocking {
        val avant = passe()
        db.plannings().supprimer(db.plannings().parId(planningId)!!.planning)
        assertEquals(avant, passe())
        // Les lignes survivent, détachées du planning, avec leur date, leur heure et leur dose
        val lignes = db.prises().toutes().filter { it.datePrevue != null }
        assertEquals(4, lignes.size)
        assertEquals(true, lignes.all { it.planningHeureId == null && it.heurePrevue == huit && it.quantite == 1.0 })
    }

    @Test fun lesHeuresInchangeesGardentLeurIdentifiantEtLesPrisesDuJourLeurLien() = runBlocking {
        repo.prendre(medId, 1.0, 2000, heure8().id, aujourdhui, huit)
        val avant = heure8().id
        modifier { p, h -> p to listOf(h.single().copy(quantite = 2.0), PlanningHeure(planningId = 0, heure = LocalTime.of(20, 0), quantite = 1.0)) }
        assertEquals(avant, heure8().id) // même identifiant : la prise d'aujourd'hui reste liée
        assertNotNull(db.prises().occurrence(avant, aujourdhui))
        assertEquals(2, occurrencesDuJour(db.plannings().tousLesActifs(), aujourdhui).size)
        assertEquals(1, db.plannings().tousLesPlannings().size) // modifié sur place : plus de versions de planning
    }

    @Test fun changerLHeureGardeLaMemeHeureEtLaPriseDuJourResteLiee() = runBlocking {
        // Cas réel : prise de 8 h notée aujourd'hui, puis l'heure du planning devient 9 h : la prise reste celle de 9 h, pas « hors planning »
        repo.prendre(medId, 1.0, 2000, heure8().id, aujourdhui, huit)
        val avant = heure8().id
        modifier { p, h -> p to listOf(h.single().copy(heure = LocalTime.of(9, 0))) }
        val ligne = db.prises().toutes().single { it.datePrevue == aujourdhui }
        assertEquals(avant, ligne.planningHeureId)
        assertEquals(avant, db.plannings().parId(planningId)!!.heures.single().id)
        val occurrence = occurrencesDuJour(db.plannings().tousLesActifs(), aujourdhui).single()
        assertEquals(LocalTime.of(9, 0), occurrence.heure)
        assertNotNull(db.prises().occurrence(occurrence.planningHeure.id, aujourdhui)) // l'occurrence de 9 h est déjà prise
        assertEquals(huit, ligne.heurePrevue) // le journal garde l'heure d'origine
    }

    @Test fun uneHeureRetireeDetacheLaPriseDuJourEnPriseHorsPlanning() = runBlocking {
        repo.prendre(medId, 1.0, 2000, heure8().id, aujourdhui, huit)
        // Heure retirée et heure différente ajoutée (sans identifiant) : l'ancienne est supprimée, sa prise se détache
        modifier { p, _ -> p to listOf(PlanningHeure(planningId = 0, heure = LocalTime.of(9, 0), quantite = 1.0)) }
        val detachee = db.prises().toutes().single { it.datePrevue == aujourdhui }
        assertNull(detachee.planningHeureId)
        assertEquals(StatutPrise.PRISE, detachee.statut)
    }

    // --- Correction d'un jour passé : la ligne change de statut sans jamais disparaître ---

    private suspend fun ligneDu(jours: Long) = db.prises().toutes().single { it.datePrevue == aujourdhui.minusDays(jours) }

    @Test fun corrigerUneManqueeEnPriseDecompteLeStock() = runBlocking {
        val stockAvant = db.stock().stock(medId)
        val l = ligneDu(3)
        repo.corrigerPrise(l.id, StatutPrise.PRISE, 5000)
        assertEquals(StatutPrise.PRISE, ligneDu(3).statut)
        assertEquals(5000, ligneDu(3).horodatage)
        assertEquals(stockAvant - 1.0, db.stock().stock(medId), 0.0)
        assertEquals(StatutJour.TOUT_PRIS, passe()[aujourdhui.minusDays(3)])
    }

    @Test fun annulerUnePassePasseEnManqueeEtRestitueLeStock() = runBlocking {
        val stockAvant = db.stock().stock(medId)
        repo.corrigerPrise(ligneDu(1).id, StatutPrise.MANQUEE, 5000)
        assertEquals(StatutPrise.MANQUEE, ligneDu(1).statut)
        assertEquals(stockAvant + 1.0, db.stock().stock(medId), 0.0)
        assertEquals(StatutJour.AUCUNE_PRISE, passe()[aujourdhui.minusDays(1)])
        assertEquals(4, db.prises().toutes().size) // la ligne est toujours là : l'occurrence reste dans l'historique
    }

    @Test fun corrigerDeuxFoisNeDecomptePasDeuxFois() = runBlocking {
        val stockAvant = db.stock().stock(medId)
        repo.corrigerPrise(ligneDu(3).id, StatutPrise.PRISE, 5000)
        repo.corrigerPrise(ligneDu(3).id, StatutPrise.PRISE, 6000)
        assertEquals(stockAvant - 1.0, db.stock().stock(medId), 0.0)
        repo.corrigerPrise(ligneDu(3).id, StatutPrise.SAUTEE, 7000)
        assertEquals(stockAvant, db.stock().stock(medId), 0.0)
    }
}
