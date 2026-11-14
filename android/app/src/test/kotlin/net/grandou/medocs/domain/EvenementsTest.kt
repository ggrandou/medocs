package net.grandou.medocs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EvenementsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val jour = LocalDate.of(2026, 11, 16)
    private val delai = 120L * 60_000
    private val plannings = listOf(
        PlanningAvecHeures(
            Planning(id = 1, medicamentId = 1, dateDebut = jour),
            listOf(
                PlanningHeure(id = 10, planningId = 1, heure = LocalTime.of(8, 0), quantite = 1.0),
                PlanningHeure(id = 11, planningId = 1, heure = LocalTime.of(20, 0), quantite = 1.0),
            ),
        ),
    )

    private fun t(j: LocalDate, h: Int, m: Int = 0) = instantDe(j, LocalTime.of(h, m), zone)
    private fun prise(ph: Long, statut: StatutPrise, report: Long? = null, date: LocalDate = jour) =
        Prise(medicamentId = 1, planningHeureId = ph, datePrevue = date, statut = statut, quantite = 1.0, horodatage = 0, reporteJusqua = report)

    private fun evts(prises: List<Prise>, apres: Long, jusqua: Long) = evenementsEntre(plannings, prises, apres, jusqua, zone, delai)

    @Test fun rappelEtEcheanceDUneOccurrence() {
        val e = evts(emptyList(), t(jour, 7), t(jour, 11))
        assertEquals(listOf(t(jour, 8), t(jour, 10)), e.map { it.instant })
        assertTrue(e[0] is Rappel && !(e[0] as Rappel).reporte)
        assertTrue(e[1] is Echeance)
    }

    @Test fun borneInferieureExclueEtSuperieureIncluse() {
        assertTrue(evts(emptyList(), t(jour, 8), t(jour, 9)).none { it is Rappel })
        assertEquals(1, evts(emptyList(), t(jour, 7), t(jour, 8)).count { it is Rappel })
    }

    @Test fun occurrenceDejaTraiteeNeGenereRien() {
        for (s in listOf(StatutPrise.PRISE, StatutPrise.SAUTEE, StatutPrise.MANQUEE)) {
            val e = evts(listOf(prise(10, s)), t(jour, 7), t(jour, 9))
            assertTrue("$s", e.none { it.occurrence.planningHeure.id == 10L })
        }
    }

    @Test fun reportUtiliseLHeureDuReportEtDecaleLEcheance() {
        val report = t(jour, 8, 30)
        val e = evts(listOf(prise(10, StatutPrise.REPORTEE, report)), t(jour, 7), t(jour, 12))
        val du10 = e.filter { it.occurrence.planningHeure.id == 10L }
        assertEquals(listOf(report, report + delai), du10.map { it.instant })
        assertTrue((du10[0] as Rappel).reporte)
    }

    @Test fun planningInactifOuHorsPeriodeNeGenereRien() {
        val inactif = listOf(plannings[0].copy(planning = plannings[0].planning.copy(actif = false)))
        assertTrue(evenementsEntre(inactif, emptyList(), t(jour, 0), t(jour, 23), zone, delai).isEmpty())
        val avant = LocalDate.of(2026, 11, 15)
        assertTrue(evts(emptyList(), t(avant, 0), t(avant, 23)).isEmpty())
    }

    @Test fun echeanceApresMinuitEstTrouveeParLeJourSuivant() {
        // 20:00 + 2 h = 22:00 le même jour ; avec un délai de 5 h l'échéance passe le lendemain à 01:00
        val long = 5L * 3600_000
        val e = evenementsEntre(plannings, emptyList(), t(jour, 23), t(jour.plusDays(1), 2), zone, long)
        assertEquals(t(jour.plusDays(1), 1), e.single().instant)
        assertTrue(e.single() is Echeance)
    }

    @Test fun prochainEvenementEnjambeLesJours() {
        val p = prochainEvenement(plannings, emptyList(), t(jour, 22, 30), zone, delai)
        assertNotNull(p)
        assertEquals(t(jour.plusDays(1), 8), p!!.instant)
    }

    @Test fun prochainEvenementEstUneEcheanceSiRienDAutre() {
        // Tout le planning est fini : il ne reste que les échéances des occurrences non traitées
        val fini = listOf(plannings[0].copy(planning = plannings[0].planning.copy(dateFin = jour)))
        val p = prochainEvenement(fini, emptyList(), t(jour, 8, 30), zone, delai)!!
        assertEquals(t(jour, 10), p.instant)
        assertTrue(p is Echeance)
        assertNull(prochainEvenement(fini, emptyList(), t(jour, 22, 1), zone, delai))
    }

    @Test fun heureDEteLesInstantsSuiventLHeureMurale() {
        // Passage à l'heure d'hiver le 2026-10-25 : 08:00 reste 08:00 locale
        val hiver = LocalDate.of(2026, 10, 25)
        val p = listOf(plannings[0].copy(planning = plannings[0].planning.copy(dateDebut = hiver.minusDays(5))))
        val e = evenementsEntre(p, emptyList(), t(hiver, 7), t(hiver, 9), zone, delai).first() as Rappel
        assertEquals(LocalTime.of(8, 0), java.time.Instant.ofEpochMilli(e.instant).atZone(zone).toLocalTime())
    }
}
