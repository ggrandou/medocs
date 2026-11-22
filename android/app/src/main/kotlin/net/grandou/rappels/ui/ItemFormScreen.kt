package net.grandou.rappels.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.LinearProgressIndicator
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

/** Steps of the creation wizard, in order. Stock-related steps are skipped when stock is not tracked. */
private enum class ItemStep(val title: String, val question: String) {
    NAME("Nom", "Que voulez-vous ne pas oublier ?"),
    UNIT("Unité", "Qu'est-ce qu'on compte ?"),
    STOCK("Stock", "Voulez-vous suivre le stock ?"),
    PACKAGING("Lots", "Comment est-ce conditionné ?"),
    ALERT("Alerte", "Quand faut-il vous prévenir ?"),
}

/**
 * Creation or edition of an item. Generic: a name, a unit, and optionally stock tracking.
 * Creation is a step-by-step wizard (one question per screen); edition shows every field at once.
 * On creation, the current stock is entered here; afterwards it is managed from the detail screen.
 */
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
    var stepIndex by rememberSaveable { mutableStateOf(0) }

    val stockValue = parseDecimal(stock)
    val perPackageValue = parseDecimal(perPackage)
    val daysValue = thresholdDays.trim().toIntOrNull()
    val thresholdUnitsValue = parseDecimal(thresholdUnits)
    val stockOk = stock.isBlank() || stockValue != null
    val packagingOk = perPackage.isBlank() || perPackageValue != null
    val alertOk = (thresholdUnits.isBlank() || thresholdUnitsValue != null) && daysValue != null && daysValue >= 0

    fun build(): Item {
        val base = existing ?: Item(name = "")
        return base.copy(
            name = name.trim(),
            unit = unit.trim(),
            trackStock = trackStock,
            packaging = packaging.trim().ifEmpty { null },
            unitsPerPackage = perPackageValue?.takeIf { it > 0 },
            thresholdDays = daysValue ?: base.thresholdDays,
            thresholdUnits = thresholdUnitsValue?.takeIf { it > 0 },
        )
    }

    val nameField = @Composable { NameField(name) { name = it } }
    val unitField = @Composable { UnitField(unit) { unit = it } }
    val stockFields = @Composable {
        SwitchRow("Suivre le stock", "Être prévenu avant d'en manquer", trackStock) { trackStock = it }
        if (trackStock && existing == null) NumberField("Stock actuel", stock, { stock = it }, suffix = unit.trim())
    }
    val packagingFields = @Composable {
        OutlinedTextField(
            packaging, { packaging = it }, Modifier.fillMaxWidth(), label = { Text("Nom d'un lot") }, singleLine = true,
            placeholder = { Text("boîte, sac, paquet…") },
        )
        NumberField("Quantité par lot", perPackage, { perPackage = it }, suffix = unit.trim(),
            support = "Pour ajouter un lot au stock d'un seul geste.")
    }
    val alertFields = @Composable {
        OutlinedTextField(
            thresholdDays, { thresholdDays = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
            label = { Text("Prévenir quand il reste moins de") }, singleLine = true, suffix = { Text("jours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("Calculé d'après les rythmes définis.") },
        )
        NumberField("Ou, sans rythme, moins de", thresholdUnits, { thresholdUnits = it }, suffix = unit.trim(),
            support = "Facultatif.")
    }

    if (existing != null) {
        val valid = name.isNotBlank() && unit.isNotBlank() && (!trackStock || (stockOk && packagingOk && alertOk))
        FormScaffold(
            title = "Modifier", onBack = onBack, saveLabel = "Enregistrer", saveEnabled = valid,
            onSave = { onSave(build(), stockValue) },
        ) {
            nameField()
            unitField()
            SwitchRow("Suivre le stock", "Être prévenu avant d'en manquer", trackStock) { trackStock = it }
            if (trackStock) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Conditionnement (facultatif)")
                    packagingFields()
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Alerte")
                    alertFields()
                }
            }
        }
        return
    }

    // Wizard: the steps actually shown depend on whether the stock is tracked.
    val steps = if (trackStock) ItemStep.entries else listOf(ItemStep.NAME, ItemStep.UNIT, ItemStep.STOCK)
    val index = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val last = index == steps.lastIndex
    val stepValid = when (step) {
        ItemStep.NAME -> name.isNotBlank()
        ItemStep.UNIT -> unit.isNotBlank()
        ItemStep.STOCK -> !trackStock || stockOk
        ItemStep.PACKAGING -> packagingOk
        ItemStep.ALERT -> alertOk
    }
    val goBack = { if (index > 0) stepIndex = index - 1 else onBack() }
    BackHandler(onBack = goBack)

    FormScaffold(
        title = "Nouveau rappel", onBack = goBack,
        saveLabel = if (last) "Créer" else "Suivant", saveEnabled = stepValid,
        onSave = { if (last) onSave(build(), stockValue) else stepIndex = index + 1 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Étape ${index + 1} sur ${steps.size}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (index + 1f) / steps.size }, Modifier.fillMaxWidth())
        }
        Text(step.question, style = MaterialTheme.typography.headlineSmall)
        when (step) {
            ItemStep.NAME -> nameField()
            ItemStep.UNIT -> unitField()
            ItemStep.STOCK -> stockFields()
            ItemStep.PACKAGING -> packagingFields()
            ItemStep.ALERT -> alertFields()
        }
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        name, onChange, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitField(unit: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            unit, onChange, Modifier.fillMaxWidth(), label = { Text("Unité") }, singleLine = true,
            supportingText = { Text("Ce qu'on compte : une dose, un ml, une paire…") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UNIT_SUGGESTIONS.forEach { s ->
                FilterChip(selected = unit.trim() == s, onClick = { onChange(s) }, label = { Text(s) })
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
