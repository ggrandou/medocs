package net.grandou.medocs.domain

import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.data.TypeRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class CalendrierTest {
    private val mois = YearMonth.of(2026, 11)
    private val debut = LocalDate.of(2026, 11, 1)
    private val aujourdhui = LocalDate.of(2026, 11, 15)
    private val midi = LocalDateTime.of(aujourdhui, LocalTime.of(12, 0))

    // Médicament 1 : 8h et 20h chaque jour, 1 unité chacune. Heures d'id 10 (8h) et 11 (20h).
    private val quotidien = PlanningAvecHeures(
        Planning(id = 1, medicamentId = 1, recurrence = TypeRecurrence.QUOTIDIEN, dateDebut = debut),
        listOf(PlanningHeure(10, 1, LocalTime.of(8, 0), 1.0), PlanningHeure(11, 1, LocalTime.of(20, 0), 1.0)),
    )

    private fun prise(heureId: Long, jour: Int, statut: StatutPrise, med: Long = 1) = Prise(
        medicamentId = med, planningHeureId = heureId, datePrevue = LocalDate.of(2026, 11, jour), statut = statut, quantite = 1.0, horodatage = 0,
    )

    private fun statuts(prises: List<Prise> = emptyList(), stock: Double = 1000.0, plannings: List<PlanningAvecHeures> = listOf(quotidien), maintenant: LocalDateTime = midi) =
        statutsDuMois(mois, maintenant, plannings, prises, mapOf(1L to stock), traitees = emptySet())

    private fun jour(n: Int) = LocalDate.of(2026, 11, n)

    @Test fun toutPrisEstVert() {
        val s = statuts(listOf(prise(10, 3, StatutPrise.PRISE), prise(11, 3, StatutPrise.PRISE)))
        assertEquals(StatutJour.TOUT_PRIS, s[jour(3)])
    }

    @Test fun uneSauteeOuUnOubliDonneOrange() {
        val s = statuts(listOf(prise(10, 3, StatutPrise.PRISE), prise(11, 3, StatutPrise.SAUTEE), prise(10, 4, StatutPrise.PRISE), prise(11, 4, StatutPrise.MANQUEE)))
        assertEquals(StatutJour.PARTIEL, s[jour(3)])
        assertEquals(StatutJour.PARTIEL, s[jour(4)])
    }

    @Test fun uneManqueeOuReporteeNEstPasUnePrise() {
        val s = statuts(listOf(prise(10, 3, StatutPrise.PRISE), prise(11, 3, StatutPrise.MANQUEE), prise(10, 4, StatutPrise.REPORTEE)))
        assertEquals(StatutJour.PARTIEL, s[jour(3)])
        assertEquals(StatutJour.AUCUNE_PRISE, s[jour(4)])
    }

    @Test fun lesJoursPassesSansLigneDansLeJournalSontUnPointSaufLaVeille() {
        val s = statuts()
        assertEquals(StatutJour.SANS_PRESCRIPTION, s[jour(2)]) // rien d'enregistré : le moteur n'a rien noté ce jour-là
        assertEquals(StatutJour.SANS_PRESCRIPTION, s[jour(14)]) // pas de ligne : on ne déduit plus rien des plannings (à 12 h, tout est déjà noté)
    }

    @Test fun uneOccurrencePasEncoreNoteeParLeMoteurCompteCommeNonPrise() {
        // 00 h 30 le 15 : la prise de 20 h du 14 date de 4 h 30 (notée manquée depuis longtemps) mais celle de 23 h 30, elle, n'a pas 2 h
        val tard = PlanningAvecHeures(quotidien.planning.copy(id = 5), listOf(PlanningHeure(50, 5, LocalTime.of(23, 30), 1.0)))
        val minuit30 = LocalDateTime.of(aujourdhui, LocalTime.of(0, 30))
        assertEquals(StatutJour.AUCUNE_PRISE, statuts(plannings = listOf(tard), maintenant = minuit30)[jour(14)])
        // 2 h plus tard le moteur l'a notée : sans ligne dans le journal, il ne reste rien à compter
        assertEquals(StatutJour.SANS_PRESCRIPTION, statuts(plannings = listOf(tard), maintenant = minuit30.plusHours(2).plusMinutes(1))[jour(14)])
    }

    @Test fun lePasseNeDependPasDesPlannings() {
        val journal = listOf(
            prise(10, 3, StatutPrise.PRISE), prise(11, 3, StatutPrise.SAUTEE), // orange
            prise(10, 5, StatutPrise.PRISE), prise(11, 5, StatutPrise.PRISE), // vert
            prise(10, 6, StatutPrise.MANQUEE), prise(11, 6, StatutPrise.MANQUEE), // rouge
        )
        val avecPlanning = statuts(journal)
        // Planning modifié (autres heures), supprimé, ou détaché des lignes : le passé reste identique
        val autrePlanning = PlanningAvecHeures(quotidien.planning.copy(id = 9), listOf(PlanningHeure(90, 9, LocalTime.of(21, 30), 3.0)))
        for (plannings in listOf(listOf(autrePlanning), emptyList())) {
            val s = statuts(journal, plannings = plannings)
            for (j in 3..6) assertEquals("jour $j", avecPlanning[jour(j)], s[jour(j)])
        }
        assertEquals(StatutJour.PARTIEL, avecPlanning[jour(3)])
        assertEquals(StatutJour.TOUT_PRIS, avecPlanning[jour(5)])
        assertEquals(StatutJour.AUCUNE_PRISE, avecPlanning[jour(6)])
    }

    @Test fun uneLigneDetacheeDUnPlanningCompteToujours() {
        val detachees = listOf(prise(10, 3, StatutPrise.PRISE).copy(planningHeureId = null), prise(11, 3, StatutPrise.PRISE).copy(planningHeureId = null))
        assertEquals(StatutJour.TOUT_PRIS, statuts(detachees, plannings = emptyList())[jour(3)])
    }

    @Test fun lesPrisesPonctuellesSansDatePrevueNeFontPasUnePrescription() {
        val ponctuelle = Prise(medicamentId = 1, statut = StatutPrise.PRISE, quantite = 1.0, horodatage = 0)
        assertEquals(StatutJour.SANS_PRESCRIPTION, statuts(listOf(ponctuelle), plannings = emptyList())[jour(3)])
    }

    @Test fun seulesLesLignesDuJournalComptentPourUnJourPasse() {
        // La prise de 8 h est notée prise ; il n'y a aucune ligne pour celle de 20 h : on ne la déduit pas du planning -> tout pris
        assertEquals(StatutJour.TOUT_PRIS, statuts(listOf(prise(10, 13, StatutPrise.PRISE)))[jour(13)])
    }

    @Test fun toutesSauteesDonneRouge() {
        val s = statuts(listOf(prise(10, 5, StatutPrise.SAUTEE), prise(11, 5, StatutPrise.SAUTEE)))
        assertEquals(StatutJour.AUCUNE_PRISE, s[jour(5)])
    }

    @Test fun lesJoursSansPrescriptionSontUnPoint() {
        val lundis = PlanningAvecHeures(
            Planning(id = 2, medicamentId = 1, recurrence = TypeRecurrence.JOURS_SEMAINE, joursSemaine = masqueJours(DayOfWeek.MONDAY), dateDebut = debut),
            listOf(PlanningHeure(20, 2, LocalTime.of(9, 0), 1.0)),
        )
        val s = statuts(listOf(prise(20, 2, StatutPrise.MANQUEE)), plannings = listOf(lundis))
        assertEquals(StatutJour.SANS_PRESCRIPTION, s[jour(3)]) // mardi passé
        assertEquals(StatutJour.SANS_PRESCRIPTION, s[jour(17)]) // mardi futur
        assertEquals(StatutJour.AUCUNE_PRISE, s[jour(2)]) // lundi passé non pris
        assertEquals(StatutJour.A_VENIR_COUVERT, s[jour(16)]) // lundi futur
    }

    @Test fun sansAucunPlanningToutEstUnPoint() {
        assertTrue(statuts(plannings = emptyList()).values.all { it == StatutJour.SANS_PRESCRIPTION })
    }

    @Test fun avantLeDebutDuPlanningCEstUnPoint() {
        val tard = PlanningAvecHeures(quotidien.planning.copy(dateDebut = jour(10)), quotidien.heures)
        val s = statuts(listOf(prise(10, 10, StatutPrise.MANQUEE)), plannings = listOf(tard))
        assertEquals(StatutJour.SANS_PRESCRIPTION, s[jour(9)])
        assertEquals(StatutJour.AUCUNE_PRISE, s[jour(10)])
    }

    @Test fun lesJoursFutursSontGrisTantQueLeStockLaisse() {
        // Aujourd'hui (15) 2 prises + chaque jour suivant 2 : 10 unités couvrent le 15, 16, 17, 18, 19 (2 x 5)
        val s = statuts(stock = 10.0)
        assertEquals(StatutJour.A_VENIR_COUVERT, s[jour(19)])
        assertEquals(StatutJour.A_VENIR_SANS_STOCK, s[jour(20)])
        assertEquals(StatutJour.A_VENIR_SANS_STOCK, s[jour(30)])
    }

    @Test fun unJourPartiellementCouvertEstBlanc() {
        // 9 unités : le 19 n'a plus qu'une unité pour deux prises
        val s = statuts(stock = 9.0)
        assertEquals(StatutJour.A_VENIR_COUVERT, s[jour(18)])
        assertEquals(StatutJour.A_VENIR_SANS_STOCK, s[jour(19)])
    }

    @Test fun lesPrisesDejaFaitesAujourdhuiNeSontPasRecomptees() {
        // 10 unités, 15 : matin et soir déjà traités (comptés dans le stock restant) -> le stock couvre alors jusqu'au 20
        val traitees = setOf(10L to aujourdhui, 11L to aujourdhui)
        val s = statutsDuMois(mois, midi, listOf(quotidien), emptyList(), mapOf(1L to 10.0), traitees)
        assertEquals(StatutJour.A_VENIR_COUVERT, s[jour(20)])
        assertEquals(StatutJour.A_VENIR_SANS_STOCK, s[jour(21)])
    }

    @Test fun aujourdhuiAvantLaPremierePriseEstUneJourneeEnCours() {
        val tot = LocalDateTime.of(aujourdhui, LocalTime.of(6, 0))
        assertEquals(StatutJour.A_VENIR_COUVERT, statuts(maintenant = tot)[aujourdhui])
    }

    @Test fun aujourdhuiMatinPrisSoirAVenirEstEnCours() {
        val s = statuts(listOf(prise(10, 15, StatutPrise.PRISE)))
        assertEquals(StatutJour.A_VENIR_COUVERT, s[aujourdhui])
    }

    @Test fun aujourdhuiToutPrisEstVertMemeSiLaSoireeEstTerminee() {
        val soir = LocalDateTime.of(aujourdhui, LocalTime.of(21, 0))
        val s = statuts(listOf(prise(10, 15, StatutPrise.PRISE), prise(11, 15, StatutPrise.PRISE)), maintenant = soir)
        assertEquals(StatutJour.TOUT_PRIS, s[aujourdhui])
    }

    @Test fun aujourdhuiPriseDuMatinOubliee() {
        // Midi : la prise de 8h n'est pas notée (en retard), celle de 20h est à venir -> rien de pris pour l'instant : rouge
        assertEquals(StatutJour.AUCUNE_PRISE, statuts()[aujourdhui])
        // Même cas, mais la prise de 8h est sautée et rien d'autre n'est fait : toujours aucune prise
        assertEquals(StatutJour.AUCUNE_PRISE, statuts(listOf(prise(10, 15, StatutPrise.SAUTEE)))[aujourdhui])
    }

    @Test fun lesJoursDuMoisSontTousPresents() {
        val s = statuts()
        assertEquals(30, s.size)
        assertEquals(debut, s.keys.min())
        assertEquals(LocalDate.of(2026, 11, 30), s.keys.max())
    }

    @Test fun unMoisPasseOuFuturLointainFonctionne() {
        val passe = statutsDuMois(YearMonth.of(2026, 10), midi, listOf(quotidien), emptyList(), mapOf(1L to 5.0), emptySet())
        assertEquals(StatutJour.SANS_PRESCRIPTION, passe[LocalDate.of(2026, 10, 20)]) // avant le début du planning
        val futur = statutsDuMois(YearMonth.of(2027, 3), midi, listOf(quotidien), emptyList(), mapOf(1L to 5.0), emptySet())
        assertEquals(StatutJour.A_VENIR_SANS_STOCK, futur[LocalDate.of(2027, 3, 1)])
    }

    @Test fun lesStocksSeSimulentMedicamentParMedicament() {
        val autre = PlanningAvecHeures(
            Planning(id = 3, medicamentId = 2, recurrence = TypeRecurrence.QUOTIDIEN, dateDebut = debut),
            listOf(PlanningHeure(30, 3, LocalTime.of(9, 0), 1.0)),
        )
        // Médicament 1 largement approvisionné, médicament 2 : 2 unités seulement (le 15 et le 16)
        val manque = joursSansStock(mapOf(1L to 1000.0, 2L to 2.0), listOf(quotidien, autre), aujourdhui, LocalDate.of(2026, 11, 20))
        assertEquals(setOf(jour(17), jour(18), jour(19), jour(20)), manque)
    }

    @Test fun sansStockDuToutToutEstEnManque() {
        val manque = joursSansStock(mapOf(1L to 0.0), listOf(quotidien), aujourdhui, jour(17))
        assertEquals(setOf(jour(15), jour(16), jour(17)), manque)
    }
}
