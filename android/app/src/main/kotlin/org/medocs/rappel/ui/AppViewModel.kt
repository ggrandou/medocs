package org.medocs.rappel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.medocs.rappel.bdpm.Bdpm
import org.medocs.rappel.bdpm.PresentationBdpm
import org.medocs.rappel.bdpm.SpecialiteBdpm
import org.medocs.rappel.data.AppDatabase
import org.medocs.rappel.data.Medicament
import org.medocs.rappel.data.Planning
import org.medocs.rappel.data.PlanningAvecHeures
import org.medocs.rappel.data.PlanningHeure
import org.medocs.rappel.data.Prise
import org.medocs.rappel.data.Repository
import org.medocs.rappel.data.StatutPrise
import org.medocs.rappel.domain.EtatOccurrence
import org.medocs.rappel.domain.Occurrence
import org.medocs.rappel.domain.etatOccurrence
import org.medocs.rappel.domain.occurrencesDuJour
import java.time.LocalDateTime
import java.time.ZoneId
import org.medocs.rappel.domain.ProjectionStock
import org.medocs.rappel.domain.projeterStock
import org.medocs.rappel.domain.stockFaible
import java.time.LocalDate

data class LigneMedicament(
    val medicament: Medicament,
    val stock: Double,
    val projection: ProjectionStock,
    val alerte: Boolean,
)

data class LigneAujourdhui(
    val occurrence: Occurrence,
    val medicament: Medicament,
    val prise: Prise?,
    val etat: EtatOccurrence,
)

data class PonctuelleAujourdhui(val prise: Prise, val medicament: Medicament)

data class EtatAujourdhui(
    val date: java.time.LocalDate,
    val lignes: List<LigneAujourdhui>,
    val ponctuelles: List<PonctuelleAujourdhui>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = Repository(db)

    // La copie de la base BDPM (premier lancement) se fait hors du thread principal.
    private val bdpm = viewModelScope.async(Dispatchers.IO) { Bdpm.ouvrir(app) }

    val lignes: StateFlow<List<LigneMedicament>?> = combine(
        db.medicaments().observerAvecStock(),
        db.plannings().observerTousLesActifs(),
        db.prises().observerTraiteesDepuis(LocalDate.now().minusDays(1)),
    ) { medicaments, plannings, traitees ->
        val aujourdhui = LocalDate.now()
        val cles = traitees.mapNotNull { p -> p.planningHeureId?.let { it to p.datePrevue!! } }.toSet()
        medicaments.map { (m, stock) ->
            val siens = plannings.filter { it.planning.medicamentId == m.id }
            val projection = projeterStock(stock, siens, aujourdhui, cles)
            LigneMedicament(m, stock, projection, stockFaible(m, stock, projection))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- Écran « Aujourd'hui » ---
    private val zone = ZoneId.systemDefault()
    private fun debutDuJour(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Horloge qui rafraîchit l'état (prises en retard) et détecte le changement de jour. */
    private val horloge: Flow<LocalDateTime> = flow {
        while (true) { emit(LocalDateTime.now()); delay(30_000) }
    }
    private val jour = horloge.map { it.toLocalDate() }.distinctUntilChanged()

    val aujourdhui: StateFlow<EtatAujourdhui?> = combine(
        db.medicaments().observerAvecStock(),
        db.plannings().observerTousLesActifs(),
        jour.flatMapLatest { db.prises().observerPlanifieesDuJour(it) },
        jour.flatMapLatest { d -> db.prises().observerPonctuellesEntre(debutDuJour(d), debutDuJour(d.plusDays(1))) },
        horloge,
    ) { medicaments, plannings, planifiees, ponctuelles, maintenant ->
        val parId = medicaments.associate { it.medicament.id to it.medicament }
        val lignes = occurrencesDuJour(plannings, maintenant.toLocalDate()).mapNotNull { occ ->
            val m = parId[occ.medicamentId] ?: return@mapNotNull null
            val prise = planifiees.firstOrNull { it.planningHeureId == occ.planningHeure.id }
            LigneAujourdhui(occ, m, prise, etatOccurrence(occ, prise, maintenant))
        }
        EtatAujourdhui(
            maintenant.toLocalDate(), lignes,
            ponctuelles.mapNotNull { p -> parId[p.medicamentId]?.let { PonctuelleAujourdhui(p, it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prendre(l: LigneAujourdhui) = viewModelScope.launch {
        val o = l.occurrence
        repo.prendre(l.medicament.id, o.quantite, System.currentTimeMillis(), o.planningHeure.id, o.date, o.heure)
    }

    fun sauter(l: LigneAujourdhui) = viewModelScope.launch {
        val o = l.occurrence
        repo.sauter(l.medicament.id, o.quantite, System.currentTimeMillis(), o.planningHeure.id, o.date, o.heure)
    }

    fun reporter(l: LigneAujourdhui, minutes: Int) = viewModelScope.launch {
        val o = l.occurrence
        val maintenant = System.currentTimeMillis()
        repo.reporter(l.medicament.id, o.quantite, maintenant, maintenant + minutes * 60_000L, o.planningHeure.id, o.date, o.heure)
    }

    /** Annule la décision (prise, sautée, reportée) : l'occurrence redevient en attente et le stock est restitué. */
    fun annuler(prise: Prise) = viewModelScope.launch { repo.annulerPrise(prise.id) }

    fun priseHorsPlanning(m: Medicament, quantite: Double) = viewModelScope.launch {
        repo.prendre(m.id, quantite, System.currentTimeMillis())
    }

    // --- Plannings ---
    fun plannings(medicamentId: Long): Flow<List<PlanningAvecHeures>> = db.plannings().observerDuMedicament(medicamentId)

    fun enregistrerPlanning(planning: Planning, heures: List<PlanningHeure>) = viewModelScope.launch {
        repo.enregistrerPlanning(planning, heures)
    }

    fun supprimerPlanning(planning: Planning) = viewModelScope.launch { db.plannings().supprimer(planning) }

    // --- Recherche BDPM ---
    val resultats = MutableStateFlow<List<SpecialiteBdpm>>(emptyList())
    val rechercheEnCours = MutableStateFlow(false)
    private var jobRecherche: Job? = null

    fun rechercher(texte: String) {
        jobRecherche?.cancel()
        jobRecherche = viewModelScope.launch {
            rechercheEnCours.value = true
            try {
                val base = bdpm.await()
                resultats.value = withContext(Dispatchers.IO) { base.rechercher(texte) }
            } finally {
                rechercheEnCours.value = false
            }
        }
    }

    suspend fun presentations(cis: Long): List<PresentationBdpm> {
        val base = bdpm.await()
        return withContext(Dispatchers.IO) { base.presentations(cis) }
    }

    // --- Actions ---
    fun ajouterMedicament(m: Medicament, stockInitial: Double) = viewModelScope.launch {
        val id = db.medicaments().inserer(m)
        if (stockInitial > 0) repo.ajouterStock(id, stockInitial, System.currentTimeMillis())
    }

    fun ajouterStock(medicamentId: Long, quantite: Double) = viewModelScope.launch {
        repo.ajouterStock(medicamentId, quantite, System.currentTimeMillis())
    }

    fun definirStock(medicamentId: Long, stock: Double) = viewModelScope.launch {
        repo.corrigerStock(medicamentId, stock, System.currentTimeMillis())
    }

    fun supprimer(m: Medicament) = viewModelScope.launch { db.medicaments().supprimer(m) }
}
