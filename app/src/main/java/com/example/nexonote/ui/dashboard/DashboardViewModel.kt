package com.example.nexonote.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.repository.ActivityRepository
import com.example.nexonote.domain.repository.EntryRepository
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.SettingsRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private data class NameSaveState(val saving: Boolean = false, val error: String? = null)

private data class DashboardCore(
    val entries: List<com.example.nexonote.domain.model.Entry>,
    val notes: List<com.example.nexonote.domain.model.Note>,
    val tasks: List<com.example.nexonote.domain.model.Task>,
    val activities: List<com.example.nexonote.domain.model.Activity>,
    val name: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val entries: EntryRepository,
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val tables: TableRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val nameSave = MutableStateFlow(NameSaveState())
    private val time = flow {
        while (true) {
            emit(clock())
            delay(30_000)
        }
    }
    private val core = combine(
        entries.entries,
        notes.notes,
        tasks.tasks,
        activities.recent,
        settings.displayName,
    ) { entryItems, noteItems, taskItems, events, name ->
        DashboardCore(entryItems, noteItems, taskItems, events, name)
    }
    private val tableData = combine(tables.tables, tables.recentRows) { items, rows ->
        items to rows
    }
    private val content = retry.flatMapLatest {
        combine(core, time, tableData) { data, now, tableItems ->
            dashboardState(
                entries = data.entries,
                notes = data.notes,
                tasks = data.tasks,
                activities = data.activities,
                name = data.name,
                now = now,
                tables = tableItems.first,
                recentTableRows = tableItems.second,
            )
        }.onStart { emit(DashboardUiState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(DashboardUiState(loading = false, error = "No pudimos cargar tu espacio. Comprueba el almacenamiento e inténtalo de nuevo."))
            }
    }
    val uiState: StateFlow<DashboardUiState> = combine(content, nameSave) { data, operation ->
        data.copy(savingName = operation.saving, nameError = operation.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun retry() { retry.update { it + 1 } }
    fun clearNameError() { nameSave.update { it.copy(error = null) } }

    fun saveName(name: String, onSaved: () -> Unit) {
        if (nameSave.value.saving) return
        nameSave.value = NameSaveState(saving = true)
        viewModelScope.launch {
            try {
                settings.setDisplayName(name)
                nameSave.value = NameSaveState()
                onSaved()
            } catch (error: CancellationException) {
                nameSave.value = NameSaveState()
                throw error
            } catch (_: Exception) {
                nameSave.value = NameSaveState(error = "No se pudo guardar el nombre. Vuelve a intentarlo.")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    container.entryRepository,
                    container.noteRepository,
                    container.taskRepository,
                    container.activityRepository,
                    container.settingsRepository,
                    container.tableRepository,
                )
            }
        }
    }
}
