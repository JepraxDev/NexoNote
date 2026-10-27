package com.example.nexonote.ui.calendar

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.*
import com.example.nexonote.domain.repository.*
import java.util.Calendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class CalendarMode(val label: String) { MONTH("Mes"), WEEK("Semana"), DAY("Día") }
enum class CalendarFilter(val label: String) { ALL("Todo"), TASKS("Tareas"), EVENTS("Eventos") }
data class CalendarUiState(
    val loading: Boolean = true, val error: String? = null,
    val selected: Long = System.currentTimeMillis(), val now: Long = System.currentTimeMillis(),
    val mode: CalendarMode = CalendarMode.MONTH, val filter: CalendarFilter = CalendarFilter.ALL,
    val from: Long = 0, val until: Long = 0,
    val activities: List<CalendarActivity> = emptyList(), val upcoming: List<CalendarActivity> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val events: EventRepository,
    tasks: TaskRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val selected = saved.getStateFlow("selected", System.currentTimeMillis())
    private val mode = saved.getStateFlow("mode", CalendarMode.MONTH.name)
    private val filter = saved.getStateFlow("filter", CalendarFilter.ALL.name)
    private val retry = MutableStateFlow(0)
    private val busyState = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)
    val busy: StateFlow<Boolean> = busyState.asStateFlow()
    val operationError: StateFlow<String?> = errorState.asStateFlow()
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
    private val data = combine(events.observeAll(), tasks.tasks) { e, t -> e to t }
    private val selection = combine(selected, mode, filter) { d, m, f -> Triple(d, CalendarMode.valueOf(m), CalendarFilter.valueOf(f)) }
    val state = retry.flatMapLatest {
        combine(data, selection, clock) { (eventList, taskList), (day, view, kind), now ->
            val start = when (view) {
                CalendarMode.MONTH -> calendarWeek(eventCalendar(day).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
                CalendarMode.WEEK -> calendarWeek(day)
                CalendarMode.DAY -> calendarDay(day)
            }
            val end = calendarAdd(start, Calendar.DAY_OF_YEAR, when (view) { CalendarMode.MONTH -> 42; CalendarMode.WEEK -> 7; else -> 1 })
            val activities = calendarActivities(eventList, taskList, start, end).filter {
                when (kind) { CalendarFilter.ALL -> true; CalendarFilter.TASKS -> it.task != null; CalendarFilter.EVENTS -> it.event != null }
            }
            CalendarUiState(false, selected = day, now = now, mode = view, filter = kind,
                from = start, until = end, activities = activities, events = eventList,
                upcoming = calendarActivities(eventList, taskList, calendarDay(now), calendarAdd(now, Calendar.DAY_OF_YEAR, 7))
                    .filter { it.endAt >= now && it.task?.isCompleted != true }.take(4))
        }.catch { emit(CalendarUiState(loading = false, error = "No se pudo cargar el calendario. Inténtalo de nuevo.")) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun select(day: Long) { saved["selected"] = day }
    fun mode(value: CalendarMode) { saved["mode"] = value.name }
    fun filter(value: CalendarFilter) { saved["filter"] = value.name }
    fun retry() { retry.update { it + 1 } }
    fun dismissError() { errorState.value = null }
    fun move(amount: Int) {
        select(calendarAdd(selected.value, when (CalendarMode.valueOf(mode.value)) {
            CalendarMode.MONTH -> Calendar.MONTH; CalendarMode.WEEK -> Calendar.WEEK_OF_YEAR; CalendarMode.DAY -> Calendar.DAY_OF_YEAR
        }, amount))
    }
    fun save(input: EventInput, target: EventOccurrence?, series: Boolean, done: () -> Unit) = perform(done) {
        if (target == null) events.save(input)
        else if (series) events.save(input, target.event.parentId ?: target.event.id)
        else if (target.event.parentId != null) events.save(input, target.event.id)
        else if (target.event.input.recurrence != TaskRecurrence.NONE) events.saveOccurrence(target.event.id, target.startAt, input)
        else events.save(input, target.event.id)
    }
    fun delete(target: EventOccurrence, series: Boolean, done: () -> Unit) = perform(done) {
        if (series) events.delete(target.event.parentId ?: target.event.id)
        else if (target.event.parentId != null) events.deleteOccurrence(target.event.parentId, requireNotNull(target.event.originalStart))
        else if (target.event.input.recurrence != TaskRecurrence.NONE) events.deleteOccurrence(target.event.id, target.startAt)
        else events.delete(target.event.id)
    }
    private fun perform(done: () -> Unit, action: suspend () -> Unit) {
        if (busyState.value) return
        busyState.value = true
        errorState.value = null
        viewModelScope.launch {
            try { action(); done() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errorState.value = error.message ?: "No se pudo guardar el evento." }
            finally { busyState.value = false }
        }
    }
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalendarViewModel(container.eventRepository, container.taskRepository, createSavedStateHandle()) }
        }
    }
}
