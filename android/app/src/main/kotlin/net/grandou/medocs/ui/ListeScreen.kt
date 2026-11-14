package net.grandou.medocs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import net.grandou.medocs.domain.ProjectionStock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeScreen(
    lignes: List<LigneMedicament>?,
    onAjouter: () -> Unit,
    onPlanning: (LigneMedicament) -> Unit,
    onModifier: (LigneMedicament) -> Unit,
    onHistorique: (LigneMedicament) -> Unit,
    onAjouterStock: (LigneMedicament, Double) -> Unit,
    onDefinirStock: (LigneMedicament, Double) -> Unit,
    onSupprimer: (LigneMedicament) -> Unit,
) {
    var selection by remember { mutableStateOf<LigneMedicament?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Mes médicaments") },
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAjouter) { Text("+ Ajouter un médicament") } },
    ) { padding ->
        when {
            lignes == null -> Unit
            lignes.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Aucun médicament.", style = MaterialTheme.typography.titleMedium)
                Text("Touche « Ajouter un médicament » pour commencer.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lignes, key = { it.medicament.id }) { l -> CarteMedicament(l) { selection = l } }
            }
        }
    }

    selection?.let { l ->
        StockDialog(
            ligne = l,
            onFermer = { selection = null },
            onPlanning = { onPlanning(l); selection = null },
            onModifier = { onModifier(l); selection = null },
            onHistorique = { onHistorique(l); selection = null },
            onAjouter = { onAjouterStock(l, it); selection = null },
            onDefinir = { onDefinirStock(l, it); selection = null },
            onSupprimer = { onSupprimer(l); selection = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteMedicament(l: LigneMedicament, onClick: () -> Unit) {
    val m = l.medicament
    val couleurs = if (l.alerte) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = couleurs) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          VisuelMedicament(m, 72.dp) // photo ou icône du type ; décoratif : le nom est lu à côté
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(m.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            m.substances?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stock : ${formaterAvecUnite(l.stock, m.unite)}", style = MaterialTheme.typography.bodyLarge)
                if (l.alerte) Text("Stock faible", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                when (val p = l.projection) {
                    ProjectionStock.SansPlanning -> "Pas de planning"
                    ProjectionStock.Suffisant -> "Stock suffisant jusqu'à la fin du traitement"
                    is ProjectionStock.Rupture -> when (p.joursRestants) {
                        0 -> "Insuffisant dès aujourd'hui"
                        1 -> "Rupture demain"
                        else -> "Rupture dans ${p.joursRestants} jours"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockDialog(
    ligne: LigneMedicament,
    onFermer: () -> Unit,
    onPlanning: () -> Unit,
    onModifier: () -> Unit,
    onHistorique: () -> Unit,
    onAjouter: (Double) -> Unit,
    onDefinir: (Double) -> Unit,
    onSupprimer: () -> Unit,
) {
    val m = ligne.medicament
    var saisie by remember { mutableStateOf("") }
    var confirmerSuppression by remember { mutableStateOf(false) }
    val valeur = lireDecimal(saisie)

    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Supprimer ${m.nom} ?") },
            text = { Text("Le planning, le journal des prises et le stock seront définitivement supprimés.") },
            confirmButton = { TextButton(onClick = onSupprimer) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(m.nom) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stock actuel : ${formaterAvecUnite(ligne.stock, m.unite)}")
                TextButton(onClick = onPlanning) { Text("Planning des prises…") }
                FlowRow {
                    TextButton(onClick = onModifier) { Text("Modifier…") }
                    TextButton(onClick = onHistorique) { Text("Historique…") }
                }
                m.unitesParBoite?.let { boite ->
                    TextButton(onClick = { onAjouter(boite) }) { Text("Ajouter une boîte (+${formaterAvecUnite(boite, m.unite)})") }
                }
                OutlinedTextField(
                    value = saisie, onValueChange = { saisie = it },
                    label = { Text("Quantité (${m.unite})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = valeur != null && valeur > 0, onClick = { onAjouter(valeur!!) }) { Text("Ajouter") }
                    TextButton(enabled = valeur != null && valeur >= 0, onClick = { onDefinir(valeur!!) }) { Text("Définir le stock") }
                }
                TextButton(onClick = { confirmerSuppression = true }) { Text("Supprimer ce médicament") }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}
