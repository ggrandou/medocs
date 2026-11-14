package org.medocs.rappel.domain

import org.medocs.rappel.data.Prise
import org.medocs.rappel.data.StatutPrise
import java.time.LocalDateTime

enum class EtatOccurrence { A_VENIR, EN_RETARD, REPORTEE, PRISE, SAUTEE, MANQUEE }

/** État affiché d'une occurrence : le statut du journal s'il existe, sinon déduit de l'heure. */
fun etatOccurrence(occurrence: Occurrence, prise: Prise?, maintenant: LocalDateTime): EtatOccurrence =
    when (prise?.statut) {
        StatutPrise.PRISE -> EtatOccurrence.PRISE
        StatutPrise.SAUTEE -> EtatOccurrence.SAUTEE
        StatutPrise.MANQUEE -> EtatOccurrence.MANQUEE
        StatutPrise.REPORTEE -> EtatOccurrence.REPORTEE
        null ->
            if (LocalDateTime.of(occurrence.date, occurrence.heure).isBefore(maintenant)) EtatOccurrence.EN_RETARD
            else EtatOccurrence.A_VENIR
    }

/** Une occurrence attend-elle encore une réponse de l'utilisateur ? */
val EtatOccurrence.enAttente: Boolean
    get() = this == EtatOccurrence.A_VENIR || this == EtatOccurrence.EN_RETARD || this == EtatOccurrence.REPORTEE
