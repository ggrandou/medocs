package org.medocs.rappel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.medocs.rappel.bdpm.PresentationBdpm
import org.medocs.rappel.bdpm.SpecialiteBdpm
import org.medocs.rappel.data.Medicament

/** Valeurs proposées au formulaire final ; toutes restent modifiables par l'utilisateur. */
private data class Prefill(
    val nom: String = "",
    val cip13: String? = null,
    val substances: String? = null,
    val forme: String? = null,
    val unite: String = "",
    val unitesParBoite: Double? = null,
    val aVerifier: Boolean = false,
)

private sealed interface Etape {
    data object Recherche : Etape
    data class Presentations(val specialite: SpecialiteBdpm) : Etape
    data class Formulaire(val prefill: Prefill, val retour: Etape) : Etape
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutScreen(vm: AppViewModel, onTermine: () -> Unit) {
    var etape by remember { mutableStateOf<Etape>(Etape.Recherche) }

    BackHandler {
        when (val e = etape) {
            Etape.Recherche -> onTermine()
            is Etape.Presentations -> etape = Etape.Recherche
            is Etape.Formulaire -> etape = e.retour
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ajouter un médicament") },
            navigationIcon = { TextButton(onClick = {
                when (val e = etape) {
                    Etape.Recherche -> onTermine()
                    is Etape.Presentations -> etape = Etape.Recherche
                    is Etape.Formulaire -> etape = e.retour
                }
            }) { Text("Retour") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val e = etape) {
                Etape.Recherche -> EtapeRecherche(
                    vm,
                    onChoisir = { etape = Etape.Presentations(it) },
                    onManuel = { etape = Etape.Formulaire(Prefill(), Etape.Recherche) },
                )
                is Etape.Presentations -> EtapePresentations(
                    vm, e.specialite,
                    onChoisir = { p ->
                        etape = Etape.Formulaire(
                            Prefill(
                                nom = e.specialite.nom, cip13 = p.cip13, substances = e.specialite.substances,
                                forme = e.specialite.forme, unite = p.unite ?: "unité", unitesParBoite = p.nbUnites,
                                aVerifier = !p.nbUnitesFiable,
                            ),
                            retour = e,
                        )
                    },
                    onManuel = {
                        etape = Etape.Formulaire(
                            Prefill(nom = e.specialite.nom, substances = e.specialite.substances, forme = e.specialite.forme),
                            retour = e,
                        )
                    },
                )
                is Etape.Formulaire -> Formulaire(e.prefill) { m, stock ->
                    vm.ajouterMedicament(m, stock)
                    onTermine()
                }
            }
        }
    }
}

@Composable
private fun EtapeRecherche(vm: AppViewModel, onChoisir: (SpecialiteBdpm) -> Unit, onManuel: () -> Unit) {
    var texte by remember { mutableStateOf("") }
    val resultats by vm.resultats.collectAsState()
    val enCours by vm.rechercheEnCours.collectAsState()

    LaunchedEffect(texte) {
        delay(250) // attend la fin de la frappe
        vm.rechercher(texte)
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = texte, onValueChange = { texte = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Nom du médicament ou molécule") }, singleLine = true,
        )
        OutlinedButton(onClick = onManuel, modifier = Modifier.fillMaxWidth()) { Text("Saisie manuelle (médicament absent de la liste)") }
    }
    when {
        texte.trim().length < 2 -> Text("Saisis au moins 2 lettres.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        resultats.isEmpty() && !enCours -> Text("Aucun résultat. Utilise la saisie manuelle.", Modifier.padding(16.dp))
        else -> LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(resultats, key = { it.cis }) { s ->
                Card(Modifier.fillMaxWidth().clickable { onChoisir(s) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.nom, style = MaterialTheme.typography.titleSmall)
                        s.substances?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EtapePresentations(
    vm: AppViewModel, s: SpecialiteBdpm, onChoisir: (PresentationBdpm) -> Unit, onManuel: () -> Unit,
) {
    val presentations by produceState<List<PresentationBdpm>?>(null, s.cis) { value = vm.presentations(s.cis) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.nom, style = MaterialTheme.typography.titleMedium)
        s.substances?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Choisis le conditionnement :", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
    }
    val liste = presentations
    if (liste == null) return
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(liste, key = { it.cip13 }) { p ->
            Card(Modifier.fillMaxWidth().clickable { onChoisir(p) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.libelle, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (p.nbUnites != null && p.unite != null) "→ ${formaterAvecUnite(p.nbUnites, p.unite)}" +
                            (if (!p.nbUnitesFiable) " (à vérifier)" else "")
                        else "Quantité à saisir",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        item { TextButton(onClick = onManuel) { Text("Aucun ne correspond : saisie manuelle") } }
    }
}

@Composable
private fun Formulaire(prefill: Prefill, onEnregistrer: (Medicament, Double) -> Unit) {
    var nom by remember { mutableStateOf(prefill.nom) }
    var unite by remember { mutableStateOf(prefill.unite) }
    var parBoite by remember { mutableStateOf(prefill.unitesParBoite?.let(::formaterQuantite) ?: "") }
    var stock by remember { mutableStateOf(prefill.unitesParBoite?.let(::formaterQuantite) ?: "") }
    var seuil by remember { mutableStateOf("7") }
    var photo by remember { mutableStateOf<String?>(null) }

    val parBoiteVal = lireDecimal(parBoite)
    val stockVal = if (stock.isBlank()) 0.0 else lireDecimal(stock)
    val seuilVal = seuil.trim().toIntOrNull()
    val valide = nom.isNotBlank() && unite.isNotBlank() && stockVal != null && stockVal >= 0 &&
        (parBoite.isBlank() || (parBoiteVal != null && parBoiteVal > 0)) && seuilVal != null && seuilVal >= 0

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        prefill.substances?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom") })
        OutlinedTextField(
            unite, { unite = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Unité de prise") }, supportingText = { Text("Comprimé, gélule, ml, sachet, goutte…") },
        )
        OutlinedTextField(
            parBoite, { parBoite = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Quantité par boîte (facultatif)") },
            supportingText = {
                if (prefill.aVerifier) Text("Déduite du libellé du conditionnement : à vérifier sur ta boîte.")
                else Text("Sert au bouton « Ajouter une boîte ».")
            },
            isError = prefill.aVerifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            stock, { stock = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Stock actuel") },
            supportingText = { Text("Ce que tu as réellement en ce moment.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            seuil, { seuil = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Alerte de stock faible (jours)") },
            supportingText = { Text("Prévenir quand le stock couvre moins de ce nombre de jours.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ChampPhoto(photo) { photo = it }
        Button(
            enabled = valide, modifier = Modifier.fillMaxWidth(),
            onClick = {
                onEnregistrer(
                    Medicament(
                        photoPath = photo,
                        nom = nom.trim(), cip13 = prefill.cip13, substances = prefill.substances, forme = prefill.forme,
                        unite = unite.trim(), unitesParBoite = if (parBoite.isBlank()) null else parBoiteVal,
                        seuilJours = seuilVal!!,
                    ),
                    stockVal!!,
                )
            },
        ) { Text("Enregistrer") }
    }
}
