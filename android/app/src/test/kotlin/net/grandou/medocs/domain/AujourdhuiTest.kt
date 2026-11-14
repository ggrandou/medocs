package net.grandou.medocs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AujourdhuiTest {
    private val jour = LocalDate.of(2026, 11, 16)
    private val occ = Occurrence(PlanningHeure(id = 5, planningId = 1, heure = LocalTime.of(8, 0), quantite = 1.0), jour, 1)
    private fun prise(s: StatutPrise) = Prise(medicamentId = 1, planningHeureId = 5, datePrevue = jour, statut = s, quantite = 1.0, horodatage = 0)

    @Test fun sansPriseSelonLHeure() {
        assertEquals(EtatOccurrence.A_VENIR, etatOccurrence(occ, null, LocalDateTime.of(jour, LocalTime.of(7, 59))))
        assertEquals(EtatOccurrence.EN_RETARD, etatOccurrence(occ, null, LocalDateTime.of(jour, LocalTime.of(8, 1))))
    }

    @Test fun lHeurePileEstEncoreAVenir() {
        assertEquals(EtatOccurrence.A_VENIR, etatOccurrence(occ, null, LocalDateTime.of(jour, LocalTime.of(8, 0))))
    }

    @Test fun leStatutDuJournalPrime() {
        val tard = LocalDateTime.of(jour, LocalTime.of(23, 0))
        assertEquals(EtatOccurrence.PRISE, etatOccurrence(occ, prise(StatutPrise.PRISE), tard))
        assertEquals(EtatOccurrence.SAUTEE, etatOccurrence(occ, prise(StatutPrise.SAUTEE), tard))
        assertEquals(EtatOccurrence.MANQUEE, etatOccurrence(occ, prise(StatutPrise.MANQUEE), tard))
        assertEquals(EtatOccurrence.REPORTEE, etatOccurrence(occ, prise(StatutPrise.REPORTEE), tard))
    }

    @Test fun enAttente() {
        assertTrue(EtatOccurrence.A_VENIR.enAttente)
        assertTrue(EtatOccurrence.EN_RETARD.enAttente)
        assertTrue(EtatOccurrence.REPORTEE.enAttente)
        assertFalse(EtatOccurrence.PRISE.enAttente)
        assertFalse(EtatOccurrence.SAUTEE.enAttente)
        assertFalse(EtatOccurrence.MANQUEE.enAttente)
    }
}
