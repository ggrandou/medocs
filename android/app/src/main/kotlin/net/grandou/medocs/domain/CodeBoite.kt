package net.grandou.medocs.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Ce qu'on sait lire sur la boîte : le CIP13 (clé de la base des médicaments) et, pour un Datamatrix GS1,
 * le lot et la date de péremption.
 */
data class CodeBoite(val cip13: String, val lot: String? = null, val peremption: LocalDate? = null) {
    companion object {
        private const val GS = '\u001d' // séparateur FNC1 des champs de longueur variable

        /** Clé de contrôle GS1 (EAN-8/13, GTIN-14) : somme pondérée 3-1 depuis la droite, hors dernier chiffre. */
        fun cleValide(chiffres: String): Boolean {
            if (chiffres.length < 8 || chiffres.any { it !in '0'..'9' }) return false
            val somme = chiffres.dropLast(1).reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
            return (10 - somme % 10) % 10 == chiffres.last() - '0'
        }

        /**
         * Interprète le texte lu par le scanner : EAN-13 nu, GTIN-14 nu, ou chaîne GS1 (« 01 »+GTIN, puis
         * péremption « 17 », lot « 10 », série « 21 »…), avec ou sans identifiant de symbologie (« ]d2 »).
         * Retourne null si ce n'est pas un code de produit valide : aucune confusion avec un autre code-barres.
         */
        fun depuisTexte(brut: String): CodeBoite? {
            val texte = brut.trim().replace(Regex("^\\][A-Za-z][0-9A-Za-z]"), "")
            if (Regex("\\d{13}").matches(texte)) return if (cleValide(texte)) CodeBoite(texte) else null
            if (Regex("\\d{14}").matches(texte)) return cipDepuisGtin14(texte)?.let { CodeBoite(it) }
            if (!texte.startsWith("01") || texte.length < 16) return null
            val gtin = texte.substring(2, 16)
            val cip = cipDepuisGtin14(gtin) ?: return null

            var lot: String? = null
            var peremption: LocalDate? = null
            var i = 16
            while (i + 2 <= texte.length) {
                when (texte.substring(i, i + 2)) {
                    "17" -> { // péremption AAMMJJ
                        if (i + 8 > texte.length) break
                        peremption = lireDate(texte.substring(i + 2, i + 8))
                        i += 8
                    }
                    "11", "13", "15" -> i += 8 // autres dates : longueur fixe, ignorées
                    "10", "21" -> { // lot, numéro de série : longueur variable, terminée par GS ou la fin
                        val fin = texte.indexOf(GS, i + 2).let { if (it < 0) texte.length else it }
                        if (texte.substring(i, i + 2) == "10") lot = texte.substring(i + 2, fin).takeIf { it.isNotEmpty() }
                        i = fin + 1
                    }
                    else -> break // champ inconnu : on garde ce qu'on a déjà compris
                }
            }
            return CodeBoite(cip, lot, peremption)
        }

        /** Un GTIN-14 de médicament français est « 0 » + EAN-13 : le CIP13 en est les 13 derniers chiffres. */
        private fun cipDepuisGtin14(gtin14: String): String? =
            if (cleValide(gtin14) && cleValide(gtin14.takeLast(13))) gtin14.takeLast(13) else null

        /** AAMMJJ ; JJ = 00 signifie « fin de mois » (usage GS1). */
        private fun lireDate(aammjj: String): LocalDate? = try {
            val annee = 2000 + aammjj.substring(0, 2).toInt()
            val mois = aammjj.substring(2, 4).toInt()
            val jour = aammjj.substring(4, 6).toInt()
            if (jour == 0) YearMonth.of(annee, mois).atEndOfMonth() else LocalDate.of(annee, mois, jour)
        } catch (e: Exception) { null }
    }
}
