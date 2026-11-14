package net.grandou.medocs.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Planning
import net.grandou.medocs.data.PlanningAvecHeures
import net.grandou.medocs.data.PlanningHeure
import net.grandou.medocs.data.TypeRecurrence
import net.grandou.medocs.domain.masqueJours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(vm: AppViewModel, medicament: Medicament, onRetour: () -> Unit) {
    val plannings by vm.plannings(medicament.id).collectAsStateWithLifecycle(emptyList())
    // null = liste ; sinon formulaire (id 0 = nouveau planning)
    var edition by remember { mutableStateOf<PlanningAvecHeures?>(null) }
    var asupprimer by remember { mutableStateOf<Planning?>(null) }

    BackHandler { if (edition != null) edition = null else onRetour() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Planning : ${medicament.nom}", maxLines = 1) },
            navigationIcon = { TextButton(onClick = { if (edition != null) edition = null else onRetour() }) { Text("Retour") } },
        )
    }) { padding ->
        val e = edition
        if (e != null) {
            FormulairePlanning(medicament, e) { planning, heures ->
                vm.enregistrerPlanning(planning, heures)
                edition = null
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (plannings.isEmpty()) Text("Aucun planning : ce médicament n'est pas encore rappelé.", style = MaterialTheme.typography.bodyMedium)
                for (p in plannings) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(resumePlanning(p, medicament.unite), style = MaterialTheme.typography.bodyLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { edition = p }) { Text("Modifier") }
                                TextButton(onClick = { asupprimer = p.planning }) { Text("Supprimer") }
                            }
                        }
                    }
                }
                Button(onClick = {
                    edition = PlanningAvecHeures(
                        Planning(medicamentId = medicament.id, dateDebut = LocalDate.now()),
                        listOf(PlanningHeure(planningId = 0, heure = LocalTime.of(8, 0), quantite = 1.0)),
                    )
                }, Modifier.fillMaxWidth()) { Text("Ajouter un planning") }
            }
        }
    }

    asupprimer?.let { p ->
        AlertDialog(
            onDismissRequest = { asupprimer = null },
            title = { Text("Supprimer ce planning ?") },
            text = { Text("Le journal des prises déjà enregistrées est conservé.") },
            confirmButton = { TextButton(onClick = { vm.supprimerPlanning(p); asupprimer = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { asupprimer = null }) { Text("Annuler") } },
        )
    }
}

private class HeureEditable(var heure: LocalTime, dose: String) {
    var dose by mutableStateOf(dose)
    var heureEtat by mutableStateOf(heure)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulairePlanning(
    medicament: Medicament,
    initial: PlanningAvecHeures,
    onEnregistrer: (Planning, List<PlanningHeure>) -> Unit,
) {
    val contexte = LocalContext.current
    val p = initial.planning
    var recurrence by remember { mutableStateOf(p.recurrence) }
    var jours by remember { mutableStateOf(DayOfWeek.entries.filter { p.joursSemaine and (1 shl (it.value - 1)) != 0 }.toSet()) }
    var intervalle by remember { mutableStateOf(p.intervalleJours.toString()) }
    var debut by remember { mutableStateOf(p.dateDebut) }
    var fin by remember { mutableStateOf(p.dateFin) }
    var actif by remember { mutableStateOf(p.actif) }
    val heures = remember {
        mutableStateListOf<HeureEditable>().apply {
            initial.heures.sortedBy { it.heure }.forEach { add(HeureEditable(it.heure, formaterQuantite(it.quantite)).also { h -> h.heureEtat = it.heure }) }
        }
    }

    fun choisirDate(actuelle: LocalDate, ok: (LocalDate) -> Unit) =
        DatePickerDialog(contexte, { _, a, m, j -> ok(LocalDate.of(a, m + 1, j)) }, actuelle.year, actuelle.monthValue - 1, actuelle.dayOfMonth).show()

    fun choisirHeure(h: HeureEditable) =
        TimePickerDialog(contexte, { _, hh, mm -> h.heureEtat = LocalTime.of(hh, mm) }, h.heureEtat.hour, h.heureEtat.minute, true).show()

    val n = intervalle.trim().toIntOrNull()
    val valide = heures.isNotEmpty() && heures.all { (lireDecimal(it.dose) ?: 0.0) > 0 } &&
        (recurrence != TypeRecurrence.JOURS_SEMAINE || jours.isNotEmpty()) &&
        (recurrence != TypeRecurrence.TOUS_LES_N_JOURS || (n != null && n >= 1)) &&
        (fin == null || !fin!!.isBefore(debut))

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rythme", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(recurrence == TypeRecurrence.QUOTIDIEN, { recurrence = TypeRecurrence.QUOTIDIEN }, { Text("Tous les jours") })
            FilterChip(recurrence == TypeRecurrence.JOURS_SEMAINE, { recurrence = TypeRecurrence.JOURS_SEMAINE }, { Text("Certains jours") })
            FilterChip(recurrence == TypeRecurrence.TOUS_LES_N_JOURS, { recurrence = TypeRecurrence.TOUS_LES_N_JOURS }, { Text("Tous les N jours") })
        }
        if (recurrence == TypeRecurrence.JOURS_SEMAINE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (j in DayOfWeek.entries) {
                    FilterChip(j in jours, { jours = if (j in jours) jours - j else jours + j }, { Text(nomJour(j)) })
                }
            }
        }
        if (recurrence == TypeRecurrence.TOUS_LES_N_JOURS) {
            OutlinedTextField(
                intervalle, { intervalle = it }, singleLine = true, label = { Text("Un jour sur …") },
                supportingText = { Text("2 = un jour sur deux, à partir de la date de début.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Text("Heures de prise", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        for ((i, h) in heures.withIndex()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { choisirHeure(h) }, Modifier.semantics { contentDescription = "Heure de prise : ${h.heureEtat}, toucher pour modifier" }) { Text(h.heureEtat.toString()) }
                    TextButton(onClick = { heures.removeAt(i) }, Modifier.semantics { contentDescription = "Retirer l'heure ${h.heureEtat}" }) { Text("Retirer") }
                }
                OutlinedTextField(
                    h.dose, { h.dose = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Dose à ${h.heureEtat} (${medicament.unite})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        OutlinedButton(onClick = {
            val derniere = heures.lastOrNull()
            heures.add(HeureEditable(LocalTime.of(20, 0), derniere?.dose ?: "1"))
        }) { Text("+ Ajouter une heure") }

        Text("Période", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        Text("Début", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { choisirDate(debut) { debut = it } }, Modifier.semantics { contentDescription = "Date de début : ${formaterDate(debut)}, toucher pour modifier" }) { Text(formaterDate(debut)) }
        Text("Fin", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { choisirDate(fin ?: debut) { fin = it } }, Modifier.semantics { contentDescription = "Date de fin : ${fin?.let(::formaterDate) ?: "aucune"}, toucher pour modifier" }) { Text(fin?.let(::formaterDate) ?: "Sans fin") }
            if (fin != null) TextButton(onClick = { fin = null }) { Text("Sans fin") }
        }
        if (fin != null && fin!!.isBefore(debut)) Text("La date de fin précède le début.", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)

        // Toute la ligne est l'interrupteur : TalkBack lit « Planning actif, interrupteur, activé »
        Row(
            Modifier.fillMaxWidth().minimumInteractiveComponentSize().toggleable(value = actif, role = Role.Switch, onValueChange = { actif = it }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(actif, null)
            Text(if (actif) "Planning actif" else "Planning en pause")
        }

        Button(
            enabled = valide, modifier = Modifier.fillMaxWidth(),
            onClick = {
                val planning = p.copy(
                    recurrence = recurrence,
                    joursSemaine = if (recurrence == TypeRecurrence.JOURS_SEMAINE) masqueJours(*jours.toTypedArray()) else 0,
                    intervalleJours = if (recurrence == TypeRecurrence.TOUS_LES_N_JOURS) n!! else 1,
                    dateDebut = debut, dateFin = fin, actif = actif,
                )
                onEnregistrer(planning, heures.sortedBy { it.heureEtat }.map { PlanningHeure(planningId = p.id, heure = it.heureEtat, quantite = lireDecimal(it.dose)!!) })
            },
        ) { Text("Enregistrer") }
    }
}
