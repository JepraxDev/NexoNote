package com.example.nexonote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.EntryRepository
import com.example.nexonote.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NexoUiState(
    val entries: List<Entry> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class NexoViewModel(
    private val entries: EntryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val operation = MutableStateFlow(NexoUiState())
    val uiState = combine(entries.entries, settings.themeMode, operation) { items, theme, status ->
        status.copy(entries = items, themeMode = theme, loading = false)
    }.catch {
        emit(NexoUiState(loading = false, error = "No se pudieron cargar los datos. Reinicia la app para reintentar."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NexoUiState())

    fun create(title: String, type: EntryType, onSuccess: () -> Unit) {
        if (operation.value.saving) return
        perform {
            entries.create(title, type)
            onSuccess()
        }
    }

    fun setCompleted(entry: Entry, completed: Boolean) = perform {
        entries.setTaskCompleted(entry.id, completed)
    }

    fun setThemeMode(mode: ThemeMode) = perform { settings.setThemeMode(mode) }
    fun dismissError() { operation.update { it.copy(error = null) } }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            operation.update { it.copy(saving = true, error = null) }
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.update { it.copy(error = "No se pudo guardar el cambio. Inténtalo de nuevo.") }
            } finally {
                operation.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NexoViewModel(container.entryRepository, container.settingsRepository) }
        }
    }
}
