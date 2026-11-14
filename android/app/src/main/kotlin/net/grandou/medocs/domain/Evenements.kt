package net.grandou.medocs.domain

import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Ce qui doit déclencher le réveil de l'application à un instant donné. */
sealed interface Evenement {
    val instant: Long
    val occurrence: Occurrence
}

/** Notifier l'utilisateur (à l'heure prévue, ou à l'heure du report). */
data class Rappel(override val occurrence: Occurrence, override val instant: Long, val reporte: Boolean) : Evenement

/** L'occurrence n'a pas reçu de réponse à temps : la marquer « manquée ». */
data class Echeance(override val occurrence: Occurrence, override val instant: Long) : Evenement

fun instantDe(date: LocalDate, heure: java.time.LocalTime, zone: ZoneId): Long =
    LocalDateTime.of(date, heure).atZone(zone).toInstant().toEpochMilli()

/**
 * Événements dans l'intervalle ]apres, jusqua] (epoch ms), triés par instant.
 * [prises] : lignes du journal des occurrences planifiées (au moins celles des derniers jours et
 * toutes les REPORTEE). Une occurrence déjà prise/sautée/manquée ne génère rien.
 */
fun evenementsEntre(
    plannings: List<PlanningAvecHeures>,
    prises: List<Prise>,
    apres: Long,
    jusqua: Long,
    zone: ZoneId,
    delaiManqueMs: Long,
): List<Evenement> {
    if (jusqua <= apres) return emptyList()
    val parCle = prises.filter { it.planningHeureId != null && it.datePrevue != null }
        .associateBy { it.planningHeureId!! to it.datePrevue!! }
    // Une échéance peut tomber dans la fenêtre bien après l'heure de l'occurrence (délai + report éventuel).
    val premier = Instant.ofEpochMilli(apres - delaiManqueMs).atZone(zone).toLocalDate().minusDays(1)
    val dernier = Instant.ofEpochMilli(jusqua).atZone(zone).toLocalDate()
    val resultat = mutableListOf<Evenement>()
    var jour = premier
    while (!jour.isAfter(dernier)) {
        for (occ in occurrencesDuJour(plannings, jour)) {
            val prise = parCle[occ.cle]
            val debut = when (prise?.statut) {
                null -> instantDe(jour, occ.heure, zone)
                StatutPrise.REPORTEE -> prise.reporteJusqua ?: instantDe(jour, occ.heure, zone)
                else -> continue
            }
            if (debut in (apres + 1)..jusqua) resultat += Rappel(occ, debut, reporte = prise != null)
            val echeance = debut + delaiManqueMs
            if (echeance in (apres + 1)..jusqua) resultat += Echeance(occ, echeance)
        }
        jour = jour.plusDays(1)
    }
    return resultat.sortedBy { it.instant }
}

/** Premier événement strictement après [apres], en cherchant jour après jour jusqu'à l'horizon. */
fun prochainEvenement(
    plannings: List<PlanningAvecHeures>,
    prises: List<Prise>,
    apres: Long,
    zone: ZoneId,
    delaiManqueMs: Long,
    horizonJours: Int = 400,
): Evenement? {
    val jourMs = 24L * 3600 * 1000
    for (i in 0 until horizonJours) {
        val de = apres + i * jourMs
        evenementsEntre(plannings, prises, de, de + jourMs, zone, delaiManqueMs).firstOrNull()?.let { return it }
    }
    return null
}
