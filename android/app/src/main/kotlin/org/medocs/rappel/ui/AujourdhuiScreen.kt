package org.medocs.rappel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.medocs.rappel.data.Medicament
import org.medocs.rappel.data.Prise
import org.medocs.rappel.domain.EtatOccurrence
import org.medocs.rappel.domain.enAttente
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMAT_JOUR = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

private fun heureDe(ms: Long): LocalTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AujourdhuiScreen(
    etat: EtatAujourdhui?,
    medicaments: List<Medicament>,
    onPrendre: (LigneAujourdhui) -> Unit,
    onSauter: (LigneAujourdhui) -> Unit,
    onReporter: (LigneAujourdhui, Int) -> Unit,
    onAnnuler: (Prise) -> Unit,
    onHorsPlanning: (Medicament, Double) -> Unit,
    alertes: List<AlerteAutorisation> = emptyList(),
) {
    var reportDe by remember { mutableStateOf<LigneAujourdhui?>(null) }
    var horsPlanning by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Aujourd'hui")
                    etat?.let { Text(it.date.format(FORMAT_JOUR).replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodyMedium) }
                }
            })
        },
        floatingActionButton = {
            if (medicaments.isNotEmpty()) ExtendedFloatingActionButton(onClick = { horsPlanning = true }) { Text("+ Prise hors planning") }
        },
    ) { padding ->
        if (etat == null) return@Scaffold
        if (etat.lignes.isEmpty() && etat.ponctuelles.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), verticalArrangement = Arrangement.Center) {
                alertes.forEach { BandeauAutorisation(it) }
                Text("Aucune prise prévue aujourd'hui.", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (medicaments.isEmpty()) "Ajoute d'abord un médicament dans l'onglet Médicaments."
                    else "Définis un planning depuis l'onglet Médicaments (touche un médicament, puis Planning).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(alertes) { a -> BandeauAutorisation(a) }
            items(etat.lignes, key = { it.occurrence.planningHeure.id }) { l ->
                CarteOccurrence(l, onPrendre, onSauter, { reportDe = l }, onAnnuler)
            }
            if (etat.ponctuelles.isNotEmpty()) {
                item { Text("Prises hors planning", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall) }
                items(etat.ponctuelles, key = { "p${it.prise.id}" }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.medicament.nom, style = MaterialTheme.typography.titleSmall)
                                Text("${formaterAvecUnite(p.prise.quantite, p.medicament.unite)} à ${heureDe(p.prise.horodatage)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { onAnnuler(p.prise) }) { Text("Annuler") }
                        }
                    }
                }
            }
        }
    }

    reportDe?.let { l ->
        AlertDialog(
            onDismissRequest = { reportDe = null },
            title = { Text("Reporter ${l.medicament.nom}") },
            text = {
                Column {
                    for ((minutes, libelle) in listOf(10 to "10 minutes", 30 to "30 minutes", 60 to "1 heure", 120 to "2 heures")) {
                        TextButton(onClick = { onReporter(l, minutes); reportDe = null }) { Text("De $libelle") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reportDe = null }) { Text("Annuler") } },
        )
    }

    if (horsPlanning) {
        HorsPlanningDialog(medicaments, onFermer = { horsPlanning = false }) { m, q -> onHorsPlanning(m, q); horsPlanning = false }
    }
}

@Composable
private fun CarteOccurrence(
    l: LigneAujourdhui,
    onPrendre: (LigneAujourdhui) -> Unit,
    onSauter: (LigneAujourdhui) -> Unit,
    onReporter: () -> Unit,
    onAnnuler: (Prise) -> Unit,
) {
    val o = l.occurrence
    val couleurs = when (l.etat) {
        EtatOccurrence.EN_RETARD -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        EtatOccurrence.PRISE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        EtatOccurrence.SAUTEE, EtatOccurrence.MANQUEE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        else -> CardDefaults.cardColors()
    }
    Card(Modifier.fillMaxWidth(), colors = couleurs) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(o.heure.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Column {
                    Text(l.medicament.nom, style = MaterialTheme.typography.titleMedium)
                    Text(formaterAvecUnite(o.quantite, l.medicament.unite), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val statut = when (l.etat) {
                EtatOccurrence.A_VENIR -> null
                EtatOccurrence.EN_RETARD -> "En retard"
                EtatOccurrence.REPORTEE -> "Reportée à ${l.prise?.reporteJusqua?.let(::heureDe)}"
                EtatOccurrence.PRISE -> "Pris à ${l.prise?.let { heureDe(it.horodatage) }}"
                EtatOccurrence.SAUTEE -> "Sautée"
                EtatOccurrence.MANQUEE -> "Manquée"
            }
            statut?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
            if (l.etat.enAttente) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPrendre(l) }) { Text("Pris") }
                    OutlinedButton(onClick = onReporter) { Text("Reporter") }
                    OutlinedButton(onClick = { onSauter(l) }) { Text("Sauter") }
                }
            } else {
                l.prise?.let { TextButton(onClick = { onAnnuler(it) }) { Text("Annuler") } }
            }
        }
    }
}

@Composable
private fun HorsPlanningDialog(medicaments: List<Medicament>, onFermer: () -> Unit, onConfirmer: (Medicament, Double) -> Unit) {
    var choisi by remember { mutableStateOf(medicaments.singleOrNull()) }
    var saisie by remember { mutableStateOf("1") }
    val quantite = lireDecimal(saisie)
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Prise hors planning") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (m in medicaments) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choisi?.id == m.id, onClick = { choisi = m })
                        Text(m.nom, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = saisie, onValueChange = { saisie = it }, singleLine = true,
                    label = { Text("Quantité (${choisi?.unite ?: "unités"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = choisi != null && quantite != null && quantite > 0, onClick = { onConfirmer(choisi!!, quantite!!) }) { Text("Enregistrer la prise") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

/** Autorisation système manquante qui empêche les rappels de fonctionner correctement. */
data class AlerteAutorisation(val message: String, val bouton: String, val action: () -> Unit)

@Composable
private fun BandeauAutorisation(a: AlerteAutorisation) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(a.message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = a.action) { Text(a.bouton) }
        }
    }
}
