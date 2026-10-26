package com.example.nexonote.ui.notes

import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteFilter
import java.util.Locale

data class NotesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filter: NoteFilter = NoteFilter.ACTIVE,
    val query: String = "",
    val notes: List<Note> = emptyList(),
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val trashedCount: Int = 0,
    val busy: Boolean = false,
    val operationError: String? = null,
)

internal fun notesState(
    allNotes: List<Note>,
    filter: NoteFilter,
    query: String,
): NotesUiState {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = allNotes.asSequence()
        .filter {
            when (filter) {
                NoteFilter.ACTIVE -> it.isActive
                NoteFilter.ARCHIVED -> it.archived && !it.trashed
                NoteFilter.TRASH -> it.trashed
            }
        }
        .filter { note ->
            normalizedQuery.isEmpty() ||
                note.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                note.content.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                note.labels.any { it.contains(normalizedQuery) } ||
                note.checklist.any { it.text.lowercase(Locale.ROOT).contains(normalizedQuery) }
        }
        .toList()
    val sorted = if (filter == NoteFilter.TRASH) {
        filtered.sortedWith(
            compareByDescending<Note> { it.trashedAt }.thenByDescending { it.id }
        )
    } else {
        filtered.sortedWith(
            compareByDescending<Note> { it.pinned }
                .thenByDescending { it.modifiedAt }
                .thenByDescending { it.id }
        )
    }
    return NotesUiState(
        loading = false,
        filter = filter,
        query = query,
        notes = sorted,
        activeCount = allNotes.count { it.isActive },
        archivedCount = allNotes.count { it.archived && !it.trashed },
        trashedCount = allNotes.count { it.trashed },
    )
}
