package com.example.nexonote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteFilter
import com.example.nexonote.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class NotesOperation(
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(private val repository: NoteRepository) : ViewModel() {
    private val filter = MutableStateFlow(NoteFilter.ACTIVE)
    private val query = MutableStateFlow("")
    private val retry = MutableStateFlow(0)
    private val operation = MutableStateFlow(NotesOperation())

    private val content = retry.flatMapLatest {
        combine(repository.notes, filter, query, ::notesState)
            .onStart {
                emit(NotesUiState(
                    loading = true,
                    filter = filter.value,
                    query = query.value,
                ))
            }
            .catch {
                emit(NotesUiState(
                    loading = false,
                    error = "No pudimos cargar tus notas. Comprueba el almacenamiento e inténtalo de nuevo.",
                    filter = filter.value,
                    query = query.value,
                ))
            }
    }

    val uiState: StateFlow<NotesUiState> = combine(content, operation) { data, status ->
        data.copy(busy = status.busy, operationError = status.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setFilter(value: NoteFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun retry() {
        retry.update { it + 1 }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    fun togglePinned(note: Note) = perform {
        repository.setPinned(note.id, !note.pinned)
    }

    fun toggleFavorite(note: Note) = perform {
        repository.setFavorite(note.id, !note.favorite)
    }

    fun toggleArchived(note: Note) = perform {
        repository.setArchived(note.id, !note.archived)
    }

    fun moveToTrash(note: Note) = perform {
        repository.moveToTrash(note.id)
    }

    fun restore(note: Note) = perform {
        repository.restore(note.id)
    }

    fun deletePermanently(note: Note) = perform {
        repository.deletePermanently(note.id)
    }

    fun emptyTrash() = perform {
        repository.emptyTrash()
    }

    private fun perform(action: suspend () -> Unit) {
        if (operation.value.busy) return
        viewModelScope.launch {
            operation.value = NotesOperation(busy = true)
            try {
                action()
                operation.value = NotesOperation()
            } catch (error: CancellationException) {
                operation.value = NotesOperation()
                throw error
            } catch (_: Exception) {
                operation.value = NotesOperation(
                    error = "No se pudo completar la acción. Inténtalo de nuevo."
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(container.noteRepository) }
        }
    }
}
