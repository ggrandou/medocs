package net.grandou.rappels.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
import net.grandou.rappels.data.Status
import java.time.LocalDateTime

/** What the screens observe: tracked items (see [ItemLine]) and today's planned occurrences. */
data class AppState(val lines: List<ItemLine>, val today: List<TodayEntry>)

private val ANSWERED = setOf(Status.DONE, Status.SKIPPED, Status.MISSED)

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

    private val today = clock.map { it.toLocalDate() }.distinctUntilChanged()

    /** Journal rows of today's planned occurrences; follows the date change at midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayRows = today.flatMapLatest { repo.occurrencesOfDay(it) }

    /** Everything the screens show (null while loading), recomputed on any change and every minute. */
    val state: StateFlow<AppState?> =
        combine(repo.itemsWithStock(), repo.observeSchedules(), todayRows, clock) { items, schedules, rows, now ->
            val handled = rows
                .filter { it.status in ANSWERED && it.scheduleTimeId != null && it.scheduledDate != null }
                .map { it.scheduleTimeId!! to it.scheduledDate!! }.toSet()
            AppState(
                // Attention first (low or out of stock), then by name
                lines = items.map { buildLine(it, schedules, now, handled) }
                    .sortedWith(compareByDescending<ItemLine> { it.needsAttention }),
                today = buildToday(items.map { it.item }, schedules, rows, now),
            )
        }
            .flowOn(Dispatchers.Default)
            .map<AppState, AppState?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Creates an item (with its initial stock, if any); [onCreated] receives the new id. */
    fun createItem(item: Item, initialStock: Double?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createItem(item)
            if (item.trackStock && initialStock != null && initialStock > 0) repo.purchase(id, initialStock)
            // Navigate only once the list knows the item, otherwise its page would look like a deleted one.
            withTimeoutOrNull(2_000) { state.first { st -> st?.lines?.any { it.item.id == id } == true } }
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

    // --- Today's answers ---

    fun markDone(e: TodayEntry) = answer(e, Status.DONE)
    fun skip(e: TodayEntry) = answer(e, Status.SKIPPED)

    fun postpone(e: TodayEntry) = answer(e, Status.SNOOZED, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000)

    /** Takes back an answer: the row disappears, and the stock it consumed comes back. */
    fun undo(e: TodayEntry) {
        e.row?.let { viewModelScope.launch { repo.cancelOccurrence(it.id) } }
    }

    private fun answer(e: TodayEntry, status: Status, snoozedUntil: Long? = null) {
        viewModelScope.launch { repo.respond(e.planned.scheduleTimeId, e.planned.date, status, snoozedUntil = snoozedUntil) }
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
