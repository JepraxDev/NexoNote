package com.example.nexonote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.ChecklistDraft
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.repository.NoteRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AUTOSAVE_DELAY_MS = 650L

data class ChecklistRow(
    val key: Long,
    val text: String,
    val checked: Boolean = false,
)

data class NoteEditorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val noteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.TEXT,
    val color: NoteColor = NoteColor.DEFAULT,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val checklist: List<ChecklistRow> = emptyList(),
    val labels: List<String> = emptyList(),
    val availableLabels: List<String> = emptyList(),
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val error: String? = null,
)

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val requestedId: Long?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            loading = requestedId != null,
            noteId = requestedId,
        )
    )
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var savedId: Long? = requestedId
    private var saveJob: Job? = null
    private var nextChecklistKey = 0L
    private var revision = 0L
    private var savedRevision = 0L

    init {
        viewModelScope.launch {
            repository.labels.collect { labels ->
                _uiState.update { it.copy(availableLabels = labels) }
            }
        }
        if (requestedId != null) {
            viewModelScope.launch {
                val note = repository.observeNote(requestedId).first()
                if (note == null) {
                    _uiState.update { it.copy(loading = false, missing = true) }
                } else {
                    _uiState.value = NoteEditorUiState(
                        loading = false,
                        noteId = note.id,
                        title = note.title,
                        content = note.content,
                        type = note.type,
                        color = note.color,
                        pinned = note.pinned,
                        favorite = note.favorite,
                        archived = note.archived,
                        trashed = note.trashed,
                        checklist = note.checklist.map {
                            ChecklistRow(++nextChecklistKey, it.text, it.checked)
                        },
                        labels = note.labels,
                        availableLabels = _uiState.value.availableLabels,
                        savedAt = note.modifiedAt,
                    )
                }
            }
        } else {
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun setTitle(value: String) {
        if (value.length > 120) return
        _uiState.update { it.copy(title = value) }
        contentChanged()
    }

    fun setContent(value: String) {
        _uiState.update { it.copy(content = value) }
        contentChanged()
    }

    fun setType(value: NoteType) {
        if (_uiState.value.type == value) return
        _uiState.update { it.copy(type = value) }
        contentChanged()
    }

    fun setColor(value: NoteColor) {
        if (_uiState.value.color == value) return
        _uiState.update { it.copy(color = value) }
        contentChanged()
    }

    fun addChecklistItem() {
        _uiState.update {
            it.copy(checklist = it.checklist + ChecklistRow(++nextChecklistKey, ""))
        }
        contentChanged(immediate = true)
    }

    fun setChecklistText(key: Long, value: String) {
        if (value.length > 500) return
        _uiState.update { state ->
            state.copy(checklist = state.checklist.map {
                if (it.key == key) it.copy(text = value) else it
            })
        }
        contentChanged()
    }

    fun setChecklistChecked(key: Long, checked: Boolean) {
        _uiState.update { state ->
            state.copy(checklist = state.checklist.map {
                if (it.key == key) it.copy(checked = checked) else it
            })
        }
        contentChanged(immediate = true)
    }

    fun removeChecklistItem(key: Long) {
        _uiState.update { state ->
            state.copy(checklist = state.checklist.filterNot { it.key == key })
        }
        contentChanged(immediate = true)
    }

    fun addLabel(value: String) {
        val label = value.trim().lowercase(Locale.ROOT).take(30)
        if (label.isEmpty()) return
        _uiState.update {
            if (it.labels.size >= 20 || label in it.labels) it
            else it.copy(labels = (it.labels + label).sorted())
        }
        contentChanged(immediate = true)
    }

    fun removeLabel(label: String) {
        _uiState.update { it.copy(labels = it.labels - label) }
        contentChanged(immediate = true)
    }

    fun togglePinned() {
        val pinned = !_uiState.value.pinned
        _uiState.update { it.copy(pinned = pinned) }
        contentChanged(immediate = true)
        savedId?.let { id -> runOperation { repository.setPinned(id, pinned) } }
    }

    fun toggleFavorite() {
        val favorite = !_uiState.value.favorite
        _uiState.update { it.copy(favorite = favorite) }
        contentChanged(immediate = true)
        savedId?.let { id -> runOperation { repository.setFavorite(id, favorite) } }
    }

    fun saveNow() {
        if (_uiState.value.missing) return
        scheduleSave(0)
    }

    fun saveAndExit(after: () -> Unit) {
        if (_uiState.value.missing) {
            after()
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (persist()) after()
        }
    }

    fun archiveAndExit(after: () -> Unit) {
        val archived = !_uiState.value.archived
        finishWith(after) { id -> repository.setArchived(id, archived) }
    }

    fun trashAndExit(after: () -> Unit) = finishWith(after) { id ->
        repository.moveToTrash(id)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun contentChanged(immediate: Boolean = false) {
        revision++
        scheduleSave(if (immediate) 0 else AUTOSAVE_DELAY_MS)
    }

    private fun scheduleSave(delayMs: Long) {
        if (_uiState.value.missing) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(delayMs)
            persist()
        }
    }

    private suspend fun persist(): Boolean {
        if (revision <= savedRevision) return true
        val snapshot = _uiState.value
        val revisionBeingSaved = revision
        if (savedId == null && !snapshot.toInput().hasContent) {
            savedRevision = revisionBeingSaved
            return true
        }
        _uiState.update { it.copy(saving = true, error = null) }
        return try {
            val id = repository.save(savedId, snapshot.toInput())
            savedId = id
            savedRevision = revisionBeingSaved
            _uiState.update {
                it.copy(
                    noteId = id,
                    saving = false,
                    savedAt = clock(),
                    error = null,
                )
            }
            true
        } catch (error: CancellationException) {
            _uiState.update { it.copy(saving = false) }
            throw error
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    saving = false,
                    error = "No se pudo guardar la nota. Revisa el almacenamiento e inténtalo de nuevo.",
                )
            }
            false
        }
    }

    private fun finishWith(after: () -> Unit, action: suspend (Long) -> Unit) {
        if (_uiState.value.missing) {
            after()
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (persist()) {
                val id = savedId
                if (id == null) {
                    after()
                    return@launch
                }
                try {
                    action(id)
                    after()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(error = "No se pudo mover la nota. Inténtalo de nuevo.")
                    }
                }
            }
        }
    }

    private fun runOperation(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(error = "No se pudo actualizar la nota. Inténtalo de nuevo.")
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, noteId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { NoteEditorViewModel(container.noteRepository, noteId) }
            }
    }
}

private fun NoteEditorUiState.toInput() = NoteInput(
    title = title,
    content = content,
    type = type,
    color = color,
    pinned = pinned,
    favorite = favorite,
    checklist = checklist.map { ChecklistDraft(it.text, it.checked) },
    labels = labels,
)
