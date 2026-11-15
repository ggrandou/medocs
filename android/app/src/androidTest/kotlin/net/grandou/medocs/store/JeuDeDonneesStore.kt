package net.grandou.medocs.store

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.grandou.medocs.data.AppDatabase
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Repository
import net.grandou.medocs.data.StatutPrise
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Données FICTIVES pour les captures d'écran du Play Store. Ne s'exécute que sur demande (`-e jeu_store 1`) et NE nettoie PAS :
 * à lancer uniquement sur un appareil de test vierge (S8), jamais sur un téléphone qui porte de vraies données.
 */
@RunWith(AndroidJUnit4::class)
class JeuDeDonneesStore {
    @Test fun creer() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("jeu_store") == "1")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.get(app)
        val repo = Repository(db)
        val aujourdhui = LocalDate.now()
        val debut = aujourdhui.minusDays(24)
        val zone = ZoneId.systemDefault()
        fun instant(d: LocalDate, h: LocalTime) = d.atTime(h).plusMinutes(3).atZone(zone).toInstant().toEpochMilli()

        // nom, unité, boîte, stock voulu à la fin, heures
        data class Med(val nom: String, val substances: String, val forme: String, val unite: String, val boite: Double, val stock: Double, val heures: List<LocalTime>)
        val meds = listOf(
            Med("Metformine 850 mg", "METFORMINE 850 mg", "comprimé pelliculé", "comprimé", 60.0, 21.0, listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
            Med("Vitamine D3 1000 UI", "CHOLÉCALCIFÉROL", "gélule", "gélule", 30.0, 45.0, listOf(LocalTime.of(8, 0))),
            Med("Amlodipine 5 mg", "AMLODIPINE 5 mg", "comprimé", "comprimé", 30.0, 6.0, listOf(LocalTime.of(11, 0))),
            Med("Oméprazole 20 mg", "OMÉPRAZOLE 20 mg", "gélule gastro-résistante", "gélule", 28.0, 34.0, listOf(LocalTime.of(13, 0))),
        ) + if (InstrumentationRegistry.getArguments().getString("notifs_extra") == "1") listOf(
            // Deux prises de plus en retard (10 h 30 et 11 h 30) pour remplir le volet de notifications
            Med("Lévothyroxine 75 µg", "LÉVOTHYROXINE SODIQUE 75 µg", "comprimé sécable", "comprimé", 30.0, 25.0, listOf(LocalTime.of(10, 30))),
            Med("Atorvastatine 20 mg", "ATORVASTATINE 20 mg", "comprimé pelliculé", "comprimé", 30.0, 25.0, listOf(LocalTime.of(11, 30))),
        ) else emptyList()
        for ((n, m) in meds.withIndex()) {
            val id = db.medicaments().inserer(Medicament(nom = m.nom, substances = m.substances, forme = m.forme, unite = m.unite, unitesParBoite = m.boite))
            repo.ajouterStock(id, 500.0, instant(debut, LocalTime.of(7, 0)))
            repo.enregistrerPlanning(
                Planning(medicamentId = id, dateDebut = debut),
                m.heures.map { PlanningHeure(planningId = 0, heure = it, quantite = 1.0) },
            )
            val heures = db.plannings().actifsDuMedicament(id).single().heures
            var jour = debut
            while (!jour.isAfter(aujourdhui)) {
                for (h in heures) {
                    val enCours = jour == aujourdhui
                    // Aujourd'hui : seules les prises de 8 h sont faites (11 h reste en retard, 13 h et 20 h à venir)
                    if (enCours && h.heure != LocalTime.of(8, 0)) continue
                    val graine = (jour.toEpochDay() * 7 + n * 3 + h.heure.hour) % 17
                    val t = instant(jour, h.heure)
                    when {
                        !enCours && jour.isBefore(aujourdhui.minusDays(4)) && graine == 0L -> repo.sauter(id, 1.0, t, h.id, jour, h.heure, StatutPrise.MANQUEE)
                        else -> repo.prendre(id, 1.0, t, h.id, jour, h.heure)
                    }
                }
                jour = jour.plusDays(1)
            }
            // Stock final voulu
            repo.ajouterStock(id, m.stock - db.stock().stock(id), System.currentTimeMillis())
        }
        Unit
    }
}
