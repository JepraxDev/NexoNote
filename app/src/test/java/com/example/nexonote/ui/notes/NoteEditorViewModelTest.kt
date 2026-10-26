package com.example.nexonote.ui.notes

import androidx.lifecycle.ViewModelStore
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private val store = ViewModelStore()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    @Test fun autosaveWaitsForDebounceAndSavesLatestContent() = runTest(dispatcher) {
        val repository = FakeNotes()
        val model = model(repository)
        model.setTitle("Idea")
        advanceTimeBy(600)
        runCurrent()
        assertTrue(repository.saved.isEmpty())

        advanceTimeBy(51)
        runCurrent()
        assertEquals(1, repository.saved.size)
        assertEquals("Idea", repository.saved.single().title)

        model.setContent("Detalle")
        advanceTimeBy(651)
        runCurrent()
        assertEquals(2, repository.saved.size)
        assertEquals("Detalle", repository.saved.last().content)
        assertEquals(1L, model.uiState.value.noteId)
    }

    @Test fun saveAndExitFlushesImmediatelyAndIgnoresEmptyDraft() = runTest(dispatcher) {
        val repository = FakeNotes()
        val empty = model(repository, name = "empty")
        var exited = false
        empty.saveAndExit { exited = true }
        runCurrent()
        assertTrue(exited)
        assertTrue(repository.saved.isEmpty())

        val model = model(repository, name = "filled")
        model.setTitle("Pendiente")
        model.saveAndExit { exited = true }
        runCurrent()
        assertEquals(1, repository.saved.size)
        assertTrue(exited)
    }

    @Test fun existingNoteLoadsAndCanBeArchivedOrTrashed() = runTest(dispatcher) {
        val repository = FakeNotes(existing = Note(
            id = 7,
            title = "Guardada",
            content = "Contenido",
            type = NoteType.TEXT,
        ))
        val model = model(repository, noteId = 7L)
        runCurrent()
        assertEquals("Guardada", model.uiState.value.title)
        assertFalse(model.uiState.value.loading)

        var exited = false
        model.archiveAndExit { exited = true }
        runCurrent()
        assertEquals(listOf(7L), repository.archived)
        assertTrue(exited)

        exited = false
        model.trashAndExit { exited = true }
        runCurrent()
        assertEquals(listOf(7L), repository.trashed)
        assertTrue(exited)
    }

    private fun model(
        repository: FakeNotes,
        noteId: Long? = null,
        name: String = noteId?.toString() ?: "new",
    ) = NoteEditorViewModel(repository, noteId) { 1_000L }.also { store.put(name, it) }

    private class FakeNotes(private val existing: Note? = null) : NoteRepository {
        val saved = mutableListOf<NoteInput>()
        val archived = mutableListOf<Long>()
        val trashed = mutableListOf<Long>()
        private var nextId = 1L
        override val notes = MutableStateFlow(if (existing == null) emptyList() else listOf(existing))
        override val labels = MutableStateFlow<List<String>>(emptyList())

        override fun observeNote(id: Long): Flow<Note?> =
            flowOf(existing?.takeIf { it.id == id })

        override suspend fun save(id: Long?, input: NoteInput): Long {
            saved += input
            return id ?: nextId++
        }

        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
        override suspend fun setArchived(id: Long, archived: Boolean) { this.archived += id }
        override suspend fun moveToTrash(id: Long) { trashed += id }
        override suspend fun restore(id: Long) = Unit
        override suspend fun deletePermanently(id: Long) = Unit
        override suspend fun emptyTrash() = Unit
    }
}
