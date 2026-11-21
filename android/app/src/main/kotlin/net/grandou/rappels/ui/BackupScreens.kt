package net.grandou.rappels.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.grandou.rappels.R
import net.grandou.rappels.data.BackupSource

const val MIN_PASSWORD_LENGTH = 8

private fun plural(n: Int, one: String, many: String) = "$n ${if (n <= 1) one else many}"

/** "3 éléments · 2 rythmes · 120 entrées d'historique". */
private fun BackupSummary.text(): String = listOfNotNull(
    plural(items, "élément", "éléments"),
    plural(schedules, "rythme", "rythmes"),
    plural(journal, "entrée d'historique", "entrées d'historique"),
    if (photos > 0) plural(photos, "photo", "photos") else null,
).joinToString(" · ")

// --- Save ---

/** "Sauvegarder": writes one encrypted file, chosen by the user through the system picker. */
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val state by vm.export.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.export(uri) else vm.wipePassword()
    }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val valid = password.length >= MIN_PASSWORD_LENGTH && password == confirmation

    SubScreen("Sauvegarder", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val done = state.done
            if (done != null) {
                ResultCard(successTone(), R.drawable.ic_check, "Sauvegarde enregistrée", done.text())
                Text(
                    "Gardez ce fichier hors du téléphone (ordinateur, clé USB, cloud personnel) : c'est lui qui vous protège si le téléphone est perdu.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBack, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Terminé") }
                return@Column
            }

            IntroCard(
                R.drawable.ic_lock, "Un fichier chiffré, que vous gardez",
                "Toutes vos données dans un seul fichier, protégé par un mot de passe de votre choix. Aucun compte, aucun cloud : rien ne quitte le téléphone sans vous.",
            )

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dernière sauvegarde", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (last == 0L) "Jamais" else formatInstant(last), style = MaterialTheme.typography.titleMedium)
                    current?.let {
                        Text("À sauvegarder : ${it.text()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionTitle("Mot de passe du fichier", Modifier.semantics { heading() })
            PasswordField(
                password, { password = it }, "Mot de passe", visible, { visible = !visible },
                hint = if (password.isEmpty() || password.length >= MIN_PASSWORD_LENGTH) "$MIN_PASSWORD_LENGTH caractères minimum"
                else "Encore ${MIN_PASSWORD_LENGTH - password.length} caractère(s)",
            )
            PasswordField(
                confirmation, { confirmation = it }, "Confirmer le mot de passe", visible, { visible = !visible },
                error = if (confirmation.isNotEmpty() && confirmation != password) "Les mots de passe ne correspondent pas" else null,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    "Ce mot de passe n'est enregistré nulle part et personne ne peut le retrouver : perdu, le fichier est illisible.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.error?.let { ResultCard(dangerTone(), R.drawable.ic_warning, "Échec", it) }

            if (state.working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Chiffrement en cours…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(
                enabled = valid && !state.working,
                onClick = {
                    vm.holdPassword(password.toCharArray())
                    picker.launch(vm.suggestedFileName())
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_more_backup), contentDescription = null, Modifier.size(20.dp))
                Text("Enregistrer la sauvegarde", Modifier.padding(start = 8.dp))
            }
        }
    }
}

// --- Restore ---

/** "Restaurer": pick a file, unlock it, review its content, then replace everything. Understands v1 files. */
@Composable
fun RestoreScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val state by vm.restore.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pick(uri) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    SubScreen("Restaurer", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val s = state) {
                RestoreState.Idle -> {
                    IntroCard(
                        R.drawable.ic_more_restore, "Retrouver vos données",
                        "Choisissez un fichier de sauvegarde. Les fichiers de l'ancienne version, « Mes Médocs », sont reconnus et convertis automatiquement.",
                    )
                    Text(
                        "Rien n'est modifié avant que vous ayez vu le contenu du fichier et confirmé.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_file), contentDescription = null, Modifier.size(20.dp))
                        Text("Choisir un fichier", Modifier.padding(start = 8.dp))
                    }
                }

                is RestoreState.Picked -> {
                    FileCard(s.fileName) { password = ""; picker.launch(arrayOf("*/*")) }
                    SectionTitle("Mot de passe du fichier", Modifier.semantics { heading() })
                    PasswordField(password, { password = it }, "Mot de passe", visible, { visible = !visible }, error = s.error)
                    Button(
                        enabled = password.isNotEmpty(),
                        onClick = { vm.unlock(password.toCharArray()); password = "" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, Modifier.size(20.dp))
                        Text("Déverrouiller", Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { password = ""; vm.resetRestore() }, Modifier.fillMaxWidth()) { Text("Annuler") }
                }

                RestoreState.Checking -> Progress("Lecture de la sauvegarde…")
                RestoreState.Restoring -> Progress("Restauration en cours…")

                is RestoreState.Ready -> {
                    FileCard(s.fileName, onChange = null)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Contenu de la sauvegarde", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (s.source) {
                                        BackupSource.CURRENT -> "Sauvegarde de T'as pensé à ?" + (s.createdAt?.let { " du ${formatInstant(it)}" } ?: "")
                                        BackupSource.V1_MEDOCS -> "Ancienne version « Mes Médocs » : sera convertie automatiquement"
                                    },
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            CountRow("Éléments suivis", s.summary.items)
                            CountRow("Rythmes", s.summary.schedules)
                            CountRow("Historique", s.summary.journal)
                            if (s.summary.photos > 0) CountRow("Photos", s.summary.photos)
                        }
                    }
                    val now = current
                    ResultCard(
                        dangerTone(), R.drawable.ic_warning, "Vos données actuelles seront remplacées",
                        if (now != null && now.items + now.journal > 0) "Sur ce téléphone : ${now.text()}. Cela ne peut pas être annulé."
                        else "Ce téléphone ne contient encore rien : il n'y a rien à perdre.",
                    )
                    Button(
                        onClick = vm::confirmRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Remplacer mes données") }
                    OutlinedButton(onClick = vm::resetRestore, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Annuler") }
                }

                is RestoreState.Done -> {
                    ResultCard(successTone(), R.drawable.ic_check, "Données restaurées", s.summary.text())
                    Button(onClick = { vm.resetRestore(); onBack() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Terminé") }
                }

                is RestoreState.Failed -> {
                    ResultCard(dangerTone(), R.drawable.ic_warning, "Restauration impossible", s.message + " Rien n'a été modifié.")
                    Button(onClick = vm::resetRestore, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choisir un autre fichier") }
                }
            }
        }
    }
}

// --- Pieces ---

@Composable
private fun IntroCard(@DrawableRes icon: Int, title: String, text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** A result in words and icon, on a status tone (green or red): the color is never the only signal. */
@Composable
private fun ResultCard(tone: Tone, @DrawableRes icon: Int, title: String, text: String) {
    Card(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusLine(title, icon, tone.content)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = tone.content)
        }
    }
}

@Composable
private fun FileCard(name: String, onChange: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (onChange != null) TextButton(onClick = onChange) { Text("Changer") }
        }
    }
}

@Composable
private fun CountRow(label: String, count: Int) {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Progress(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    hint: String? = null,
    error: String? = null,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                )
            }
        },
    )
}
