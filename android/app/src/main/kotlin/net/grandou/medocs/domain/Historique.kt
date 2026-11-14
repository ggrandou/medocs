package net.grandou.medocs.domain

import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StockMouvement

/** Ligne de l'historique d'un médicament : une prise du journal, ou un mouvement de stock manuel. */
sealed interface EntreeHistorique {
    val instant: Long

    data class DePrise(val prise: Prise) : EntreeHistorique {
        override val instant get() = prise.horodatage
    }

    data class DeStock(val mouvement: StockMouvement) : EntreeHistorique {
        override val instant get() = mouvement.horodatage
    }
}

/** Fusionne journal et mouvements manuels, du plus récent au plus ancien. */
fun fusionnerHistorique(prises: List<Prise>, mouvements: List<StockMouvement>): List<EntreeHistorique> =
    (prises.map { EntreeHistorique.DePrise(it) } + mouvements.map { EntreeHistorique.DeStock(it) })
        .sortedByDescending { it.instant }
