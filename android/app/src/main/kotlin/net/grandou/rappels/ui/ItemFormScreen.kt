package net.grandou.rappels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.grandou.rappels.R
import net.grandou.rappels.data.Item

private val UNIT_SUGGESTIONS = listOf("unité", "dose", "pièce", "paire", "ml", "g")

/**
 * Creation or edition of an item. Generic: a name, a unit, and optionally stock tracking.
 * On creation, the current stock is entered here; afterwards it is managed from the detail screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemFormScreen(existing: Item?, onBack: () -> Unit, onSave: (Item, Double?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "unité") }
    var trackStock by rememberSaveable { mutableStateOf(existing?.trackStock ?: true) }
    var stock by rememberSaveable { mutableStateOf("") }
    var packaging by rememberSaveable { mutableStateOf(existing?.packaging ?: "") }
    var perPackage by rememberSaveable { mutableStateOf(existing?.unitsPerPackage?.let(::formatQuantity) ?: "") }
    var thresholdDays by rememberSaveable { mutableStateOf((existing?.thresholdDays ?: 7).toString()) }
    var thresholdUnits by rememberSaveable { mutableStateOf(existing?.thresholdUnits?.let(::formatQuantity) ?: "") }

    val stockValue = parseDecimal(stock)
    val perPackageValue = parseDecimal(perPackage)
    val daysValue = thresholdDays.trim().toIntOrNull()
    val thresholdUnitsValue = parseDecimal(thresholdUnits)
    val valid = name.isNotBlank() && unit.isNotBlank() &&
        (!trackStock || (
            (stock.isBlank() || stockValue != null) &&
                (perPackage.isBlank() || perPackageValue != null) &&
                (thresholdUnits.isBlank() || thresholdUnitsValue != null) &&
                daysValue != null && daysValue >= 0))

    FormScaffold(
        title = if (existing == null) "Nouveau rappel" else "Modifier",
        onBack = onBack,
        saveLabel = if (existing == null) "Créer" else "Enregistrer",
        saveEnabled = valid,
        onSave = {
            val base = existing ?: Item(name = "")
            val item = base.copy(
                name = name.trim(),
                unit = unit.trim(),
                trackStock = trackStock,
                packaging = packaging.trim().ifEmpty { null },
                unitsPerPackage = perPackageValue?.takeIf { it > 0 },
                thresholdDays = daysValue ?: base.thresholdDays,
                thresholdUnits = thresholdUnitsValue?.takeIf { it > 0 },
            )
            onSave(item, stockValue)
        },
    ) {
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Unité") }, singleLine = true,
                supportingText = { Text("Ce qu'on compte : une dose, un ml, une paire…") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UNIT_SUGGESTIONS.forEach { s ->
                    FilterChip(selected = unit.trim() == s, onClick = { unit = s }, label = { Text(s) })
                }
            }
        }

        SwitchRow("Suivre le stock", "Être prévenu avant d'en manquer", trackStock) { trackStock = it }

        if (trackStock) {
            if (existing == null) {
                NumberField("Stock actuel", stock, { stock = it }, suffix = unit.trim())
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Conditionnement (facultatif)")
                OutlinedTextField(
                    packaging, { packaging = it }, Modifier.fillMaxWidth(), label = { Text("Nom d'un lot") }, singleLine = true,
                    placeholder = { Text("boîte, sac, paquet…") },
                )
                NumberField("Quantité par lot", perPackage, { perPackage = it }, suffix = unit.trim(),
                    support = "Pour ajouter un lot au stock d'un seul geste.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Alerte")
                OutlinedTextField(
                    thresholdDays, { thresholdDays = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
                    label = { Text("Prévenir quand il reste moins de") }, singleLine = true, suffix = { Text("jours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Calculé d'après les rythmes définis.") },
                )
                NumberField("Ou, sans rythme, moins de", thresholdUnits, { thresholdUnits = it }, suffix = unit.trim(),
                    support = "Facultatif.")
            }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String = "", support: String? = null) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = support?.let { { Text(it) } },
        isError = value.isNotBlank() && parseDecimal(value) == null,
    )
}

@Composable
internal fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, null)
    }
}
