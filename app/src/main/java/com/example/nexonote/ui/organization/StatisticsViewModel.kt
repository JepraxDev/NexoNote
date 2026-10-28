package com.example.nexonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.OrganizationStats
import com.example.nexonote.domain.model.computeOrganizationStats
import com.example.nexonote.domain.repository.EventRepository
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.OrganizationRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ActivityPeriod(val label: String) {
    DAILY("Diaria"),
    WEEKLY("Semanal"),
    MONTHLY("Mensual"),
}

data class StatisticsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: OrganizationStats = OrganizationStats(),
    val period: ActivityPeriod = ActivityPeriod.DAILY,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    noteRepository: NoteRepository,
    taskRepository: TaskRepository,
    tableRepository: TableRepository,
    eventRepository: EventRepository,
    organizationRepository: OrganizationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val period = MutableStateFlow(ActivityPeriod.DAILY)
    private val retry = MutableStateFlow(0)
    private val content = retry.flatMapLatest {
        combine(
        combine(noteRepository.notes, taskRepository.tasks, tableRepository.tables) { notes, tasks, tables ->
            Triple(notes, tasks, tables)
        },
        combine(
            eventRepository.observeAll(),
            organizationRepository.activityTimes,
            organizationRepository.rowCreationTimes,
        ) { events, activityTimes, rows -> Triple(events, activityTimes, rows) },
    ) { first, second ->
        val (notes, tasks, tables) = first
        val (events, activityTimes, rows) = second
        computeOrganizationStats(notes, tasks, tables, events, activityTimes, rows, clock())
    }.map { stats -> stats to null as String? }
        .catch { emit(OrganizationStats(now = clock()) to "No se pudieron calcular las estadísticas.") }
        .flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<StatisticsUiState> = combine(content, period) { (stats, error), selected ->
        StatisticsUiState(loading = false, error = error, stats = stats, period = selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun selectPeriod(value: ActivityPeriod) {
        period.value = value
    }

    /** Re-subscribes after a database error so the retry button is not a no-op. */
    fun retry() {
        retry.update { it + 1 }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatisticsViewModel(
                    noteRepository = container.noteRepository,
                    taskRepository = container.taskRepository,
                    tableRepository = container.tableRepository,
                    eventRepository = container.eventRepository,
                    organizationRepository = container.organizationRepository,
                )
            }
        }
    }
}
