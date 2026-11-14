package net.grandou.medocs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.data.Prise
import net.grandou.medocs.data.StatutPrise
import net.grandou.medocs.data.StockMouvement
import net.grandou.medocs.data.TypeMouvement
import net.grandou.medocs.domain.EntreeHistorique
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMAT_INSTANT = DateTimeFormatter.ofPattern("EEE d MMM  HH:mm", Locale.FRANCE)
private fun formaterInstant(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(FORMAT_INSTANT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierMedicamentScreen(vm: AppViewModel, medicament: Medicament, onRetour: () -> Unit) {
    var nom by remember { mutableStateOf(medicament.nom) }
    var unite by remember { mutableStateOf(medicament.unite) }
    var parBoite by remember { mutableStateOf(medicament.unitesParBoite?.let(::formaterQuantite) ?: "") }
    var seuilJours by remember { mutableStateOf(medicament.seuilJours.toString()) }
    var seuilUnites by remember { mutableStateOf(medicament.seuilUnites?.let(::formaterQuantite) ?: "") }
    var confirmerArchivage by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(medicament.photoPath) }

    val parBoiteVal = lireDecimal(parBoite)
    val seuilJoursVal = seuilJours.trim().toIntOrNull()
    val seuilUnitesVal = lireDecimal(seuilUnites)
    val valide = nom.isNotBlank() && unite.isNotBlank() &&
        (parBoite.isBlank() || (parBoiteVal != null && parBoiteVal > 0)) &&
        seuilJoursVal != null && seuilJoursVal >= 0 &&
        (seuilUnites.isBlank() || (seuilUnitesVal != null && seuilUnitesVal >= 0))

    BackHandler(onBack = onRetour)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Modifier le médicament") },
            navigationIcon = { TextButton(onClick = onRetour) { Text("Retour") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            medicament.substances?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom") })
            OutlinedTextField(
                unite, { unite = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Unité de prise") },
                supportingText = { Text("Sert à l'affichage : le stock et les doses ne sont pas converties.") },
            )
            OutlinedTextField(
                parBoite, { parBoite = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Quantité par boîte (facultatif)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                seuilJours, { seuilJours = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Alerte de stock faible (jours)") },
                supportingText = { Text("Prévenir quand le stock couvre moins de ce nombre de jours de planning.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                seuilUnites, { seuilUnites = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Alerte en unités (facultatif)") },
                supportingText = { Text("Utile pour un médicament sans planning.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            ChampPhoto(photo) { photo = it }
            Button(
                enabled = valide, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.modifierMedicament(
                        medicament.copy(
                            photoPath = photo,
                            nom = nom.trim(), unite = unite.trim(),
                            unitesParBoite = if (parBoite.isBlank()) null else parBoiteVal,
                            seuilJours = seuilJoursVal!!,
                            seuilUnites = if (seuilUnites.isBlank()) null else seuilUnitesVal,
                        )
                    )
                    onRetour()
                },
            ) { Text("Enregistrer") }
            OutlinedButton(onClick = { confirmerArchivage = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Archiver (arrêter les rappels, garder l'historique)")
            }
        }
    }

    if (confirmerArchivage) {
        AlertDialog(
            onDismissRequest = { confirmerArchivage = false },
            title = { Text("Archiver ${medicament.nom} ?") },
            text = { Text("Les rappels et les alertes de stock s'arrêtent. Le planning et l'historique sont conservés ; tu pourras le restaurer depuis le menu « Plus » de l'onglet Médicaments.") },
            confirmButton = { TextButton(onClick = { vm.modifierMedicament(medicament.copy(archive = true)); onRetour() }) { Text("Archiver") } },
            dismissButton = { TextButton(onClick = { confirmerArchivage = false }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriqueScreen(vm: AppViewModel, medicament: Medicament, onRetour: () -> Unit) {
    val entrees by vm.historique(medicament.id).collectAsStateWithLifecycle(emptyList())
    var asupprimer by remember { mutableStateOf<Prise?>(null) }

    BackHandler(onBack = onRetour)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Historique : ${medicament.nom}", maxLines = 1) },
            navigationIcon = { TextButton(onClick = onRetour) { Text("Retour") } },
        )
    }) { padding ->
        if (entrees.isEmpty()) {
            Text("Rien à afficher pour l'instant.", Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entrees, key = { e -> when (e) { is EntreeHistorique.DePrise -> "p${e.prise.id}"; is EntreeHistorique.DeStock -> "m${e.mouvement.id}" } }) { e ->
                    when (e) {
                        is EntreeHistorique.DePrise -> LignePrise(e.prise, medicament) { asupprimer = e.prise }
                        is EntreeHistorique.DeStock -> LigneStock(e.mouvement, medicament)
                    }
                }
            }
        }
    }

    asupprimer?.let { p ->
        AlertDialog(
            onDismissRequest = { asupprimer = null },
            title = { Text("Supprimer cette ligne ?") },
            text = { Text(
                if (p.statut == StatutPrise.PRISE) "La prise est effacée du journal et ${formaterAvecUnite(p.quantite, medicament.unite)} sont remis en stock."
                else "La ligne est effacée du journal."
            ) },
            confirmButton = { TextButton(onClick = { vm.annuler(p); asupprimer = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { asupprimer = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun LignePrise(p: Prise, m: Medicament, onSupprimer: () -> Unit) {
    val manquee = p.statut == StatutPrise.MANQUEE
    val couleurs = if (manquee) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    val statut = when (p.statut) {
        StatutPrise.PRISE -> "Pris"
        StatutPrise.SAUTEE -> "Sautée"
        StatutPrise.MANQUEE -> "Manquée"
        StatutPrise.REPORTEE -> "Reportée" + (p.reporteJusqua?.let { " (rappel à ${formaterInstant(it).takeLast(5)})" } ?: "")
    }
    val prevue = if (p.datePrevue != null) "Prévue ${formaterDate(p.datePrevue)} ${p.heurePrevue ?: ""}" else "Hors planning"
    Card(Modifier.fillMaxWidth(), colors = couleurs) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$statut · ${formaterAvecUnite(p.quantite, m.unite)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(prevue, style = MaterialTheme.typography.bodyMedium)
                Text("Enregistré ${formaterInstant(p.horodatage)}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSupprimer) { Text("Supprimer") }
        }
    }
}

@Composable
private fun LigneStock(mv: StockMouvement, m: Medicament) {
    val libelle = when (mv.type) {
        TypeMouvement.ACHAT -> "Stock ajouté : +${formaterAvecUnite(mv.delta, m.unite)}"
        TypeMouvement.CORRECTION -> "Stock corrigé : ${if (mv.delta >= 0) "+" else ""}${formaterAvecUnite(mv.delta, m.unite)}"
        TypeMouvement.PRISE -> "Prise : ${formaterAvecUnite(mv.delta, m.unite)}"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp, 8.dp)) {
            Text(libelle, style = MaterialTheme.typography.titleSmall)
            Text(formaterInstant(mv.horodatage), style = MaterialTheme.typography.bodySmall)
        }
    }
}
