package org.medocs.rappel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import org.medocs.rappel.data.Repository
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
