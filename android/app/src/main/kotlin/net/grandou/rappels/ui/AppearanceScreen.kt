package net.grandou.rappels.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.grandou.rappels.ThemeMode

/** Choice of light mode, dark mode or the system's. */
@Composable
fun AppearanceScreen(theme: ThemeMode, onChoice: (ThemeMode) -> Unit, onBack: () -> Unit) {
    SubScreen("Apparence", onBack) {
        androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = mode == theme, onClick = { onChoice(mode) }, role = Role.RadioButton)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == theme, onClick = null)
                    Text(mode.label, Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
