package com.example.nexonote.domain.model

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class OrganizationTest {
    private val now = day(2026, Calendar.OCTOBER, 28) + 12 * HOUR

    private fun day(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis

    private val folders = listOf(
        Folder(id = 1, name = "Trabajo"),
        Folder(id = 2, name = "Proyectos", parentId = 1),
        Folder(id = 3, name = "NexoNote", parentId = 2),
        Folder(id = 4, name = "Personal"),
    )

    private fun item(
        type: ContentType,
        id: Long = 1,
        title: String = "Elemento",
        favorite: Boolean = false,
        categoryId: Long? = null,
        folderId: Long? = null,
        tags: List<Tag> = emptyList(),
        date: Long? = now,
        status: TaskStatus? = null,
    ) = OrganizationItem(
        type = type,
        id = id,
        title = title,
        favorite = favorite,
        categoryId = categoryId,
        folderId = folderId,
        tags = tags,
        date = date,
        taskStatus = status,
    )

    @Test fun folderCyclePreventionRejectsSelfAndDescendants() {
        val parents = folders.associate { it.id to it.parentId }
        assertTrue(folderCreatesCycle(1, 1, parents))
        assertTrue(folderCreatesCycle(1, 3, parents))
        assertTrue(folderCreatesCycle(2, 3, parents))
        assertFalse(folderCreatesCycle(3, 1, parents))
        assertFalse(folderCreatesCycle(2, 4, parents))
        assertFalse(folderCreatesCycle(null, 1, parents))
        assertFalse(folderCreatesCycle(1, null, parents))
    }

    @Test fun descendantsAndPathsFollowTheHierarchyWithoutLoops() {
        assertEquals(setOf(2L, 3L), descendantsOf(1, folders))
        assertEquals(setOf(3L), descendantsOf(2, folders))
        assertEquals(emptySet<Long>(), descendantsOf(4, folders))
        assertEquals("Trabajo / Proyectos / NexoNote", folderPath(folders[2], folders))
        assertEquals("Sin carpeta", folderPath(null, folders))
        val cyclic = folders + Folder(id = 5, name = "Ciclo", parentId = 5)
        assertEquals(emptySet<Long>(), descendantsOf(5, cyclic))
        assertTrue(folderPath(cyclic.last(), cyclic).contains("Ciclo"))
    }

    @Test fun searchFiltersRequireEverySelectedTagAndRespectTypeAndCategory() {
        val work = Tag(1, "trabajo")
        val urgent = Tag(2, "urgente")
        val note = item(
            ContentType.NOTE,
            categoryId = 8,
            tags = listOf(work, urgent),
        )
        val filters = SearchFilters(categoryId = 8, tagIds = setOf(1L, 2L), types = setOf(ContentType.NOTE))
        assertTrue(filters.matches(note, now))
        assertFalse(SearchFilters(tagIds = setOf(1L, 2L, 3L)).matches(note, now))
        assertFalse(SearchFilters(categoryId = 9).matches(note, now))
        assertFalse(SearchFilters(types = setOf(ContentType.TASK)).matches(note, now))
        assertTrue(SearchFilters(query = "trabajo").matches(note, now))
    }

    @Test fun stateAndDateFiltersDoNotMixTaskAndNoteSemantics() {
        val pending = item(ContentType.TASK, status = TaskStatus.PENDING, date = now - 2 * HOUR)
        val completed = item(ContentType.TASK, status = TaskStatus.COMPLETED, date = now)
        val note = item(ContentType.NOTE, date = now)
        assertTrue(SearchFilters(state = SearchStateFilter.PENDING).matches(pending, now))
        assertFalse(SearchFilters(state = SearchStateFilter.PENDING).matches(completed, now))
        assertFalse(SearchFilters(state = SearchStateFilter.PENDING).matches(note, now))
        assertTrue(SearchFilters(date = SearchDateFilter.OVERDUE).matches(pending, now))
        assertFalse(SearchFilters(date = SearchDateFilter.OVERDUE).matches(completed, now))
        assertFalse(SearchFilters(date = SearchDateFilter.OVERDUE).matches(note, now))
        assertTrue(SearchFilters(date = SearchDateFilter.TODAY).matches(note, now))
    }

    @Test fun favoritesFilterMatchesOnlyFavoriteItems() {
        assertTrue(
            SearchFilters(favoriteOnly = true).matches(
                item(ContentType.TABLE, favorite = true),
                now,
            )
        )
        assertFalse(
            SearchFilters(favoriteOnly = true).matches(
                item(ContentType.TABLE, favorite = false),
                now,
            )
        )
    }

    @Test fun statisticsAreComputedFromRealDistinctDataAndNeverDivideByZero() {
        val note = Note(1, "Nota", "", NoteType.TEXT, createdAt = day(2026, Calendar.OCTOBER, 27))
        val taskPending = Task(1, "Pendiente", dueAt = now - HOUR, dueHasTime = true)
        val taskDone = Task(2, "Hecha", status = TaskStatus.COMPLETED)
        val tableA = CustomTable(1, "A", rowCount = 3)
        val tableDuplicate = CustomTable(1, "A", rowCount = 3)
        val tableB = CustomTable(2, "B", rowCount = 2)
        val event = CalendarEvent(
            id = 1,
            input = EventInput("Evento", now + HOUR, now + 2 * HOUR),
        )
        val stats = computeOrganizationStats(
            notes = listOf(note, note.copy(id = 2, trashed = true)),
            tasks = listOf(taskPending, taskDone, taskPending),
            tables = listOf(tableA, tableDuplicate, tableB),
            events = listOf(event),
            activityTimes = listOf(now),
            rowCreationTimes = listOf(now, day(2026, Calendar.JANUARY, 1)),
            now = now,
        )
        assertEquals(1, stats.totalNotes)
        assertEquals(1, stats.pendingTasks)
        assertEquals(1, stats.completedTasks)
        assertEquals(1, stats.overdueTasks)
        assertEquals(2, stats.tables)
        assertEquals(5, stats.records)
        assertEquals(1, stats.recordsLast30Days)
        assertEquals(1, stats.scheduledEvents)
        assertEquals(1, stats.upcomingEvents)
        assertEquals(0.5f, stats.completionRate, 0.001f)
        assertEquals(50, stats.completionPercent)
        assertEquals(7, stats.noteDaily.size)
        assertEquals(8, stats.activityWeekly.size)
        assertEquals(6, stats.activityMonthly.size)
        assertEquals(1, stats.activityDaily.sumOf { it.value })
    }

    @Test fun emptyStatisticsUseZeroCompletionInsteadOfDividingByZero() {
        val stats = computeOrganizationStats(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), now,
        )
        assertEquals(0f, stats.completionRate, 0.001f)
        assertEquals(0, stats.completionPercent)
        assertTrue(stats.isEmpty)
    }

    @Test fun suggestedViewsAndValidatorsExposeSafeDefaults() {
        assertEquals(
            listOf("Pendientes", "Importantes", "Trabajo", "Personal"),
            suggestedViews().map { it.first },
        )
        assertEquals(
            setOf(ContentType.TASK),
            suggestedViews().first().second.types,
        )
        assertNull(validateFolderName("Trabajo"))
        assertNotNull(validateFolderName("   "))
        assertNotNull(validateCategoryName(""))
        assertNotNull(validateTagName("x".repeat(31)))
        assertNotNull(validateSavedViewName(""))
        assertNotNull(validateColor("#12GG00"))
        assertNull(validateColor("#006A6A"))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
