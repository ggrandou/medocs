package net.grandou.medocs.ui

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
import net.grandou.medocs.bdpm.Bdpm
import net.grandou.medocs.bdpm.PresentationBdpm
import net.grandou.medocs.bdpm.SpecialiteBdpm
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.photos.Photos
import net.grandou.medocs.data.Chiffrement
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.MotDePasseIncorrect
import net.grandou.medocs.data.SauvegardeInvalide
import net.grandou.medocs.data.SauvegardeJson
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.Repository
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.rappels.Moteur
import net.grandou.medocs.rappels.Notifications
import net.grandou.medocs.domain.EntreeHistorique
import net.grandou.medocs.domain.fusionnerHistorique
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.Occurrence
import net.grandou.medocs.domain.etatOccurrence
import net.grandou.medocs.domain.occurrencesDuJour
import java.time.LocalDateTime
import java.time.ZoneId
import net.grandou.medocs.domain.ProjectionStock
import net.grandou.medocs.domain.projeterStock
import net.grandou.medocs.domain.stockFaible
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
    /** Prises manquées hier ou avant-hier (aujourd'hui, elles sont déjà dans [lignes]). */
    val manqueesRecentes: List<PonctuelleAujourdhui> = emptyList(),
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

    /** Supprime les fichiers photo que plus aucun médicament n'utilise (photo remplacée, retirée ou abandonnée). */
    private suspend fun nettoyerPhotos() = withContext(Dispatchers.IO) {
        Photos.nettoyer(getApplication(), db.medicaments().tous().mapNotNull { it.photoPath }.toSet())
    }

    init { viewModelScope.launch { nettoyerPhotos() } }

    /** Recale l'alarme système après toute modification des données (plannings, prises, stock). */
    private suspend fun recaler() = Moteur.executer(getApplication(), traiter = false)

    /** À l'ouverture de l'app : rattrape ce qui aurait dû être notifié (alarme perdue, arrêt forcé…). */
    fun rafraichirRappels() = viewModelScope.launch(Dispatchers.IO) { Moteur.executer(getApplication()) }

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
    }.let { base ->
        combine(
            base,
            jour.flatMapLatest { d -> db.prises().observerManquees(d.minusDays(2), d) },
            db.medicaments().observerAvecStock(),
        ) { etat, manquees, medicaments ->
            val parId = medicaments.associate { it.medicament.id to it.medicament }
            etat.copy(manqueesRecentes = manquees.mapNotNull { p -> parId[p.medicamentId]?.let { PonctuelleAujourdhui(p, it) } })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prendre(l: LigneAujourdhui) = viewModelScope.launch {
        val o = l.occurrence
        repo.prendre(l.medicament.id, o.quantite, System.currentTimeMillis(), o.planningHeure.id, o.date, o.heure)
        Notifications.annuler(getApplication(), o)
        recaler()
    }

    fun sauter(l: LigneAujourdhui) = viewModelScope.launch {
        val o = l.occurrence
        repo.sauter(l.medicament.id, o.quantite, System.currentTimeMillis(), o.planningHeure.id, o.date, o.heure)
        Notifications.annuler(getApplication(), o)
        recaler()
    }

    fun reporter(l: LigneAujourdhui, minutes: Int) = viewModelScope.launch {
        val o = l.occurrence
        val maintenant = System.currentTimeMillis()
        repo.reporter(l.medicament.id, o.quantite, maintenant, maintenant + minutes * 60_000L, o.planningHeure.id, o.date, o.heure)
        Notifications.annuler(getApplication(), o)
        recaler()
    }

    /** Annule la décision (prise, sautée, reportée) : l'occurrence redevient en attente et le stock est restitué. */
    fun annuler(prise: Prise) = viewModelScope.launch { repo.annulerPrise(prise.id); recaler() }

    fun priseHorsPlanning(m: Medicament, quantite: Double) = viewModelScope.launch {
        repo.prendre(m.id, quantite, System.currentTimeMillis())
        recaler()
    }

    // --- Sauvegarde chiffrée (export / import d'un fichier choisi par l'utilisateur) ---
    /** Résultat affiché à l'utilisateur après un export ou un import. */
    val messageSauvegarde = MutableStateFlow<String?>(null)

    fun exporter(uri: android.net.Uri, motDePasse: CharArray) = viewModelScope.launch(Dispatchers.IO) {
        messageSauvegarde.value = try {
            val base = repo.exporter()
            val contexte = getApplication<Application>()
            val photos = base.medicaments.mapNotNull { it.photoPath }.distinct().mapNotNull { nom ->
                Photos.lireOctets(contexte, nom)?.let { nom to java.util.Base64.getEncoder().encodeToString(it) }
            }.toMap()
            val donnees = base.copy(photos = photos)
            val fichier = Chiffrement.chiffrer(SauvegardeJson.versJson(donnees).toByteArray(Charsets.UTF_8), motDePasse)
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(fichier) }
            "Sauvegarde enregistrée : ${donnees.medicaments.size} médicament(s), ${donnees.photos.size} photo(s), ${donnees.prises.size} ligne(s) de journal."
        } catch (e: Exception) {
            "Échec de la sauvegarde : ${e.message ?: e.javaClass.simpleName}"
        } finally { motDePasse.fill('\u0000') }
    }

    fun importer(uri: android.net.Uri, motDePasse: CharArray) = viewModelScope.launch(Dispatchers.IO) {
        messageSauvegarde.value = try {
            val octets = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val donnees = SauvegardeJson.depuisJson(String(Chiffrement.dechiffrer(octets, motDePasse), Charsets.UTF_8))
            // Les photos sont validées et écrites AVANT la base : une image illisible fait échouer la restauration sans rien changer
            val contexte = getApplication<Application>()
            donnees.photos.forEach { (nom, b64) ->
                val octets = try { java.util.Base64.getDecoder().decode(b64) } catch (e: IllegalArgumentException) { throw SauvegardeInvalide("Une photo de la sauvegarde est corrompue") }
                Photos.ecrireOctets(contexte, nom, octets)
            }
            repo.remplacerPar(donnees)
            recaler()
            nettoyerPhotos()
            "Données restaurées : ${donnees.medicaments.size} médicament(s), ${donnees.photos.size} photo(s), ${donnees.prises.size} ligne(s) de journal."
        } catch (e: MotDePasseIncorrect) {
            "Mot de passe incorrect, ou fichier modifié. Rien n'a été changé."
        } catch (e: SauvegardeInvalide) {
            "Fichier refusé : ${e.message}. Rien n'a été changé."
        } catch (e: Exception) {
            "Échec de la restauration : ${e.message ?: e.javaClass.simpleName}. Rien n'a été changé."
        } finally { motDePasse.fill('\u0000') }
    }

    // --- Édition, archives, historique ---
    val archives: StateFlow<List<Medicament>> = db.medicaments().observerArchives()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun modifierMedicament(m: Medicament) = viewModelScope.launch { db.medicaments().modifier(m); recaler(); nettoyerPhotos() }

    fun historique(medicamentId: Long): Flow<List<EntreeHistorique>> = combine(
        db.prises().observerJournal(medicamentId),
        db.stock().observerMouvements(medicamentId),
    ) { prises, mouvements -> fusionnerHistorique(prises, mouvements) }

    // --- Plannings ---
    fun plannings(medicamentId: Long): Flow<List<PlanningAvecHeures>> = db.plannings().observerDuMedicament(medicamentId)

    fun enregistrerPlanning(planning: Planning, heures: List<PlanningHeure>) = viewModelScope.launch {
        repo.enregistrerPlanning(planning, heures)
        recaler()
    }

    fun supprimerPlanning(planning: Planning) = viewModelScope.launch { db.plannings().supprimer(planning); recaler() }

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

    suspend fun trouverParCip13(cip13: String): net.grandou.medocs.bdpm.TrouvailleBdpm? {
        val base = bdpm.await()
        return withContext(Dispatchers.IO) { base.parCip13(cip13) }
    }

    suspend fun presentations(cis: Long): List<PresentationBdpm> {
        val base = bdpm.await()
        return withContext(Dispatchers.IO) { base.presentations(cis) }
    }

    // --- Actions ---
    fun ajouterMedicament(m: Medicament, stockInitial: Double) = viewModelScope.launch {
        val id = db.medicaments().inserer(m)
        if (stockInitial > 0) repo.ajouterStock(id, stockInitial, System.currentTimeMillis())
        recaler()
        nettoyerPhotos()
    }

    fun ajouterStock(medicamentId: Long, quantite: Double) = viewModelScope.launch {
        repo.ajouterStock(medicamentId, quantite, System.currentTimeMillis())
        recaler()
    }

    fun definirStock(medicamentId: Long, stock: Double) = viewModelScope.launch {
        repo.corrigerStock(medicamentId, stock, System.currentTimeMillis())
        recaler()
    }

    fun supprimer(m: Medicament) = viewModelScope.launch { db.medicaments().supprimer(m); recaler(); nettoyerPhotos() }
}
