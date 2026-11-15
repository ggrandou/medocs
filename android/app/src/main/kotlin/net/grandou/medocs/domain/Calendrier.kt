package net.grandou.medocs.domain

import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Couleur d'une pastille du calendrier mensuel (onglet Historique). */
enum class StatutJour {
    /** Passé ou aujourd'hui : toutes les prises faites. */
    TOUT_PRIS,

    /** Passé ou aujourd'hui : au moins une prise faite, mais pas toutes (sautées, manquées ou oubliées). */
    PARTIEL,

    /** Passé ou aujourd'hui : aucune prise faite alors que des prises étaient prévues. */
    AUCUNE_PRISE,

    /** Aucune prescription ce jour-là (point). */
    SANS_PRESCRIPTION,

    /** À venir : des prises prévues, couvertes par le stock. */
    A_VENIR_COUVERT,

    /** À venir : des prises prévues que le stock ne couvre plus. */
    A_VENIR_SANS_STOCK,
}

/**
 * Jours (de [aujourdhui] à [jusqua]) où au moins une prise prévue dépasse le stock du médicament.
 * Même simulation que [projeterStock], mais renvoie TOUS les jours en manque : un médicament à court
 * le reste jusqu'à un nouvel achat, et chaque médicament se simule à part.
 * [traitees] : occurrences d'aujourd'hui ou plus tard déjà prises/sautées, à ne pas recompter.
 */
fun joursSansStock(
    stocks: Map<Long, Double>,
    plannings: List<PlanningAvecHeures>,
    aujourdhui: LocalDate,
    jusqua: LocalDate,
    traitees: Set<Pair<Long, LocalDate>> = emptySet(),
): Set<LocalDate> {
    val manque = mutableSetOf<LocalDate>()
    for ((medicamentId, stock) in stocks) {
        val siens = plannings.filter { it.planning.medicamentId == medicamentId }
        if (siens.isEmpty()) continue
        var reste = stock
        var jour = aujourdhui
        while (!jour.isAfter(jusqua)) {
            for (occ in occurrencesDuJour(siens, jour)) {
                if (occ.cle in traitees) continue
                reste -= occ.quantite
                if (reste < -1e-9) manque += jour
            }
            jour = jour.plusDays(1)
        }
    }
    return manque
}

/**
 * Occurrences d'un jour passé qui n'ont pas encore de ligne dans le journal ET dont le moteur n'a pas encore pu écrire la ligne
 * « manquée » (moins de [delaiManqueMin] minutes après leur heure : ex. une prise de 23 h 30 regardée à minuit). Au-delà, la ligne
 * existe déjà ou n'existera jamais : on ne déduit plus rien des plannings.
 */
fun occurrencesSansLigneRecentes(
    plannings: List<PlanningAvecHeures>,
    date: LocalDate,
    lignesLiees: Set<Long>,
    maintenant: LocalDateTime,
    delaiManqueMin: Long = DELAI_MANQUE_MIN,
): List<Occurrence> = occurrencesDuJour(plannings, date).filter {
    it.planningHeure.id !in lignesLiees && LocalDateTime.of(it.date, it.heure).plusMinutes(delaiManqueMin).isAfter(maintenant)
}

/** Délai sans réponse avant qu'une occurrence passe en « manquée » (minutes). */
const val DELAI_MANQUE_MIN = 120L

/**
 * Statut de chaque jour d'un mois.
 *
 * **Jours passés : lus dans le journal des prises, jamais dans les plannings.** Chaque occurrence passée y a sa ligne (prise,
 * sautée, ou « manquée » écrite par le moteur 2 h après l'heure) : modifier ou supprimer un planning ne change donc pas l'historique.
 * Vert = tout pris, rouge = rien de pris, orange = entre les deux (sautée, manquée ou reportée sans suite) ; point = aucune ligne ce jour-là.
 * Seule exception : une occurrence sans ligne encore (moins de 2 h après son heure, le moteur ne l'a pas notée) compte comme non prise
 * ([occurrencesSansLigneRecentes]).
 * **Aujourd'hui** : calculé sur les plannings. Même règle sur les prises échues ; tant qu'il en reste à venir et que tout ce qui est échu
 * est pris, la journée est « en cours » et se colore comme un jour futur.
 * **Jours futurs** : gris si le stock couvre les prises, blanc au-delà du stock.
 *
 * [prisesDuMois] : lignes du journal dont la date prévue tombe dans le mois (reliées à un planning ou non).
 * [stocks] : stock actuel par médicament ; [traitees] : occurrences d'aujourd'hui ou plus tard déjà décidées.
 */
fun statutsDuMois(
    mois: YearMonth,
    maintenant: LocalDateTime,
    plannings: List<PlanningAvecHeures>,
    prisesDuMois: List<Prise>,
    stocks: Map<Long, Double>,
    traitees: Set<Pair<Long, LocalDate>>,
): Map<LocalDate, StatutJour> {
    val aujourdhui = maintenant.toLocalDate()
    val dernier = mois.atEndOfMonth()
    val sansStock = if (dernier.isBefore(aujourdhui)) emptySet() else joursSansStock(stocks, plannings, aujourdhui, dernier, traitees)
    val parOccurrence = prisesDuMois.filter { it.planningHeureId != null && it.datePrevue != null }.associateBy { it.planningHeureId!! to it.datePrevue!! }
    val parJour = prisesDuMois.filter { it.datePrevue != null }.groupBy { it.datePrevue!! }

    fun avenir(jour: LocalDate) = if (jour in sansStock) StatutJour.A_VENIR_SANS_STOCK else StatutJour.A_VENIR_COUVERT

    return (1..mois.lengthOfMonth()).map { mois.atDay(it) }.associateWith { jour ->
        when {
            jour.isAfter(aujourdhui) -> if (occurrencesDuJour(plannings, jour).isEmpty()) StatutJour.SANS_PRESCRIPTION else avenir(jour)
            jour.isBefore(aujourdhui) -> {
                val lignes = parJour[jour].orEmpty()
                val liees = lignes.mapNotNull { it.planningHeureId }.toSet()
                val sansLigne = occurrencesSansLigneRecentes(plannings, jour, liees, maintenant).size
                val total = lignes.size + sansLigne
                val prises = lignes.count { it.statut == StatutPrise.PRISE }
                when {
                    total == 0 -> StatutJour.SANS_PRESCRIPTION
                    prises == total -> StatutJour.TOUT_PRIS
                    prises == 0 -> StatutJour.AUCUNE_PRISE
                    else -> StatutJour.PARTIEL
                }
            }
            else -> {
                val occurrences = occurrencesDuJour(plannings, jour)
                if (occurrences.isEmpty()) return@associateWith StatutJour.SANS_PRESCRIPTION
                val etats = occurrences.map { etatOccurrence(it, parOccurrence[it.cle], maintenant) }
                val prises = etats.count { it == EtatOccurrence.PRISE }
                val echues = etats.filter { it != EtatOccurrence.A_VENIR }
                when {
                    prises == etats.size -> StatutJour.TOUT_PRIS
                    echues.isEmpty() || (echues.size < etats.size && echues.all { it == EtatOccurrence.PRISE }) -> avenir(jour) // journée en cours
                    prises == 0 -> StatutJour.AUCUNE_PRISE
                    else -> StatutJour.PARTIEL
                }
            }
        }
    }
}
