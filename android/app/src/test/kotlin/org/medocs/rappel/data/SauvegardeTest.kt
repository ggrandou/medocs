package org.medocs.rappel.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SauvegardeTest {
    private val donnees = Donnees(
        medicaments = listOf(
            Medicament(id = 1, nom = "DOLIPRANE 1000 mg, comprimé", cip13 = "3400935955838", substances = "PARACÉTAMOL 1000 mg", forme = "comprimé",
                unite = "comprimé", unitesParBoite = 8.0, seuilJours = 5, seuilUnites = 2.5, archive = true),
            Medicament(id = 2, nom = "Saisi à la main", unite = "ml"),
        ),
        plannings = listOf(Planning(id = 7, medicamentId = 1, recurrence = TypeRecurrence.JOURS_SEMAINE, joursSemaine = 21, intervalleJours = 1,
            dateDebut = LocalDate.of(2026, 11, 14), dateFin = LocalDate.of(2026, 12, 31), actif = false)),
        heures = listOf(PlanningHeure(id = 9, planningId = 7, heure = LocalTime.of(8, 30), quantite = 0.5)),
        prises = listOf(
            Prise(id = 3, medicamentId = 1, planningHeureId = 9, datePrevue = LocalDate.of(2026, 11, 14), heurePrevue = LocalTime.of(8, 30),
                statut = StatutPrise.REPORTEE, quantite = 0.5, horodatage = 1_000, reporteJusqua = 2_000),
            Prise(id = 4, medicamentId = 2, statut = StatutPrise.PRISE, quantite = 10.0, horodatage = 3_000),
        ),
        mouvements = listOf(
            StockMouvement(id = 1, medicamentId = 1, delta = 8.0, type = TypeMouvement.ACHAT, horodatage = 500),
            StockMouvement(id = 2, medicamentId = 2, delta = -10.0, type = TypeMouvement.PRISE, horodatage = 3_000, priseId = 4),
        ),
    )

    @Test fun jsonAllerRetourConserveTout() {
        assertEquals(donnees, SauvegardeJson.depuisJson(SauvegardeJson.versJson(donnees)))
    }

    @Test fun jsonVideEstValide() {
        val vide = Donnees(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(vide, SauvegardeJson.depuisJson(SauvegardeJson.versJson(vide)))
    }

    @Test fun refuseUnFichierQuiNEstPasUneSauvegarde() {
        for (texte in listOf("pas du json", "{}", """{"format":"autre","version":1}""", """{"format":"medocs","version":99}""")) {
            try { SauvegardeJson.depuisJson(texte); fail("accepté : $texte") } catch (_: SauvegardeInvalide) {}
        }
    }

    @Test fun refuseDesLiensCasses() {
        val casse = donnees.copy(plannings = listOf(donnees.plannings[0].copy(medicamentId = 99)))
        try { SauvegardeJson.depuisJson(SauvegardeJson.versJson(casse)); fail() } catch (e: SauvegardeInvalide) { assertTrue(e.message!!.contains("planning")) }
        val casse2 = donnees.copy(mouvements = listOf(donnees.mouvements[1].copy(priseId = 404)))
        try { SauvegardeJson.depuisJson(SauvegardeJson.versJson(casse2)); fail() } catch (_: SauvegardeInvalide) {}
    }

    @Test fun chiffrementAllerRetour() {
        val clair = "données de santé é à ✓".toByteArray()
        val chiffre = Chiffrement.chiffrer(clair, "mot de passe".toCharArray())
        assertArrayEquals(clair, Chiffrement.dechiffrer(chiffre, "mot de passe".toCharArray()))
    }

    @Test fun deuxChiffrementsDuMemeTexteDiffèrent() {
        val a = Chiffrement.chiffrer("x".toByteArray(), "p".toCharArray())
        val b = Chiffrement.chiffrer("x".toByteArray(), "p".toCharArray())
        assertTrue(!a.contentEquals(b)) // sel et IV aléatoires
    }

    @Test fun mauvaisMotDePasse() {
        val chiffre = Chiffrement.chiffrer("secret".toByteArray(), "bon".toCharArray())
        try { Chiffrement.dechiffrer(chiffre, "mauvais".toCharArray()); fail() } catch (_: MotDePasseIncorrect) {}
    }

    @Test fun modificationDuFichierDetectee() {
        val chiffre = Chiffrement.chiffrer("secret".toByteArray(), "bon".toCharArray())
        chiffre[chiffre.size - 1] = (chiffre[chiffre.size - 1].toInt() xor 1).toByte()
        try { Chiffrement.dechiffrer(chiffre, "bon".toCharArray()); fail() } catch (_: MotDePasseIncorrect) {}
    }

    @Test fun fichierEtrangerRefuse() {
        try { Chiffrement.dechiffrer("n'importe quoi de trop court".toByteArray(), "p".toCharArray()); fail() } catch (_: SauvegardeInvalide) {}
        try { Chiffrement.dechiffrer(ByteArray(200), "p".toCharArray()); fail() } catch (_: SauvegardeInvalide) {}
    }

    @Test fun lEnTeteEstAuthentifie() {
        val chiffre = Chiffrement.chiffrer("secret".toByteArray(), "bon".toCharArray())
        // Altérer le sel change la clé dérivée : le déchiffrement doit échouer proprement
        chiffre[8] = (chiffre[8].toInt() xor 1).toByte()
        try { Chiffrement.dechiffrer(chiffre, "bon".toCharArray()); fail() } catch (_: MotDePasseIncorrect) {}
    }
}
