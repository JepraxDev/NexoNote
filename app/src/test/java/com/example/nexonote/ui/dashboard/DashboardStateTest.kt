package com.example.nexonote.ui.dashboard

import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.ActivityKind
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskStatus
import org.junit.Assert.*
import org.junit.Test

class DashboardStateTest {
    @Test fun emptyDashboardHasZeroProgressWithoutNaN() {
        val state = dashboardState(emptyList(), emptyList(), emptyList(), emptyList(), "", 100)
        assertFalse(state.loading)
        assertEquals(0, state.taskCount)
        assertEquals(0f, state.progress)
        assertEquals(0, state.percentage)
        assertTrue(state.recentNotes.isEmpty())
        assertTrue(state.upcomingTasks.isEmpty())
        assertTrue(state.recentActivity.isEmpty())
    }

    @Test fun statisticsCountAllItemsEvenWhenListsAreLimited() {
        val entries = listOf(Entry(13, "Tabla", EntryType.TABLE))
        val tasks = listOf(
            task(10, "Pendiente"),
            task(11, "Hecha", status = TaskStatus.COMPLETED),
            task(12, "Otra hecha", status = TaskStatus.COMPLETED),
        )
        val notes = (1L..8).map { note(it, "Nota $it") }
        val state = dashboardState(entries, notes, tasks, emptyList(), "Ana", 100)
        assertEquals(8, state.noteCount)
        assertEquals(1, state.pendingCount)
        assertEquals(2, state.completedCount)
        assertEquals(1, state.tableCount)
        assertEquals(67, state.percentage)
        assertEquals(5, state.recentNotes.size)
        assertEquals(listOf(10L), state.upcomingTasks.map { it.id })
    }

    @Test fun recentNotesUseModificationDateAndPutUnknownDatesLast() {
        val state = dashboardState(emptyList(), listOf(
            note(99, "Antigua sin fecha"),
            note(1, "Recién modificada", modifiedAt = 200),
            note(2, "Anterior", modifiedAt = 100),
        ), emptyList(), emptyList(), "", 300)
        assertEquals(listOf(1L, 2L, 99L), state.recentNotes.map { it.id })
        assertNull(state.recentNotes.last().modifiedAt)
    }

    @Test fun archivedAndTrashedNotesAreExcludedFromActiveDashboard() {
        val state = dashboardState(emptyList(), listOf(
            note(1, "Activa"),
            note(2, "Archivada", archived = true),
            note(3, "Papelera", trashed = true),
        ), emptyList(), emptyList(), "", 300)
        assertEquals(1, state.noteCount)
        assertEquals(listOf(1L), state.recentNotes.map { it.id })
    }

    @Test fun pendingTasksSortByDeadlineThenPriorityAndExcludeCompleted() {
        val state = dashboardState(
            emptyList(),
            emptyList(),
            listOf(
                task(1, "Sin fecha alta", priority = TaskPriority.HIGH),
                task(2, "Baja", dueAt = 200, priority = TaskPriority.LOW),
                task(3, "Alta", dueAt = 200, priority = TaskPriority.HIGH),
                task(4, "Vencida", dueAt = 100),
                task(5, "Hecha", status = TaskStatus.COMPLETED, dueAt = 50),
                task(6, "Sin fecha baja", priority = TaskPriority.LOW),
            ),
            emptyList(),
            "",
            150,
        )
        assertEquals(listOf(4L, 3L, 2L, 1L, 6L), state.upcomingTasks.map { it.id })
    }

    @Test fun allTasksCompletedMeansFullProgress() {
        val state = dashboardState(
            emptyList(),
            emptyList(),
            listOf(task(1, "Hecha", status = TaskStatus.COMPLETED)),
            emptyList(),
            "",
            1,
        )
        assertEquals(100, state.percentage)
        assertTrue(state.upcomingTasks.isEmpty())
    }

    @Test fun activityIsLimitedToFiveAndSortedNewestFirst() {
        val activities = (1L..7).map {
            Activity(it, it, ActivityKind.NOTE_CREATED, occurredAt = it, title = "Nota $it")
        }.shuffled()
        val state = dashboardState(emptyList(), emptyList(), emptyList(), activities, "", 10)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), state.recentActivity.map { it.id })
        assertEquals("Nota 7", state.recentActivity.first().title)
    }

    @Test fun greetingChangesAtDayBoundaries() {
        assertEquals("Buenas noches", greeting(0))
        assertEquals("Buenas noches", greeting(5))
        assertEquals("Buenos días", greeting(6))
        assertEquals("Buenos días", greeting(11))
        assertEquals("Buenas tardes", greeting(12))
        assertEquals("Buenas tardes", greeting(19))
        assertEquals("Buenas noches", greeting(20))
    }

    private fun task(
        id: Long,
        title: String,
        status: TaskStatus = TaskStatus.PENDING,
        dueAt: Long? = null,
        priority: TaskPriority = TaskPriority.UNSPECIFIED,
    ) = Task(id = id, title = title, status = status, dueAt = dueAt, priority = priority)

    private fun note(
        id: Long,
        title: String,
        modifiedAt: Long? = null,
        archived: Boolean = false,
        trashed: Boolean = false,
    ) = Note(
        id = id,
        title = title,
        content = "",
        type = NoteType.TEXT,
        modifiedAt = modifiedAt,
        archived = archived,
        trashed = trashed,
    )
}
