package org.medocs.rappel.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.medocs.rappel.data.Prise
import org.medocs.rappel.data.StatutPrise
import org.medocs.rappel.data.StockMouvement
import org.medocs.rappel.data.TypeMouvement

class HistoriqueTest {
    private fun prise(id: Long, t: Long) = Prise(id = id, medicamentId = 1, statut = StatutPrise.PRISE, quantite = 1.0, horodatage = t)
    private fun mvt(id: Long, t: Long) = StockMouvement(id = id, medicamentId = 1, delta = 28.0, type = TypeMouvement.ACHAT, horodatage = t)

    @Test fun fusionneEtTrieDuPlusRecentAuPlusAncien() {
        val h = fusionnerHistorique(listOf(prise(1, 100), prise(2, 300)), listOf(mvt(1, 200), mvt(2, 400)))
        assertEquals(listOf(400L, 300L, 200L, 100L), h.map { it.instant })
        assertEquals(listOf(true, false, true, false), h.map { it is EntreeHistorique.DeStock })
    }

    @Test fun listesVides() {
        assertEquals(emptyList<EntreeHistorique>(), fusionnerHistorique(emptyList(), emptyList()))
    }
}
