package com.example.nexonote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalEventRepository
import com.example.nexonote.data.repository.LocalNoteRepository
import com.example.nexonote.data.repository.LocalOrganizationRepository
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.EventInput
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.testing.FakeReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizationRepositoryTest {
    private lateinit var database: NexoDatabase
    private lateinit var repository: LocalOrganizationRepository
    private var now = 1_800_000_000_000L

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NexoDatabase::class.java,
        ).build()
        repository = LocalOrganizationRepository(database) { now }
    }

    @After fun teardown() {
        database.close()
    }

    @Test fun deletingFolderMovesChildrenAndContentsToItsParent() = runBlocking {
        val root = repository.saveFolder(name = "Trabajo", color = "#6750A4")
        val child = repository.saveFolder(name = "Proyectos", color = "#006A6A", parentId = root)
        val grandchild = repository.saveFolder(name = "NexoNote", color = "#B3261E", parentId = child)
        val noteId = note(folderId = child)
        val taskId = task(folderId = child)

        repository.deleteFolder(child)

        val remaining = repository.folders.first()
        assertEquals(listOf(root, grandchild), remaining.map { it.id }.sorted())
        assertEquals(root, remaining.first { it.id == grandchild }.parentId)
        assertEquals(root, database.organizationDao().findNoteFolder(noteId))
        assertEquals(root, database.organizationDao().findTaskFolder(taskId))

        repository.deleteFolder(root)
        assertNull(database.organizationDao().findNoteFolder(noteId))
        assertNull(database.organizationDao().findTaskFolder(taskId))
        assertEquals(1, repository.folders.first().size)
        assertNull(repository.folders.first().single().parentId)
    }

    @Test fun folderMovementsCannotCreateCycles() = runBlocking {
        val root = repository.saveFolder(name = "Raíz", color = "#6750A4")
        val child = repository.saveFolder(name = "Hija", color = "#006A6A", parentId = root)
        val grandchild = repository.saveFolder(name = "Nieta", color = "#0061A4", parentId = child)

        // A legitimate move still works: the grandchild becomes a direct child of the root.
        repository.saveFolder(grandchild, "Nieta", "", FolderIcon.FOLDER, "#0061A4", root)
        assertEquals(root, database.organizationDao().findFolder(grandchild)?.parentId)

        // Cycles are rejected: a folder can never become a descendant of itself.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveFolder(root, "Raíz", "", FolderIcon.FOLDER, "#6750A4", grandchild)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveFolder(root, "Raíz", "", FolderIcon.FOLDER, "#6750A4", root)
            }
        }
        assertNull(database.organizationDao().findFolder(root)?.parentId)
        assertEquals(grandchild, database.organizationDao().findFolder(grandchild)?.id)
    }

    @Test fun moveToFolderMovesEveryContentTypeAndRejectsMissingTargets() = runBlocking {
        val source = repository.saveFolder(name = "Origen", color = "#6750A4")
        val target = repository.saveFolder(name = "Destino", color = "#006A6A")
        val noteId = note(folderId = source)
        val taskId = task(folderId = source)
        val tableId = table(folderId = source)
        val eventId = event(folderId = source)

        listOf(
            ContentType.NOTE to noteId,
            ContentType.TASK to taskId,
            ContentType.TABLE to tableId,
            ContentType.EVENT to eventId,
        ).forEach { (type, id) ->
            repository.moveToFolder(type, id, target)
            val item = repository.search(SearchFilters(folderId = target)).first().single { it.type == type && it.id == id }
            assertEquals(target, item.folderId)
        }
        assertEquals(4, repository.folders.first().first { it.id == target }.itemCount)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.moveToFolder(ContentType.NOTE, noteId, 999_999L) }
        }

        repository.moveToFolder(ContentType.NOTE, noteId, null)
        assertNull(
            repository.search(SearchFilters()).first()
                .single { it.type == ContentType.NOTE && it.id == noteId }.folderId
        )
    }

    @Test fun categoriesAndTagsApplyToEveryContentType() = runBlocking {
        val category = repository.saveCategory(name = "Trabajo", color = "#775A00")
        val tag = repository.saveTag(name = "Trabajo", color = "#006A6A")
        val noteId = note(labels = listOf("trabajo"))
        val taskId = task(labels = listOf("trabajo"))
        val tableId = table(tags = listOf("trabajo"))
        val eventId = event(tags = listOf("trabajo"))

        listOf(ContentType.NOTE to noteId, ContentType.TASK to taskId, ContentType.TABLE to tableId, ContentType.EVENT to eventId)
            .forEach { (type, id) -> assertTrue(repository.assignCategory(type, id, category)) }

        assertEquals(1, repository.tags.first().size)
        assertEquals(tag, repository.tags.first().single().id)
        assertEquals(4, repository.tags.first().single().itemCount)
        assertEquals(4, repository.categories.first().single().itemCount)

        val byTag = repository.search(SearchFilters(tagIds = setOf(tag))).first()
        assertEquals(setOf(ContentType.NOTE, ContentType.TASK, ContentType.TABLE, ContentType.EVENT), byTag.map { it.type }.toSet())
        val byCategory = repository.search(SearchFilters(categoryId = category)).first()
        assertEquals(4, byCategory.size)
        val byText = repository.search(SearchFilters(query = "trabajo")).first()
        assertEquals(4, byText.size)
    }

    @Test fun searchFindsContentByTagNameNotOnlyTitleOrBody() = runBlocking {
        val noteId = note(title = "Lista del súper", labels = listOf("hogar"))
        val results = repository.search(SearchFilters(query = "hogar")).first()
        assertEquals(noteId, results.single().id)
        assertEquals(ContentType.NOTE, results.single().type)
        assertTrue(results.single().tags.any { it.name == "hogar" })
    }

    @Test fun favoritesAreUnifiedAndCanBeRemovedIndividually() = runBlocking {
        val noteId = note(favorite = true)
        val taskId = task(favorite = true)
        val tableId = table(favorite = true)
        val eventId = event(favorite = true)
        assertEquals(4, repository.favorites.first().size)

        repository.setFavorite(ContentType.TASK, taskId, false)
        val remaining = repository.favorites.first()
        assertEquals(3, remaining.size)
        assertTrue(remaining.none { it.type == ContentType.TASK && it.id == taskId })
        assertEquals(1, remaining.count { it.type == ContentType.NOTE && it.id == noteId })
        assertEquals(1, remaining.count { it.type == ContentType.TABLE && it.id == tableId })
        assertEquals(1, remaining.count { it.type == ContentType.EVENT && it.id == eventId })
    }

    @Test fun savedViewsRoundTripAndDropDeletedTags() = runBlocking {
        val tag = repository.saveTag(name = "importante", color = "#B3261E")
        val category = repository.saveCategory(name = "Personal", color = "#4E6A2F")
        val filters = SearchFilters(
            query = "informe",
            categoryId = category,
            tagIds = setOf(tag),
            types = setOf(ContentType.NOTE, ContentType.TASK),
            favoriteOnly = true,
        )
        val viewId = repository.saveSavedView(null, "Importantes", filters)
        val saved = repository.savedViews.first().single()
        assertEquals(viewId, saved.id)
        assertEquals(filters, saved.filters)

        repository.deleteTag(tag)
        val refreshed = repository.savedViews.first().single()
        assertTrue(refreshed.filters.tagIds.isEmpty())
        assertEquals("informe", refreshed.filters.query)
    }

    @Test fun statisticsInputsTrackActivitiesAndRowCreation() = runBlocking {
        note()
        table()
        assertTrue(repository.activityTimes.first().isNotEmpty())
        val tableRepository = LocalTableRepository(database) { now }
        val tableId = database.tableDao().observeTables().first().single().id
        tableRepository.createRow(tableId)
        assertTrue(repository.rowCreationTimes.first().isNotEmpty())
    }

    private suspend fun note(
        title: String = "Nota",
        labels: List<String> = emptyList(),
        folderId: Long? = null,
        favorite: Boolean = false,
    ): Long = LocalNoteRepository(database) { now }.save(
        null,
        NoteInput(
            title = title,
            content = "Contenido",
            type = NoteType.TEXT,
            color = NoteColor.DEFAULT,
            pinned = false,
            favorite = favorite,
            labels = labels,
            folderId = folderId,
        ),
    )

    private suspend fun task(
        labels: List<String> = emptyList(),
        folderId: Long? = null,
        favorite: Boolean = false,
    ): Long = LocalTaskRepository(database, FakeReminderScheduler()) { now }.save(
        null,
        TaskInput(
            title = "Tarea",
            labels = labels,
            folderId = folderId,
            favorite = favorite,
        ),
    )

    private suspend fun table(
        tags: List<String> = emptyList(),
        folderId: Long? = null,
        favorite: Boolean = false,
    ): Long = LocalTableRepository(database) { now }.createTable(
        TableInput(
            name = "Tabla",
            tags = tags,
            folderId = folderId,
            favorite = favorite,
        ),
    )

    private suspend fun event(
        tags: List<String> = emptyList(),
        folderId: Long? = null,
        favorite: Boolean = false,
    ): Long = LocalEventRepository(database, RecordingEventScheduler()) { now }.save(
        EventInput(
            title = "Evento",
            startAt = now + 3_600_000,
            endAt = now + 7_200_000,
            tags = tags,
            folderId = folderId,
            favorite = favorite,
        ),
    )
}
