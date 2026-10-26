package com.example.nexonote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalNoteRepository
import com.example.nexonote.domain.model.ChecklistDraft
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesRepositoryTest {
    private lateinit var database: NexoDatabase
    private lateinit var repository: LocalNoteRepository
    private var now = 1_000L

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NexoDatabase::class.java,
        ).build()
        repository = LocalNoteRepository(database) { now }
    }

    @After fun teardown() {
        database.close()
    }

    @Test fun createAndUpdateKeepEntrySummaryActivityChecklistAndLabels() = runBlocking {
        val id = repository.save(
            null,
            NoteInput(
                title = "Lista de viaje",
                content = "",
                type = NoteType.CHECKLIST,
                color = NoteColor.BLUE,
                pinned = true,
                favorite = false,
                checklist = listOf(
                    ChecklistDraft("Pasaporte", true),
                    ChecklistDraft("Cargador", false),
                ),
                labels = listOf("Viajes", " Verano "),
            ),
        )
        assertTrue(id > 0)
        val created = repository.observeNote(id).first()!!
        assertEquals(id, created.id)
        assertEquals("Lista de viaje", created.title)
        assertEquals(NoteType.CHECKLIST, created.type)
        assertEquals(NoteColor.BLUE, created.color)
        assertTrue(created.pinned)
        assertEquals(listOf("verano", "viajes"), created.labels)
        assertEquals(listOf("Pasaporte", "Cargador"), created.checklist.map { it.text })
        assertEquals(1, created.checkedItems)
        assertEquals(2, created.totalItems)
        assertEquals(now, created.createdAt)
        assertEquals(now, created.modifiedAt)
        assertEquals("Lista de viaje", database.entryDao().observeAll().first().single().title)
        assertEquals(
            "NOTE_CREATED",
            database.entryDao().observeRecentActivity(5).first().single().kind,
        )

        now = 2_000L
        repository.save(
            id,
            NoteInput(
                title = "",
                content = "Contenido actualizado",
                type = NoteType.TEXT,
                color = NoteColor.GREEN,
                pinned = false,
                favorite = true,
                labels = listOf("ideas"),
            ),
        )
        val updated = repository.observeNote(id).first()!!
        assertEquals("", updated.title)
        assertEquals("Contenido actualizado", updated.content)
        assertEquals(NoteType.TEXT, updated.type)
        assertTrue(updated.favorite)
        assertFalse(updated.pinned)
        assertTrue(updated.checklist.isEmpty())
        assertEquals(listOf("ideas"), updated.labels)
        assertEquals(1_000L, updated.createdAt)
        assertEquals(2_000L, updated.modifiedAt)
        assertEquals("Contenido actualizado", database.entryDao().observeAll().first().single().title)
        assertEquals(1, database.entryDao().observeRecentActivity(5).first().size)
    }

    @Test fun trashRestoreArchiveAndDeleteKeepLifecycleConsistent() = runBlocking {
        val id = repository.save(null, input("Nota temporal"))
        repository.setPinned(id, true)
        repository.setFavorite(id, true)
        assertTrue(repository.observeNote(id).first()!!.pinned)
        assertTrue(repository.observeNote(id).first()!!.favorite)
        repository.setArchived(id, true)
        assertTrue(repository.observeNote(id).first()!!.archived)
        assertFalse(repository.observeNote(id).first()!!.isActive)

        repository.moveToTrash(id)
        val trashed = repository.observeNote(id).first()!!
        assertTrue(trashed.trashed)
        assertFalse(trashed.archived)
        assertEquals(now, trashed.trashedAt)

        repository.restore(id)
        val restored = repository.observeNote(id).first()!!
        assertFalse(restored.trashed)
        assertNull(restored.trashedAt)
        assertTrue(restored.isActive)

        repository.deletePermanently(id)
        assertNull(repository.observeNote(id).first())
        assertTrue(repository.notes.first().isEmpty())
        assertTrue(database.entryDao().observeAll().first().isEmpty())
    }

    @Test fun emptyTrashOnlyDeletesTrashedNotes() = runBlocking {
        val active = repository.save(null, input("Activa"))
        val trashedOne = repository.save(null, input("Primera"))
        val trashedTwo = repository.save(null, input("Segunda"))
        repository.moveToTrash(trashedOne)
        repository.moveToTrash(trashedTwo)
        repository.emptyTrash()
        assertEquals(listOf(active), repository.notes.first().map { it.id })
        assertNotNull(repository.observeNote(active).first())
    }

    @Test fun emptyNewNoteCannotBeCreated() = runBlocking {
        try {
            repository.save(null, input(""))
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(repository.notes.first().isEmpty())
    }

    private fun input(title: String) = NoteInput(
        title = title,
        content = "",
        type = NoteType.TEXT,
        color = NoteColor.DEFAULT,
        pinned = false,
        favorite = false,
    )
}
