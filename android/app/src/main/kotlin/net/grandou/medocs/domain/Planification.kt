package net.grandou.medocs.domain

import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.TypeRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Une prise attendue : une heure d'un planning, à une date donnée. */
data class Occurrence(val planningHeure: PlanningHeure, val date: LocalDate, val medicamentId: Long) {
    val heure: LocalTime get() = planningHeure.heure
    val quantite: Double get() = planningHeure.quantite
    val cle: Pair<Long, LocalDate> get() = planningHeure.id to date
}

fun masqueJours(vararg jours: DayOfWeek): Int = jours.fold(0) { m, j -> m or (1 shl (j.value - 1)) }

/** Le planning prévoit-il des prises ce jour-là ? */
fun Planning.survientLe(date: LocalDate): Boolean {
    if (!actif || date.isBefore(dateDebut) || (dateFin != null && date.isAfter(dateFin))) return false
    return when (recurrence) {
        TypeRecurrence.QUOTIDIEN -> true
        TypeRecurrence.JOURS_SEMAINE -> joursSemaine and (1 shl (date.dayOfWeek.value - 1)) != 0
        TypeRecurrence.TOUS_LES_N_JOURS ->
            ChronoUnit.DAYS.between(dateDebut, date) % intervalleJours.coerceAtLeast(1) == 0L
    }
}

/** Occurrences d'un jour pour un ensemble de plannings, triées par heure. */
fun occurrencesDuJour(plannings: List<PlanningAvecHeures>, date: LocalDate): List<Occurrence> =
    plannings.filter { it.planning.survientLe(date) }
        .flatMap { p -> p.heures.map { Occurrence(it, date, p.planning.medicamentId) } }
        .sortedBy { it.heure }

/** Prochaine occurrence strictement après (date, heure), dans les [horizonJours] jours, ou null. */
fun prochaineOccurrence(
    plannings: List<PlanningAvecHeures>,
    apresDate: LocalDate,
    apresHeure: LocalTime,
    horizonJours: Int = 366,
): Occurrence? {
    for (i in 0..horizonJours) {
        val jour = apresDate.plusDays(i.toLong())
        val trouvee = occurrencesDuJour(plannings, jour).firstOrNull { i > 0 || it.heure.isAfter(apresHeure) }
        if (trouvee != null) return trouvee
    }
    return null
}

sealed interface ProjectionStock {
    /** Aucun planning actif : pas de projection possible. */
    data object SansPlanning : ProjectionStock

    /** Le stock couvre le planning jusqu'à sa fin (ou au-delà de l'horizon de calcul). */
    data object Suffisant : ProjectionStock

    /** Première occurrence qui ne pourra pas être honorée ; [joursRestants] = 0 si dès aujourd'hui. */
    data class Rupture(val date: LocalDate, val joursRestants: Int) : ProjectionStock
}

/**
 * Simule jour par jour la consommation prévue à partir d'aujourd'hui.
 * [traitees] : occurrences d'aujourd'hui (ou plus tard) déjà prises/sautées, à ne pas recompter.
 * Les occurrences d'aujourd'hui non traitées comptent comme à venir (même si leur heure est passée).
 */
fun projeterStock(
    stock: Double,
    plannings: List<PlanningAvecHeures>,
    aujourdhui: LocalDate,
    traitees: Set<Pair<Long, LocalDate>> = emptySet(),
    horizonJours: Int = 366,
): ProjectionStock {
    if (plannings.none { it.planning.actif }) return ProjectionStock.SansPlanning
    var reste = stock
    for (i in 0..horizonJours) {
        val jour = aujourdhui.plusDays(i.toLong())
        for (occ in occurrencesDuJour(plannings, jour)) {
            if (occ.cle in traitees) continue
            reste -= occ.quantite
            if (reste < -EPS) return ProjectionStock.Rupture(jour, i)
        }
    }
    return ProjectionStock.Suffisant
}

private const val EPS = 1e-9

/** Faut-il alerter l'utilisateur ? Seuil en jours de planning et/ou seuil en unités. */
fun stockFaible(medicament: Medicament, stock: Double, projection: ProjectionStock): Boolean {
    val sousSeuilUnites = medicament.seuilUnites?.let { stock <= it + EPS } ?: false
    val sousSeuilJours = projection is ProjectionStock.Rupture && projection.joursRestants <= medicament.seuilJours
    return sousSeuilUnites || sousSeuilJours
}
