package net.grandou.rappels.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.grandou.rappels.data.AppDatabase
import net.grandou.rappels.data.Item
import net.grandou.rappels.data.Repository
import net.grandou.rappels.data.Schedule
import net.grandou.rappels.data.ScheduleTime
import net.grandou.rappels.data.ScheduleWithTimes
import java.time.LocalDateTime

/** Holds the repository and the data the screens observe. Actions run in [viewModelScope]. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))

    /** Current time, refreshed every minute so "next reminder" and stock levels stay fresh. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }

    /** Tracked items (null while loading): attention first (low or out of stock), then by name. */
    val lines: StateFlow<List<ItemLine>?> =
        combine(repo.itemsWithStock(), repo.observeSchedules(), clock) { items, schedules, now ->
            items.map { buildLine(it, schedules, now) }
                .sortedWith(compareByDescending<ItemLine> { it.needsAttention })
        }
            .flowOn(Dispatchers.Default)
            .map<List<ItemLine>, List<ItemLine>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Creates an item (with its initial stock, if any); [onCreated] receives the new id. */
    fun createItem(item: Item, initialStock: Double?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createItem(item)
            if (item.trackStock && initialStock != null && initialStock > 0) repo.purchase(id, initialStock)
            // Navigate only once the list knows the item, otherwise its page would look like a deleted one.
            withTimeoutOrNull(2_000) { lines.first { l -> l?.any { it.item.id == id } == true } }
            onCreated(id)
        }
    }

    fun updateItem(item: Item) {
        viewModelScope.launch { repo.updateItem(item) }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch { repo.deleteItem(item) }
    }

    fun saveSchedule(schedule: Schedule, times: List<ScheduleTime>) {
        viewModelScope.launch { repo.saveSchedule(schedule, times) }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch { repo.deleteSchedule(schedule) }
    }

    /** Pauses or resumes a schedule (its times and history are kept). */
    fun setScheduleActive(sw: ScheduleWithTimes, active: Boolean) {
        viewModelScope.launch { repo.saveSchedule(sw.schedule.copy(active = active), sw.times) }
    }

    fun purchase(itemId: Long, units: Double) {
        viewModelScope.launch { repo.purchase(itemId, units) }
    }

    fun addPackage(itemId: Long) {
        viewModelScope.launch { repo.addPackage(itemId) }
    }

    fun correctStock(itemId: Long, target: Double) {
        viewModelScope.launch { repo.correctStock(itemId, target) }
    }
}
