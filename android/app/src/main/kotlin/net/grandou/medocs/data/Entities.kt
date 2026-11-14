package net.grandou.medocs.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalTime

/**
 * Médicament suivi par l'utilisateur.
 *
 * Les informations venant de la BDPM sont COPIÉES ici (pas de clé étrangère) : la BDPM embarquée
 * est remplacée à chaque mise à jour de l'application sans toucher aux données de l'utilisateur.
 * Un médicament peut aussi être saisi entièrement à la main (cip13 == null).
 */
@Entity(tableName = "medicament")
data class Medicament(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    /** Lien informatif vers la BDPM (présentation choisie), facultatif. */
    val cip13: String? = null,
    /** Molécule(s) et dosage, texte d'affichage uniquement (ex. « PARACÉTAMOL 500 mg »). */
    val substances: String? = null,
    val forme: String? = null,
    /** Unité de prise et de stock : « comprimé », « ml », « sachet »... */
    val unite: String = "unité",
    /** Quantité contenue dans une boîte, pour le bouton « ajouter une boîte ». Modifiable. */
    val unitesParBoite: Double? = null,
    /** Chemin de la photo prise par l'utilisateur, stockée en local. */
    val photoPath: String? = null,
    /** Alerte quand le stock ne couvre plus que ce nombre de jours de planning. */
    val seuilJours: Int = 7,
    /** Alerte quand le stock passe sous ce nombre d'unités (utile sans planning, prises ponctuelles). */
    val seuilUnites: Double? = null,
    val archive: Boolean = false,
)

enum class TypeRecurrence {
    /** Tous les jours. */
    QUOTIDIEN,

    /** Certains jours de la semaine (voir [Planning.joursSemaine]). */
    JOURS_SEMAINE,

    /** Tous les N jours à partir de [Planning.dateDebut] (voir [Planning.intervalleJours]). */
    TOUS_LES_N_JOURS,
}

/** Règle de récurrence d'un traitement ; les heures et doses sont dans [PlanningHeure]. */
@Entity(
    tableName = "planning",
    foreignKeys = [ForeignKey(Medicament::class, ["id"], ["medicamentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicamentId")],
)
data class Planning(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val recurrence: TypeRecurrence = TypeRecurrence.QUOTIDIEN,
    /** Masque de bits des jours (bit 0 = lundi ... bit 6 = dimanche), pour JOURS_SEMAINE. */
    val joursSemaine: Int = 0,
    /** Pour TOUS_LES_N_JOURS (>= 1). */
    val intervalleJours: Int = 1,
    val dateDebut: LocalDate,
    /** Dernier jour inclus ; null = traitement sans fin. */
    val dateFin: LocalDate? = null,
    val actif: Boolean = true,
)

/** Une heure de prise d'un planning, avec sa dose (« 2 le matin, 1 le soir »). Heure locale « murale ». */
@Entity(
    tableName = "planning_heure",
    foreignKeys = [ForeignKey(Planning::class, ["id"], ["planningId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planningId")],
)
data class PlanningHeure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planningId: Long,
    val heure: LocalTime,
    /** Dose en unités du médicament ; décimal pour les demi-comprimés (0.5). */
    val quantite: Double,
)

enum class StatutPrise {
    /** Prise reportée : l'occurrence reste en attente jusqu'à [Prise.reporteJusqua]. */
    REPORTEE,
    PRISE,
    SAUTEE,

    /** Occurrence passée sans réponse de l'utilisateur. */
    MANQUEE,
}

/**
 * Journal des prises. Une ligne existe dès que l'utilisateur agit sur une occurrence planifiée,
 * ou à la création d'une prise ponctuelle (planningHeureId == null, hors planning).
 * Les occurrences futures ne sont PAS stockées : elles se déduisent des plannings.
 */
@Entity(
    tableName = "prise",
    foreignKeys = [
        ForeignKey(Medicament::class, ["id"], ["medicamentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PlanningHeure::class, ["id"], ["planningHeureId"], onDelete = ForeignKey.SET_NULL),
    ],
    // Unicité d'une occurrence planifiée (les NULL des prises ponctuelles ne se contraignent pas).
    indices = [
        Index("medicamentId"),
        Index(value = ["planningHeureId", "datePrevue"], unique = true),
    ],
)
data class Prise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val planningHeureId: Long? = null,
    val datePrevue: LocalDate? = null,
    val heurePrevue: LocalTime? = null,
    val statut: StatutPrise,
    val quantite: Double,
    /** Instant (epoch ms) de la prise réelle, ou de la décision pour SAUTEE/MANQUEE. */
    val horodatage: Long,
    /** Pour REPORTEE : instant (epoch ms) du nouveau rappel. */
    val reporteJusqua: Long? = null,
)

enum class TypeMouvement { ACHAT, PRISE, CORRECTION }

/**
 * Journal du stock : le stock courant est la SOMME des deltas (jamais une valeur stockée),
 * ce qui permet d'annuler une prise sans dérive. Un mouvement PRISE est supprimé en cascade
 * avec sa [Prise].
 */
@Entity(
    tableName = "stock_mouvement",
    foreignKeys = [
        ForeignKey(Medicament::class, ["id"], ["medicamentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Prise::class, ["id"], ["priseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medicamentId"), Index("priseId")],
)
data class StockMouvement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val delta: Double,
    val type: TypeMouvement,
    val horodatage: Long,
    val priseId: Long? = null,
)

data class PlanningAvecHeures(
    @Embedded val planning: Planning,
    @Relation(parentColumn = "id", entityColumn = "planningId") val heures: List<PlanningHeure>,
)

data class MedicamentAvecStock(
    @Embedded val medicament: Medicament,
    val stock: Double,
)
