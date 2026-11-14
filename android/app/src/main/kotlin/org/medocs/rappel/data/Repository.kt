package org.medocs.rappel.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime

/** Opérations qui touchent plusieurs tables : toujours transactionnelles. */
class Repository(private val db: AppDatabase) {

    /**
     * Enregistre une prise réelle et décrémente le stock. Pour une occurrence planifiée,
     * [planningHeureId] et [datePrevue] l'identifient ; sans eux, c'est une prise ponctuelle.
     * Idempotent : une occurrence déjà prise n'est pas décomptée deux fois.
     */
    suspend fun prendre(
        medicamentId: Long,
        quantite: Double,
        maintenant: Long,
        planningHeureId: Long? = null,
        datePrevue: LocalDate? = null,
        heurePrevue: LocalTime? = null,
    ): Long = db.withTransaction {
        val existante = occurrence(planningHeureId, datePrevue)
        if (existante?.statut == StatutPrise.PRISE) return@withTransaction existante.id
        val priseId = sauverOccurrence(
            existante,
            Prise(
                medicamentId = medicamentId, planningHeureId = planningHeureId, datePrevue = datePrevue,
                heurePrevue = heurePrevue, statut = StatutPrise.PRISE, quantite = quantite, horodatage = maintenant,
            ),
        )
        db.stock().inserer(StockMouvement(medicamentId = medicamentId, delta = -quantite,
            type = TypeMouvement.PRISE, horodatage = maintenant, priseId = priseId))
        priseId
    }

    /** Marque l'occurrence comme sautée (ou manquée) ; annule le décompte du stock si elle avait été prise. */
    suspend fun sauter(
        medicamentId: Long, quantite: Double, maintenant: Long,
        planningHeureId: Long, datePrevue: LocalDate, heurePrevue: LocalTime,
        statut: StatutPrise = StatutPrise.SAUTEE,
    ): Long = db.withTransaction {
        require(statut == StatutPrise.SAUTEE || statut == StatutPrise.MANQUEE)
        val existante = occurrence(planningHeureId, datePrevue)
        val priseId = sauverOccurrence(
            existante,
            Prise(medicamentId = medicamentId, planningHeureId = planningHeureId, datePrevue = datePrevue,
                heurePrevue = heurePrevue, statut = statut, quantite = quantite, horodatage = maintenant),
        )
        db.stock().supprimerDeLaPrise(priseId)
        priseId
    }

    suspend fun reporter(
        medicamentId: Long, quantite: Double, maintenant: Long, reporteJusqua: Long,
        planningHeureId: Long, datePrevue: LocalDate, heurePrevue: LocalTime,
    ): Long = db.withTransaction {
        sauverOccurrence(
            occurrence(planningHeureId, datePrevue),
            Prise(medicamentId = medicamentId, planningHeureId = planningHeureId, datePrevue = datePrevue,
                heurePrevue = heurePrevue, statut = StatutPrise.REPORTEE, quantite = quantite,
                horodatage = maintenant, reporteJusqua = reporteJusqua),
        )
    }

    /** Supprime la ligne du journal ; le mouvement de stock associé disparaît en cascade (stock restitué). */
    suspend fun annulerPrise(priseId: Long) = db.prises().supprimer(priseId)

    suspend fun ajouterStock(medicamentId: Long, quantite: Double, maintenant: Long) {
        db.stock().inserer(StockMouvement(medicamentId = medicamentId, delta = quantite,
            type = TypeMouvement.ACHAT, horodatage = maintenant))
    }

    /** Fixe le stock à la valeur comptée par l'utilisateur (écart enregistré comme correction). */
    suspend fun corrigerStock(medicamentId: Long, nouveauStock: Double, maintenant: Long) = db.withTransaction {
        val ecart = nouveauStock - db.stock().stock(medicamentId)
        if (ecart != 0.0) {
            db.stock().inserer(StockMouvement(medicamentId = medicamentId, delta = ecart,
                type = TypeMouvement.CORRECTION, horodatage = maintenant))
        }
    }

    /** Remplace les heures d'un planning (création ou modification) de façon atomique. */
    suspend fun enregistrerPlanning(planning: Planning, heures: List<PlanningHeure>): Long = db.withTransaction {
        val id = if (planning.id == 0L) db.plannings().inserer(planning)
        else { db.plannings().modifier(planning); db.plannings().supprimerHeures(planning.id); planning.id }
        db.plannings().insererHeures(heures.map { it.copy(id = 0, planningId = id) })
        id
    }

    private suspend fun occurrence(planningHeureId: Long?, date: LocalDate?): Prise? =
        if (planningHeureId != null && date != null) db.prises().occurrence(planningHeureId, date) else null

    /** Insère ou met à jour la ligne d'une occurrence ; retourne son id. */
    private suspend fun sauverOccurrence(existante: Prise?, nouvelle: Prise): Long =
        if (existante == null) db.prises().inserer(nouvelle)
        else { db.prises().modifier(nouvelle.copy(id = existante.id)); existante.id }
}
