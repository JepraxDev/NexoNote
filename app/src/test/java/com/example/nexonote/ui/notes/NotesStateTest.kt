package com.example.nexonote.ui.notes

import com.example.nexonote.domain.model.ChecklistItem
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteFilter
import com.example.nexonote.domain.model.NoteType
import org.junit.Assert.*
import org.junit.Test

class NotesStateTest {
    @Test fun activeFilterExcludesArchivedAndTrashedNotes() {
        val state = notesState(
            listOf(
                note(1, "Activa"),
                note(2, "Archivada", archived = true),
                note(3, "Papelera", trashed = true),
            ),
            NoteFilter.ACTIVE,
            "",
        )
        assertEquals(listOf(1L), state.notes.map { it.id })
        assertEquals(1, state.activeCount)
        assertEquals(1, state.archivedCount)
        assertEquals(1, state.trashedCount)
    }

    @Test fun pinnedNotesComeFirstAndThenByModificationDate() {
        val state = notesState(
            listOf(
                note(1, "Antigua", modifiedAt = 100),
                note(2, "Fijada", modifiedAt = 50, pinned = true),
                note(3, "Reciente", modifiedAt = 200),
            ),
            NoteFilter.ACTIVE,
            "",
        )
        assertEquals(listOf(2L, 3L, 1L), state.notes.map { it.id })
    }

    @Test fun searchLooksAtTitleContentLabelsAndChecklist() {
        val notes = listOf(
            note(1, "Receta", content = "Tomate y albahaca"),
            note(2, "Viaje", labels = listOf("verano")),
            note(3, "Compra", type = NoteType.CHECKLIST, checklist = listOf(
                ChecklistItem(1, "Leche", false, 0),
            )),
        )
        assertEquals(listOf(1L), notesState(notes, NoteFilter.ACTIVE, "albahaca").notes.map { it.id })
        assertEquals(listOf(2L), notesState(notes, NoteFilter.ACTIVE, "VERANO").notes.map { it.id })
        assertEquals(listOf(3L), notesState(notes, NoteFilter.ACTIVE, "leche").notes.map { it.id })
    }

    @Test fun trashIsOrderedByDeletionDate() {
        val state = notesState(
            listOf(
                note(1, "Primera", trashed = true, trashedAt = 100),
                note(2, "Segunda", trashed = true, trashedAt = 300),
                note(3, "Activa"),
            ),
            NoteFilter.TRASH,
            "",
        )
        assertEquals(listOf(2L, 1L), state.notes.map { it.id })
    }

    @Test fun emptyQueryDoesNotFilter() {
        val state = notesState(listOf(note(1, "Nota")), NoteFilter.ACTIVE, "   ")
        assertEquals(1, state.notes.size)
        assertEquals(0, state.trashedCount)
    }

    private fun note(
        id: Long,
        title: String,
        content: String = "",
        type: NoteType = NoteType.TEXT,
        pinned: Boolean = false,
        archived: Boolean = false,
        trashed: Boolean = false,
        trashedAt: Long? = null,
        modifiedAt: Long? = null,
        labels: List<String> = emptyList(),
        checklist: List<ChecklistItem> = emptyList(),
    ) = Note(
        id = id,
        title = title,
        content = content,
        type = type,
        pinned = pinned,
        archived = archived,
        trashed = trashed,
        trashedAt = trashedAt,
        modifiedAt = modifiedAt,
        labels = labels,
        checklist = checklist,
    )
}
