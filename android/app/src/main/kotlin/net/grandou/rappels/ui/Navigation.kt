package net.grandou.rappels.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.grandou.rappels.R

/** The four bottom tabs. "Rappels" = the list of what is tracked (schedule and stock of each item). */
enum class AppTab(val label: String, @DrawableRes val activeIcon: Int, @DrawableRes val inactiveIcon: Int) {
    TODAY("Aujourd'hui", R.drawable.ic_nav_today_active, R.drawable.ic_nav_today_inactive),
    HISTORY("Historique", R.drawable.ic_nav_history_active, R.drawable.ic_nav_history_inactive),
    REMINDERS("Rappels", R.drawable.ic_nav_reminders_active, R.drawable.ic_nav_reminders_inactive),
    MORE("Plus", R.drawable.ic_nav_more_active, R.drawable.ic_nav_more_inactive),
}

/** Filled icon for the activeIcon tab, outlined for the others; the label is read by TalkBack. */
@Composable
fun BottomBar(tab: AppTab, onChange: (AppTab) -> Unit) {
    NavigationBar {
        AppTab.entries.forEach { o ->
            NavigationBarItem(
                selected = o == tab,
                onClick = { onChange(o) },
                icon = { Icon(painterResource(if (o == tab) o.activeIcon else o.inactiveIcon), contentDescription = null) },
                label = { Text(o.label) },
            )
        }
    }
}
