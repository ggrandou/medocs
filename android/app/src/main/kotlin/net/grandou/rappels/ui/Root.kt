package net.grandou.rappels.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.grandou.rappels.ThemeMode

private enum class SubPage { BACKUP, RESTORE, APPEARANCE, PERMISSIONS, ABOUT }

/**
 * Screens of the "Rappels" tab, encoded as text so that they survive rotation:
 * "detail/<item>", "item/<item or 0>", "schedule/<item>/<schedule or 0>". Empty = the list.
 */
private fun backOf(route: String): String {
    val p = route.split("/")
    return when {
        p[0] == "schedule" -> "detail/${p[1]}"
        p[0] == "item" && p[1] != "0" -> "detail/${p[1]}"
        else -> ""
    }
}

/** UI root: bottom tabs, the "Rappels" screens, and screens opened from "Plus" (More). */
@Composable
fun Root(
    appVersion: String,
    theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
    persistent: Boolean, onPersistent: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var subPage by rememberSaveable { mutableStateOf<SubPage?>(null) }
    var route by rememberSaveable { mutableStateOf("") }
    val vm: AppViewModel = viewModel()
    val backupVm: BackupViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val lines = state?.lines
    val actions = EntryActions(vm::markDone, vm::skip, vm::postpone, vm::undo, vm::cancelOneOff, vm::addOneOff)

    BackHandler(enabled = subPage != null) { subPage = null }
    BackHandler(enabled = subPage == null && route.isNotEmpty()) { route = backOf(route) }
    BackHandler(enabled = subPage == null && route.isEmpty() && tab != AppTab.TODAY) { tab = AppTab.TODAY }

    if (subPage == null && route.isNotEmpty()) {
        RemindersRoute(route, lines, vm) { route = it }
        return
    }

    when (subPage) {
        SubPage.BACKUP -> BackupScreen(backupVm) { subPage = null }
        SubPage.RESTORE -> RestoreScreen(backupVm) { subPage = null }
        SubPage.APPEARANCE -> AppearanceScreen(theme, onTheme) { subPage = null }
        SubPage.PERMISSIONS -> PermissionsScreen { subPage = null }
        SubPage.ABOUT -> AboutScreen(appVersion) { subPage = null }
        null -> Scaffold(bottomBar = { BottomBar(tab) { tab = it } }) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                when (tab) {
                    AppTab.TODAY -> TodayScreen(
                        state, actions,
                        onOpenReminders = { tab = AppTab.REMINDERS },
                    )
                    AppTab.HISTORY -> HistoryScreen(
                        state, vm::rowsBetween, vm::oneOffsBetween, actions,
                    )
                    AppTab.REMINDERS -> RemindersScreen(lines, { route = "item/0" }, { route = "detail/${it.item.id}" })
                    AppTab.MORE -> MoreScreen(
                        theme, persistent, onPersistent,
                        onBackup = { backupVm.resetExport(); backupVm.refreshCurrent(); subPage = SubPage.BACKUP },
                        onRestore = { backupVm.resetRestore(); backupVm.refreshCurrent(); subPage = SubPage.RESTORE },
                        onAppearance = { subPage = SubPage.APPEARANCE },
                        onPermissions = { subPage = SubPage.PERMISSIONS }, onAbout = { subPage = SubPage.ABOUT },
                    )
                }
            }
        }
    }
}

/** Full-screen pages of the "Rappels" tab: item detail, item form, schedule form. */
@Composable
private fun RemindersRoute(route: String, lines: List<ItemLine>?, vm: AppViewModel, go: (String) -> Unit) {
    val p = route.split("/")
    val itemId = p[1].toLong()
    val line = lines?.firstOrNull { it.item.id == itemId }

    // The item was deleted (or is not loaded yet): back to the list once the data is there.
    if (itemId != 0L && line == null) {
        if (lines != null) androidx.compose.runtime.LaunchedEffect(route) { go("") }
        return
    }

    when (p[0]) {
        "item" -> ItemFormScreen(
            existing = line?.item,
            onBack = { go(backOf(route)) },
            onSave = { item, stock ->
                if (line == null) vm.createItem(item, stock) { go("detail/$it") }
                else { vm.updateItem(item); go(backOf(route)) }
            },
        )
        "schedule" -> {
            val scheduleId = p[2].toLong()
            ScheduleFormScreen(
                item = line!!.item,
                existing = line.schedules.firstOrNull { it.schedule.id == scheduleId },
                onBack = { go(backOf(route)) },
                onSave = { schedule, times -> vm.saveSchedule(schedule, times); go(backOf(route)) },
                onDelete = { vm.deleteSchedule(it); go(backOf(route)) },
            )
        }
        else -> ItemDetailScreen(
            line = line!!,
            onBack = { go("") },
            onEdit = { go("item/$itemId") },
            onDelete = { vm.deleteItem(line.item); go("") },
            onAddSchedule = { go("schedule/$itemId/0") },
            onEditSchedule = { go("schedule/$itemId/${it.schedule.id}") },
            onToggleSchedule = { sw, on -> vm.setScheduleActive(sw, on) },
            onPurchase = { vm.purchase(itemId, it) },
            onAddPackage = { vm.addPackage(itemId) },
            onCorrectStock = { vm.correctStock(itemId, it) },
        )
    }
}
