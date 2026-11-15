package net.grandou.medocs.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Repository
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.domain.StatutJour
import net.grandou.medocs.rappels.Moteur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Bout en bout de l'onglet Historique : vrai ViewModel, vraie base de l'appareil. Les données créées sont supprimées à la fin
 * (suppression du médicament, en cascade) : à lancer sur un appareil de test.
 */
@RunWith(AndroidJUnit4::class)
class CalendrierViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = AppDatabase.get(app)
    private val repo = Repository(db)
    private val aujourdhui = LocalDate.now()

    private suspend fun <T> attendre(flux: kotlinx.coroutines.flow.Flow<T>, condition: (T) -> Boolean): T = withTimeout(10_000) { flux.first(condition) }

    @Test fun calendrierEtCorrectionDUnJourPasse() = runBlocking {
        val vm = AppViewModel(app)
        val medId = db.medicaments().inserer(Medicament(nom = "TEST CALENDRIER", unite = "comprimé"))
        try {
            repo.ajouterStock(medId, 100.0, System.currentTimeMillis())
            // Une prise par jour à 8 h, depuis 5 jours
            repo.enregistrerPlanning(
                Planning(medicamentId = medId, dateDebut = aujourdhui.minusDays(5)),
                listOf(PlanningHeure(planningId = 0, heure = LocalTime.of(8, 0), quantite = 1.0)),
            )
            val heureId = db.plannings().actifsDuMedicament(medId).single().heures.single().id
            val pris = aujourdhui.minusDays(3)
            val oublie = aujourdhui.minusDays(2)
            repo.prendre(medId, 1.0, System.currentTimeMillis(), heureId, pris, LocalTime.of(8, 0))

            // Calendrier : vert pour le jour pris, rouge pour le jour oublié, point avant le début du planning, gris pour demain
            val statutsPasses = attendre(vm.mois(YearMonth.from(pris))) { it[pris] == StatutJour.TOUT_PRIS }
            assertEquals(StatutJour.AUCUNE_PRISE, vm.mois(YearMonth.from(oublie)).first()[oublie])
            val avantDebut = aujourdhui.minusDays(6)
            assertEquals(StatutJour.SANS_PRESCRIPTION, vm.mois(YearMonth.from(avantDebut)).first()[avantDebut])
            val demain = aujourdhui.plusDays(1)
            assertEquals(StatutJour.A_VENIR_COUVERT, vm.mois(YearMonth.from(demain)).first()[demain])
            assertTrue(statutsPasses.size >= 28)

            // L'écran d'un jour passé affiche la prise ; corriger le jour oublié le fait passer au vert, daté à l'heure prévue
            val etat = attendre(vm.jour(oublie)) { it.lignes.size == 1 }
            assertEquals(oublie, etat.date)
            assertEquals(net.grandou.medocs.domain.EtatOccurrence.EN_RETARD, etat.lignes.single().etat)
            vm.prendre(etat.lignes.single())
            attendre(vm.mois(YearMonth.from(oublie))) { it[oublie] == StatutJour.TOUT_PRIS }
            val prise = db.prises().occurrence(heureId, oublie)!!
            assertEquals(StatutPrise.PRISE, prise.statut)
            assertEquals(LocalDateTime.of(oublie, LocalTime.of(8, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), prise.horodatage)
            assertEquals(98.0, db.stock().stock(medId), 1e-9) // 100 - la prise de départ - la correction

            // Annuler la correction rend le jour rouge et restitue le stock
            vm.annuler(prise)
            attendre(vm.mois(YearMonth.from(oublie))) { it[oublie] == StatutJour.AUCUNE_PRISE }
            assertEquals(99.0, db.stock().stock(medId), 1e-9)
        } finally {
            db.medicaments().tousActifs().filter { it.id == medId }.forEach { db.medicaments().supprimer(it) }
            Moteur.executer(app, traiter = false)
        }
    }
}
