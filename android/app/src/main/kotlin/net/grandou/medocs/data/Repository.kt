package net.grandou.medocs.data

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

    /**
     * Crée ou modifie un planning. Les heures gardent leur identifiant, même si leur horaire change : une prise déjà notée aujourd'hui reste donc
     * reliée à son heure ; une heure retirée détache ses prises (elles passent en « hors planning »).
     * Le passé n'est pas concerné : l'historique se lit dans le journal des prises (voir CLAUDE.md), pas dans les plannings.
     */
    suspend fun enregistrerPlanning(planning: Planning, heures: List<PlanningHeure>): Long = db.withTransaction {
        val anciennes = if (planning.id == 0L) null else db.plannings().parId(planning.id)?.heures
        if (anciennes == null) {
            val id = db.plannings().inserer(planning.copy(id = 0))
            db.plannings().insererHeures(heures.map { it.copy(id = 0, planningId = id) })
            return@withTransaction id
        }
        db.plannings().modifier(planning)
        fusionnerHeures(anciennes, heures, planning.id)
        planning.id
    }

    /**
     * Aligne les heures d'un planning sur la nouvelle liste. Une heure est reconnue par son identifiant (l'éditeur le conserve) :
     * changer 10 h 30 en 10 h 00 modifie la MÊME heure, donc la prise déjà notée ce jour-là reste reliée. Sans identifiant (appelant
     * qui n'en fournit pas), on retombe sur l'égalité d'heure ; sinon l'heure est nouvelle et les anciennes non reprises sont supprimées.
     */
    private suspend fun fusionnerHeures(anciennes: List<PlanningHeure>, nouvelles: List<PlanningHeure>, planningId: Long) {
        val restantes = anciennes.toMutableList()
        val sansId = mutableListOf<PlanningHeure>()
        for (h in nouvelles) {
            val i = if (h.id != 0L) restantes.indexOfFirst { it.id == h.id } else -1
            if (i >= 0) majHeure(restantes.removeAt(i), h) else sansId += h
        }
        for (h in sansId) {
            val i = restantes.indexOfFirst { it.heure == h.heure }
            if (i >= 0) majHeure(restantes.removeAt(i), h)
            else db.plannings().insererHeures(listOf(h.copy(id = 0, planningId = planningId)))
        }
        restantes.forEach { db.plannings().supprimerHeure(it.id) }
    }

    private suspend fun majHeure(ancienne: PlanningHeure, nouvelle: PlanningHeure) {
        if (ancienne.heure != nouvelle.heure || ancienne.quantite != nouvelle.quantite) {
            db.plannings().modifierHeure(ancienne.copy(heure = nouvelle.heure, quantite = nouvelle.quantite))
        }
    }

    /**
     * Corrige après coup la décision d'une ligne du journal (prise, sautée, manquée), sans jamais la supprimer : l'occurrence reste
     * dans l'historique. Le stock suit (mouvement de prise créé ou retiré).
     */
    suspend fun corrigerPrise(priseId: Long, statut: StatutPrise, instant: Long) = db.withTransaction {
        require(statut != StatutPrise.REPORTEE)
        val p = db.prises().parId(priseId) ?: return@withTransaction
        db.stock().supprimerDeLaPrise(priseId)
        db.prises().modifier(p.copy(statut = statut, horodatage = instant, reporteJusqua = null))
        if (statut == StatutPrise.PRISE) {
            db.stock().inserer(StockMouvement(medicamentId = p.medicamentId, delta = -p.quantite, type = TypeMouvement.PRISE, horodatage = instant, priseId = priseId))
        }
    }

    private suspend fun occurrence(planningHeureId: Long?, date: LocalDate?): Prise? =
        if (planningHeureId != null && date != null) db.prises().occurrence(planningHeureId, date) else null

    /** Insère ou met à jour la ligne d'une occurrence ; retourne son id. */
    private suspend fun sauverOccurrence(existante: Prise?, nouvelle: Prise): Long =
        if (existante == null) db.prises().inserer(nouvelle)
        else { db.prises().modifier(nouvelle.copy(id = existante.id)); existante.id }

    /** Instantané cohérent de toutes les données de l'utilisateur (une seule transaction de lecture). */
    suspend fun exporter(): Donnees = db.withTransaction {
        Donnees(db.medicaments().tous(), db.plannings().tousLesPlannings(), db.plannings().toutesLesHeures(), db.prises().toutes(), db.stock().tous())
    }

    /** Remplace TOUTES les données par celles de la sauvegarde, de façon atomique (tout ou rien). */
    suspend fun remplacerPar(d: Donnees) = db.withTransaction {
        db.medicaments().toutSupprimer() // cascade : plannings, heures, prises, mouvements
        db.medicaments().insererTous(d.medicaments)
        db.plannings().insererPlannings(d.plannings)
        db.plannings().insererHeures(d.heures)
        db.prises().insererToutes(d.prises)
        db.stock().insererTous(d.mouvements)
    }
}
