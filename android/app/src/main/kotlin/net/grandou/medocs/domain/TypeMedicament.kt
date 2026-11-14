package net.grandou.medocs.domain

import java.text.Normalizer

/** Aspect d'un médicament, pour choisir son icône quand il n'a pas de photo. */
enum class TypeMedicament { COMPRIME, GELULE, LIQUIDE, GOUTTES, INJECTION, SACHET, TUBE, SUPPOSITOIRE, SPRAY, GENERIQUE }

/**
 * Déduit l'aspect d'un médicament de sa forme pharmaceutique (celle de la base : « comprimé pelliculé », « solution buvable en gouttes »…),
 * puis de son unité de prise, puis de son nom (saisie manuelle). Les règles sont ordonnées : « poudre pour solution injectable » est une
 * injection avant d'être une poudre. Elles couvrent plus de 98 % des spécialités de la base (207 sur 11 897 restent génériques).
 */
object TypesMedicament {
    private val regles: List<Pair<TypeMedicament, Regex>> = listOf(
        TypeMedicament.INJECTION to Regex("injectable|perfusion|seringue|stylo|injection"),
        TypeMedicament.SPRAY to Regex("inhalation|aerosol|spray|pulverisation|nebuli|inhalateur|\\bdose\\b|bouffee"),
        TypeMedicament.GOUTTES to Regex("collyre|goutte|bain d.?oeil"),
        TypeMedicament.SUPPOSITOIRE to Regex("suppositoire|ovule"),
        TypeMedicament.TUBE to Regex("pommade|creme|\\bgel\\b|lotion|\\bpate\\b|shampooing|mousse|baton|emulsion pour application"),
        TypeMedicament.SACHET to Regex("sachet|poudre|granule"),
        TypeMedicament.GELULE to Regex("gelule|capsule"),
        TypeMedicament.COMPRIME to Regex("comprime|pastille|dragee|gomme|lyophilisat|cachet|tablette|pilule|\\bcp\\b"),
        TypeMedicament.LIQUIDE to Regex("solution|suspension|sirop|buvable|elixir|liquide|\\bml\\b|cuilleree|bain de bouche|collutoire"),
    )

    private fun normaliser(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

    private fun classer(texte: String?): TypeMedicament {
        val t = normaliser(texte ?: return TypeMedicament.GENERIQUE)
        return regles.firstOrNull { (_, regle) -> regle.containsMatchIn(t) }?.first ?: TypeMedicament.GENERIQUE
    }

    fun deduire(forme: String?, unite: String, nom: String): TypeMedicament =
        classer(forme).takeIf { it != TypeMedicament.GENERIQUE }
            ?: classer(unite).takeIf { it != TypeMedicament.GENERIQUE }
            ?: classer(nom)
}
