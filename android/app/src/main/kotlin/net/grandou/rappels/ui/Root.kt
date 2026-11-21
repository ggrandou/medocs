package net.grandou.rappels.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.grandou.rappels.ThemeMode

private enum class SubPage { APPEARANCE, ABOUT }

/** UI root: bottom tabs and screens opened from "Plus" (More). */
@Composable
fun Root(appVersion: String, theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var subPage by rememberSaveable { mutableStateOf<SubPage?>(null) }

    BackHandler(enabled = subPage != null) { subPage = null }
    BackHandler(enabled = subPage == null && tab != AppTab.TODAY) { tab = AppTab.TODAY }

    when (subPage) {
        SubPage.APPEARANCE -> AppearanceScreen(theme, onTheme) { subPage = null }
        SubPage.ABOUT -> AboutScreen(appVersion) { subPage = null }
        null -> Scaffold(bottomBar = { BottomBar(tab) { tab = it } }) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                when (tab) {
                    AppTab.TODAY -> EmptyTab("Aujourd'hui")
                    AppTab.HISTORY -> EmptyTab("Historique")
                    AppTab.REMINDERS -> EmptyTab("Rappels")
                    AppTab.MORE -> MoreScreen(theme, { subPage = SubPage.APPEARANCE }, { subPage = SubPage.ABOUT })
                }
            }
        }
    }
}
