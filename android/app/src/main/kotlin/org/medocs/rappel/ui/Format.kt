package org.medocs.rappel.ui

import org.medocs.rappel.data.PlanningAvecHeures
import org.medocs.rappel.data.TypeRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val UNITES_INVARIABLES = setOf("ml", "l", "g", "mg", "kg", "cl")

/** « 30 » ou « 0,5 » : pas de décimales inutiles, virgule française. */
fun formaterQuantite(q: Double): String =
    if (q == Math.rint(q)) q.toLong().toString()
    else String.format(Locale.FRANCE, "%.2f", q).trimEnd('0').trimEnd(',')

/** « 1 comprimé », « 30 comprimés », « 50 ml ». */
fun formaterAvecUnite(q: Double, unite: String): String {
    val u = if (Math.abs(q) > 1 && unite.lowercase() !in UNITES_INVARIABLES && !unite.endsWith("s") && !unite.endsWith("x")) "${unite}s" else unite
    return "${formaterQuantite(q)} $u"
}

/** Saisie décimale tolérante (virgule ou point) ; null si invalide. */
fun lireDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private val FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
fun formaterDate(d: LocalDate): String = d.format(FORMAT_DATE)

fun nomJour(j: DayOfWeek): String = j.getDisplayName(TextStyle.SHORT, Locale.FRANCE)

/** « Tous les jours : 08:00 (1 comprimé), 20:00 (1 comprimé) — à partir du 16/11/2026 ». */
fun resumePlanning(p: PlanningAvecHeures, unite: String): String {
    val rythme = when (p.planning.recurrence) {
        TypeRecurrence.QUOTIDIEN -> "Tous les jours"
        TypeRecurrence.JOURS_SEMAINE -> DayOfWeek.entries
            .filter { p.planning.joursSemaine and (1 shl (it.value - 1)) != 0 }.joinToString(", ") { nomJour(it) }
            .ifEmpty { "Aucun jour" }
        TypeRecurrence.TOUS_LES_N_JOURS -> if (p.planning.intervalleJours == 1) "Tous les jours"
            else "Tous les ${p.planning.intervalleJours} jours"
    }
    val heures = p.heures.sortedBy { it.heure }.joinToString(", ") { "${it.heure} (${formaterAvecUnite(it.quantite, unite)})" }
    val periode = "du ${formaterDate(p.planning.dateDebut)}" + (p.planning.dateFin?.let { " au ${formaterDate(it)}" } ?: "")
    return "$rythme : $heures — $periode" + if (p.planning.actif) "" else " (en pause)"
}
