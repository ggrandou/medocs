package net.grandou.medocs.rappels

import android.app.Notification
import android.app.NotificationManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Repository
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.domain.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** Rappel persistant : réaffiché après balayage tant qu'il n'y a pas de réponse. Vraie base : à lancer sur un appareil de test. */
@RunWith(AndroidJUnit4::class)
class NotificationPersistanteTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = AppDatabase.get(app)
    private val repo = Repository(db)
    private val nm = app.getSystemService(NotificationManager::class.java)

    private fun active(id: Int) = nm.activeNotifications.firstOrNull { it.id == id }
    private fun attendre(cond: () -> Boolean): Boolean {
        repeat(40) { if (cond()) return true; Thread.sleep(250) }
        return cond()
    }

    @Test fun reaffichageApresBalayage() {
        val medId = runBlocking { db.medicaments().inserer(Medicament(nom = "TEST PERSISTANT", unite = "comprimé")) }
        try {
            val date = LocalDate.now()
            runBlocking {
                repo.enregistrerPlanning(
                    Planning(medicamentId = medId, dateDebut = date),
                    listOf(PlanningHeure(planningId = 0, heure = LocalTime.of(8, 0), quantite = 1.0)),
                )
            }
            val h = runBlocking { db.plannings().actifsDuMedicament(medId).single().heures.single() }
            val occ = Occurrence(h, date, medId)
            val id = Notifications.idOccurrence(occ)
            val med = runBlocking { db.medicaments().parId(medId)!! }

            Notifications.rappel(app, med, occ, false)
            val n = active(id)
            assertNotNull("notification affichée", n)
            assertTrue("persistante", n!!.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

            // Balayage simulé : retrait + deleteIntent => réaffichée
            val suppr = n.notification.deleteIntent
            nm.cancel(id)
            assertNull(active(id))
            suppr.send()
            assertTrue("réaffichée après balayage", attendre { active(id) != null })

            // « J'ai pris » : disparaît, et un balayage tardif ne la ramène pas
            active(id)!!.notification.actions[0].actionIntent.send()
            assertTrue("retirée après action", attendre { active(id) == null })
            assertEquals(StatutPrise.PRISE, runBlocking { db.prises().occurrence(h.id, date)!!.statut })
            suppr.send()
            Thread.sleep(1500)
            assertNull("pas de réaffichage après réponse", active(id))
        } finally {
            runBlocking { db.medicaments().supprimer(db.medicaments().parId(medId)!!) }
            nm.cancelAll()
        }
    }

    /** Prise créée après son heure (encore dans le délai) : l'ouverture de l'app doit afficher la notification. */
    @Test fun rattrapageDUnePriseEnRetard() {
        val maintenant = java.time.LocalDateTime.now()
        org.junit.Assume.assumeTrue(maintenant.toLocalTime().isAfter(LocalTime.of(0, 30)))
        val medId = runBlocking { db.medicaments().inserer(Medicament(nom = "TEST RATTRAPAGE", unite = "comprimé")) }
        try {
            runBlocking {
                repo.enregistrerPlanning(
                    Planning(medicamentId = medId, dateDebut = maintenant.toLocalDate()),
                    listOf(PlanningHeure(planningId = 0, heure = maintenant.toLocalTime().minusMinutes(20).withSecond(0).withNano(0), quantite = 1.0)),
                )
            }
            val h = runBlocking { db.plannings().actifsDuMedicament(medId).single().heures.single() }
            val id = Notifications.idOccurrence(Occurrence(h, maintenant.toLocalDate(), medId))
            assertNull(active(id))
            runBlocking { Moteur.executer(app) }
            assertNotNull("notification de rattrapage", active(id))
            active(id)!!.notification.actions[0].actionIntent.send()
            assertTrue(attendre { active(id) == null })
            runBlocking { Moteur.executer(app) }
            assertNull("pas de rattrapage après réponse", active(id))
        } finally {
            runBlocking { db.medicaments().supprimer(db.medicaments().parId(medId)!!) }
            nm.cancelAll()
        }
    }
}
