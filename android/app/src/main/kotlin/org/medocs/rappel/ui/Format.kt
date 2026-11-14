package org.medocs.rappel.ui

import java.util.Locale

private val UNITES_INVARIABLES = setOf("ml", "l", "g", "mg", "kg", "cl")

/** « 30 » ou « 0,5 » : pas de décimales inutiles, virgule française. */
fun formaterQuantite(q: Double): String =
    if (q == Math.rint(q)) q.toLong().toString()
    else String.format(Locale.FRANCE, "%.2f", q).trimEnd('0').trimEnd(',')

/** « 1 comprimé », « 30 comprimés », « 50 ml ». */
fun formaterAvecUnite(q: Double, unite: String): String {
    val u = if (q > 1 && unite.lowercase() !in UNITES_INVARIABLES && !unite.endsWith("s") && !unite.endsWith("x")) "${unite}s" else unite
    return "${formaterQuantite(q)} $u"
}

/** Saisie décimale tolérante (virgule ou point) ; null si invalide. */
fun lireDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
