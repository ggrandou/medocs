package net.grandou.medocs.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.grandou.medocs.R

/** Barre d'onglets : icône pleine pour l'onglet actif, en contour pour l'autre ; le libellé est lu par TalkBack. */
@Composable
fun BarreNavigation(onglet: Int, onChange: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = onglet == 0, onClick = { onChange(0) },
            icon = {
                Icon(painterResource(if (onglet == 0) R.drawable.ic_nav_aujourdhui_actif else R.drawable.ic_nav_aujourdhui_inactif), contentDescription = null)
            },
            label = { Text("Aujourd'hui") },
        )
        NavigationBarItem(
            selected = onglet == 1, onClick = { onChange(1) },
            icon = {
                Icon(painterResource(if (onglet == 1) R.drawable.ic_nav_medicaments_actif else R.drawable.ic_nav_medicaments_inactif), contentDescription = null)
            },
            label = { Text("Médicaments") },
        )
        NavigationBarItem(
            selected = onglet == 2, onClick = { onChange(2) },
            icon = {
                Icon(painterResource(if (onglet == 2) R.drawable.ic_nav_plus_actif else R.drawable.ic_nav_plus_inactif), contentDescription = null)
            },
            label = { Text("Plus") },
        )
    }
}
