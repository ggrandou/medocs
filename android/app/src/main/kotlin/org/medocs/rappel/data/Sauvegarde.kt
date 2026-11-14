package org.medocs.rappel.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Instantané complet des données de l'utilisateur, avec les identifiants d'origine (les liens en dépendent). */
data class Donnees(
    val medicaments: List<Medicament>,
    val plannings: List<Planning>,
    val heures: List<PlanningHeure>,
    val prises: List<Prise>,
    val mouvements: List<StockMouvement>,
    /** Photos des boîtes : nom de fichier → contenu JPEG en Base64. Facultatif (absent des anciennes sauvegardes). */
    val photos: Map<String, String> = emptyMap(),
)

/** Les noms de photos viennent d'un fichier non fiable : lettres, chiffres, « - » et « _ » seulement, jamais de chemin. */
private val NOM_PHOTO = Regex("[A-Za-z0-9_-]{1,64}\\.jpg")
fun nomPhotoValide(nom: String): Boolean = NOM_PHOTO.matches(nom)

class SauvegardeInvalide(message: String) : Exception(message)

object SauvegardeJson {
    /** Version du format de fichier (indépendante de la version du schéma Room). */
    const val VERSION = 1

    fun versJson(d: Donnees): String = JSONObject().apply {
        put("format", "medocs")
        put("version", VERSION)
        put("medicaments", JSONArray().also { a -> d.medicaments.forEach { a.put(medicament(it)) } })
        put("plannings", JSONArray().also { a -> d.plannings.forEach { a.put(planning(it)) } })
        put("heures", JSONArray().also { a -> d.heures.forEach { a.put(heure(it)) } })
        put("prises", JSONArray().also { a -> d.prises.forEach { a.put(prise(it)) } })
        put("mouvements", JSONArray().also { a -> d.mouvements.forEach { a.put(mouvement(it)) } })
        put("photos", JSONObject().also { p -> d.photos.forEach { (nom, b64) -> p.put(nom, b64) } })
    }.toString()

    fun depuisJson(texte: String): Donnees {
        val o = try { JSONObject(texte) } catch (e: Exception) { throw SauvegardeInvalide("Fichier illisible") }
        if (o.optString("format") != "medocs") throw SauvegardeInvalide("Ce fichier n'est pas une sauvegarde de l'application")
        if (o.optInt("version") != VERSION) throw SauvegardeInvalide("Version de sauvegarde non prise en charge : ${o.optInt("version")}")
        return try {
            val photos = o.optJSONObject("photos")?.let { p -> p.keys().asSequence().associateWith { p.getString(it) } } ?: emptyMap()
            photos.keys.forEach { if (!nomPhotoValide(it)) throw SauvegardeInvalide("Nom de photo invalide dans la sauvegarde") }
            val d = Donnees(
                // Une photo absente du fichier n'est pas une erreur : le médicament perd simplement sa vignette
                medicaments = o.getJSONArray("medicaments").objets().map(::lireMedicament).map {
                    if (it.photoPath != null && it.photoPath !in photos) it.copy(photoPath = null) else it
                },
                plannings = o.getJSONArray("plannings").objets().map(::lirePlanning),
                heures = o.getJSONArray("heures").objets().map(::lireHeure),
                prises = o.getJSONArray("prises").objets().map(::lirePrise),
                mouvements = o.getJSONArray("mouvements").objets().map(::lireMouvement),
                photos = photos,
            )
            verifierCoherence(d)
            d
        } catch (e: SauvegardeInvalide) { throw e } catch (e: Exception) {
            throw SauvegardeInvalide("Sauvegarde corrompue : ${e.message}")
        }
    }

    /** Les clés étrangères doivent toutes pointer vers des lignes présentes, sinon l'insertion échouerait à mi-chemin. */
    private fun verifierCoherence(d: Donnees) {
        val med = d.medicaments.map { it.id }.toSet()
        val plan = d.plannings.map { it.id }.toSet()
        val heu = d.heures.map { it.id }.toSet()
        val pri = d.prises.map { it.id }.toSet()
        fun exige(ok: Boolean, quoi: String) { if (!ok) throw SauvegardeInvalide("Sauvegarde incohérente : $quoi") }
        d.plannings.forEach { exige(it.medicamentId in med, "planning ${it.id} sans médicament") }
        d.heures.forEach { exige(it.planningId in plan, "heure ${it.id} sans planning") }
        d.prises.forEach {
            exige(it.medicamentId in med, "prise ${it.id} sans médicament")
            exige(it.planningHeureId == null || it.planningHeureId in heu, "prise ${it.id} sans heure de planning")
        }
        d.mouvements.forEach {
            exige(it.medicamentId in med, "mouvement ${it.id} sans médicament")
            exige(it.priseId == null || it.priseId in pri, "mouvement ${it.id} sans prise")
        }
    }

    private fun JSONArray.objets(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.chaineOuNull(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.longOuNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.doubleOuNull(k: String): Double? = if (isNull(k)) null else getDouble(k)

    private fun medicament(m: Medicament) = JSONObject().put("id", m.id).put("nom", m.nom).put("cip13", m.cip13)
        .put("substances", m.substances).put("forme", m.forme).put("unite", m.unite).put("unitesParBoite", m.unitesParBoite)
        .put("photoPath", m.photoPath).put("seuilJours", m.seuilJours).put("seuilUnites", m.seuilUnites).put("archive", m.archive)

    private fun lireMedicament(o: JSONObject) = Medicament(
        id = o.getLong("id"), nom = o.getString("nom"), cip13 = o.chaineOuNull("cip13"), substances = o.chaineOuNull("substances"),
        forme = o.chaineOuNull("forme"), unite = o.getString("unite"), unitesParBoite = o.doubleOuNull("unitesParBoite"),
        photoPath = o.chaineOuNull("photoPath"), seuilJours = o.getInt("seuilJours"), seuilUnites = o.doubleOuNull("seuilUnites"),
        archive = o.getBoolean("archive"),
    )

    private fun planning(p: Planning) = JSONObject().put("id", p.id).put("medicamentId", p.medicamentId)
        .put("recurrence", p.recurrence.name).put("joursSemaine", p.joursSemaine).put("intervalleJours", p.intervalleJours)
        .put("dateDebut", p.dateDebut.toString()).put("dateFin", p.dateFin?.toString()).put("actif", p.actif)

    private fun lirePlanning(o: JSONObject) = Planning(
        id = o.getLong("id"), medicamentId = o.getLong("medicamentId"), recurrence = TypeRecurrence.valueOf(o.getString("recurrence")),
        joursSemaine = o.getInt("joursSemaine"), intervalleJours = o.getInt("intervalleJours"),
        dateDebut = LocalDate.parse(o.getString("dateDebut")), dateFin = o.chaineOuNull("dateFin")?.let(LocalDate::parse),
        actif = o.getBoolean("actif"),
    )

    private fun heure(h: PlanningHeure) = JSONObject().put("id", h.id).put("planningId", h.planningId)
        .put("heure", h.heure.toString()).put("quantite", h.quantite)

    private fun lireHeure(o: JSONObject) = PlanningHeure(
        id = o.getLong("id"), planningId = o.getLong("planningId"), heure = LocalTime.parse(o.getString("heure")), quantite = o.getDouble("quantite"),
    )

    private fun prise(p: Prise) = JSONObject().put("id", p.id).put("medicamentId", p.medicamentId).put("planningHeureId", p.planningHeureId)
        .put("datePrevue", p.datePrevue?.toString()).put("heurePrevue", p.heurePrevue?.toString()).put("statut", p.statut.name)
        .put("quantite", p.quantite).put("horodatage", p.horodatage).put("reporteJusqua", p.reporteJusqua)

    private fun lirePrise(o: JSONObject) = Prise(
        id = o.getLong("id"), medicamentId = o.getLong("medicamentId"), planningHeureId = o.longOuNull("planningHeureId"),
        datePrevue = o.chaineOuNull("datePrevue")?.let(LocalDate::parse), heurePrevue = o.chaineOuNull("heurePrevue")?.let(LocalTime::parse),
        statut = StatutPrise.valueOf(o.getString("statut")), quantite = o.getDouble("quantite"),
        horodatage = o.getLong("horodatage"), reporteJusqua = o.longOuNull("reporteJusqua"),
    )

    private fun mouvement(m: StockMouvement) = JSONObject().put("id", m.id).put("medicamentId", m.medicamentId)
        .put("delta", m.delta).put("type", m.type.name).put("horodatage", m.horodatage).put("priseId", m.priseId)

    private fun lireMouvement(o: JSONObject) = StockMouvement(
        id = o.getLong("id"), medicamentId = o.getLong("medicamentId"), delta = o.getDouble("delta"),
        type = TypeMouvement.valueOf(o.getString("type")), horodatage = o.getLong("horodatage"), priseId = o.longOuNull("priseId"),
    )
}
