package org.medocs.rappel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.medocs.rappel.data.Medicament
import org.medocs.rappel.data.Planning
import org.medocs.rappel.data.PlanningAvecHeures
import org.medocs.rappel.data.PlanningHeure
import org.medocs.rappel.data.TypeRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class PlanificationTest {
    private val lundi = LocalDate.of(2026, 11, 16) // un lundi
    private var nextId = 1L

    private fun planning(
        recurrence: TypeRecurrence = TypeRecurrence.QUOTIDIEN,
        jours: Int = 0,
        n: Int = 1,
        debut: LocalDate = lundi,
        fin: LocalDate? = null,
        actif: Boolean = true,
        medicamentId: Long = 1,
        vararg heures: Pair<String, Double>,
    ): PlanningAvecHeures {
        val p = Planning(id = nextId++, medicamentId = medicamentId, recurrence = recurrence, joursSemaine = jours,
            intervalleJours = n, dateDebut = debut, dateFin = fin, actif = actif)
        return PlanningAvecHeures(p, heures.map { (h, q) -> PlanningHeure(id = nextId++, planningId = p.id, heure = LocalTime.parse(h), quantite = q) })
    }

    private fun quotidien(vararg heures: Pair<String, Double>, fin: LocalDate? = null) =
        planning(recurrence = TypeRecurrence.QUOTIDIEN, fin = fin, heures = heures)

    @Test fun quotidienSurvientChaqueJourApresLeDebut() {
        val p = quotidien("08:00" to 1.0).planning
        assertFalse(p.survientLe(lundi.minusDays(1)))
        assertTrue(p.survientLe(lundi))
        assertTrue(p.survientLe(lundi.plusDays(400)))
    }

    @Test fun dateFinEstIncluse() {
        val p = quotidien("08:00" to 1.0, fin = lundi.plusDays(2)).planning
        assertTrue(p.survientLe(lundi.plusDays(2)))
        assertFalse(p.survientLe(lundi.plusDays(3)))
    }

    @Test fun planningInactifNeSurvientJamais() {
        val p = planning(actif = false, heures = arrayOf("08:00" to 1.0)).planning
        assertFalse(p.survientLe(lundi))
    }

    @Test fun joursDeSemaine() {
        val p = planning(TypeRecurrence.JOURS_SEMAINE, jours = masqueJours(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            heures = arrayOf("08:00" to 1.0)).planning
        assertTrue(p.survientLe(lundi))
        assertFalse(p.survientLe(lundi.plusDays(1)))
        assertTrue(p.survientLe(lundi.plusDays(3)))
        assertFalse(p.survientLe(lundi.plusDays(6))) // dimanche
        assertTrue(p.survientLe(lundi.plusDays(7)))
    }

    @Test fun tousLesNJoursSeCalculeDepuisLaDateDeDebut() {
        val p = planning(TypeRecurrence.TOUS_LES_N_JOURS, n = 3, heures = arrayOf("08:00" to 1.0)).planning
        assertTrue(p.survientLe(lundi))
        assertFalse(p.survientLe(lundi.plusDays(1)))
        assertFalse(p.survientLe(lundi.plusDays(2)))
        assertTrue(p.survientLe(lundi.plusDays(3)))
        assertTrue(p.survientLe(lundi.plusDays(30)))
    }

    @Test fun occurrencesDuJourTrieesParHeure() {
        val a = quotidien("20:00" to 1.0, "08:00" to 2.0)
        val occ = occurrencesDuJour(listOf(a), lundi)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), occ.map { it.heure })
        assertEquals(listOf(2.0, 1.0), occ.map { it.quantite })
    }

    @Test fun prochaineOccurrenceLeMemeJourPuisLeLendemain() {
        val a = quotidien("08:00" to 1.0, "20:00" to 1.0)
        assertEquals(LocalTime.of(20, 0), prochaineOccurrence(listOf(a), lundi, LocalTime.of(9, 0))!!.heure)
        val demain = prochaineOccurrence(listOf(a), lundi, LocalTime.of(20, 0))!!
        assertEquals(lundi.plusDays(1), demain.date)
        assertEquals(LocalTime.of(8, 0), demain.heure)
    }

    @Test fun prochaineOccurrenceNulleSiPlanningTermine() {
        val a = quotidien("08:00" to 1.0, fin = lundi)
        assertNull(prochaineOccurrence(listOf(a), lundi, LocalTime.of(9, 0)))
    }

    @Test fun projectionSansPlanning() {
        assertEquals(ProjectionStock.SansPlanning, projeterStock(10.0, emptyList(), lundi))
        assertEquals(ProjectionStock.SansPlanning, projeterStock(10.0, listOf(planning(actif = false, heures = arrayOf("08:00" to 1.0))), lundi))
    }

    @Test fun ruptureLorsqueLeStockNeCouvrePlusUneDose() {
        // 1 comprimé/jour, stock 3 : jours 0,1,2 couverts, rupture le jour 3
        val r = projeterStock(3.0, listOf(quotidien("08:00" to 1.0)), lundi)
        assertEquals(ProjectionStock.Rupture(lundi.plusDays(3), 3), r)
    }

    @Test fun stockExactementEpuiseNEstPasUneRuptureAvantLaProchaineDose() {
        val r = projeterStock(2.0, listOf(quotidien("08:00" to 1.0, "20:00" to 1.0)), lundi) as ProjectionStock.Rupture
        assertEquals(1, r.joursRestants) // aujourd'hui couvert (2), demain à 08:00 manquant
    }

    @Test fun ruptureDesAujourdhuiSiStockNul() {
        val r = projeterStock(0.0, listOf(quotidien("08:00" to 1.0)), lundi) as ProjectionStock.Rupture
        assertEquals(0, r.joursRestants)
    }

    @Test fun demiComprimes() {
        val r = projeterStock(1.0, listOf(quotidien("08:00" to 0.5)), lundi) as ProjectionStock.Rupture
        assertEquals(2, r.joursRestants)
    }

    @Test fun traiteesNeSontPasRecomptees() {
        val a = quotidien("08:00" to 1.0, "20:00" to 1.0)
        val matin = a.heures.first { it.heure == LocalTime.of(8, 0) }
        // Stock 2 déjà amputé de la prise du matin : reste 1 pour ce soir seulement
        val r = projeterStock(1.0, listOf(a), lundi, traitees = setOf(matin.id to lundi)) as ProjectionStock.Rupture
        assertEquals(1, r.joursRestants)
    }

    @Test fun stockSuffisantJusquALaFinDuTraitement() {
        val a = quotidien("08:00" to 1.0, fin = lundi.plusDays(6)) // 7 doses
        assertEquals(ProjectionStock.Suffisant, projeterStock(7.0, listOf(a), lundi))
        assertTrue(projeterStock(6.0, listOf(a), lundi) is ProjectionStock.Rupture)
    }

    @Test fun plusieursPlanningsDuMemeMedicamentSAdditionnent() {
        val a = quotidien("08:00" to 1.0)
        val b = quotidien("20:00" to 1.0)
        val r = projeterStock(4.0, listOf(a, b), lundi) as ProjectionStock.Rupture
        assertEquals(2, r.joursRestants)
    }

    @Test fun alerteSelonSeuilEnJours() {
        val m = Medicament(id = 1, nom = "X", seuilJours = 7)
        val plan = listOf(quotidien("08:00" to 1.0))
        assertTrue(stockFaible(m, 5.0, projeterStock(5.0, plan, lundi)))   // rupture dans 5 j <= 7
        assertTrue(stockFaible(m, 7.0, projeterStock(7.0, plan, lundi)))   // rupture dans 7 j <= 7
        assertFalse(stockFaible(m, 8.0, projeterStock(8.0, plan, lundi)))  // 8 j > 7
    }

    @Test fun alerteSelonSeuilEnUnitesSansPlanning() {
        val m = Medicament(id = 1, nom = "X", seuilUnites = 4.0)
        assertTrue(stockFaible(m, 4.0, ProjectionStock.SansPlanning))
        assertFalse(stockFaible(m, 5.0, ProjectionStock.SansPlanning))
    }

    @Test fun pasDAlerteSansPlanningNiSeuilEnUnites() {
        assertFalse(stockFaible(Medicament(id = 1, nom = "X"), 0.0, ProjectionStock.SansPlanning))
    }

    @Test fun pasDAlerteSiLeTraitementSeTermineAvantLaRupture() {
        val m = Medicament(id = 1, nom = "X", seuilJours = 30)
        assertFalse(stockFaible(m, 7.0, ProjectionStock.Suffisant))
    }
}
