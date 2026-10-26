package com.example.nexonote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.domain.model.SubtaskDraft
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.testing.FakeReminderScheduler
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksRepositoryTest {
    private lateinit var database: NexoDatabase
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var repository: LocalTaskRepository
    private var now = 1_000_000L

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NexoDatabase::class.java,
        ).build()
        scheduler = FakeReminderScheduler()
        repository = LocalTaskRepository(database, scheduler) { now }
    }

    @After fun teardown() {
        database.close()
    }

    @Test fun createKeepsEntryAndRelationsAndSchedulesReminder() = runBlocking {
        val due = now + 86_400_000L
        val id = repository.save(
            null,
            taskInput(
                title = "Preparar presentación",
                dueAt = due,
                dueHasTime = true,
                reminderEnabled = true,
                priority = TaskPriority.HIGH,
                labels = listOf(" Trabajo ", "trabajo"),
                subtasks = listOf(SubtaskDraft("Diapositivas"), SubtaskDraft("Ensayar", true)),
            ),
        )
        val created = repository.observeTask(id).first()!!
        assertEquals("Preparar presentación", created.title)
        assertEquals(TaskPriority.HIGH, created.priority)
        assertEquals(TaskStatus.PENDING, created.status)
        assertEquals(listOf("trabajo"), created.labels)
        assertEquals(listOf("Diapositivas", "Ensayar"), created.subtasks.map { it.text })
        assertEquals(1, created.completedSubtasks)
        val entry = database.entryDao().observeAll().first().single()
        assertEquals("Preparar presentación", entry.title)
        assertEquals(due, entry.dueAt)
        assertEquals(TaskPriority.HIGH.rank, entry.priority)
        assertFalse(entry.completed)
        assertEquals(listOf("TASK_CREATED"), database.entryDao().observeRecentActivity(5).first().map { it.kind })
        assertEquals(due, scheduler.scheduled[id])
    }

    @Test fun completingMarksSubtasksCancelsReminderAndCreatesNextOccurrence() = runBlocking {
        val due = now + 86_400_000L
        val id = repository.save(
            null,
            taskInput(
                title = "Informe semanal",
                dueAt = due,
                dueHasTime = true,
                reminderEnabled = true,
                recurrence = TaskRecurrence.WEEKLY,
                labels = listOf("trabajo"),
                subtasks = listOf(SubtaskDraft("Datos"), SubtaskDraft("Revisión")),
            ),
        )
        now += 1_000L
        repository.setCompleted(id, true)

        val completed = repository.observeTask(id).first()!!
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertTrue(completed.subtasks.all { it.completed })
        assertTrue(database.entryDao().observeAll().first().first { it.id == id }.completed)
        assertEquals(
            listOf("TASK_CREATED", "TASK_COMPLETED"),
            database.entryDao().observeRecentActivity(5).first().take(2).map { it.kind },
        )
        assertTrue(id in scheduler.cancelled)

        val next = repository.tasks.first().first { it.id != id }
        assertEquals(TaskStatus.PENDING, next.status)
        assertEquals(completed.seriesId, next.seriesId)
        assertEquals(addWeek(due), next.dueAt)
        assertEquals(next.dueAt, next.occurrenceAt)
        assertEquals(TaskRecurrence.WEEKLY, next.recurrence)
        assertEquals(listOf("trabajo"), next.labels)
        assertEquals(listOf("Datos", "Revisión"), next.subtasks.map { it.text })
        assertTrue(next.subtasks.none { it.completed })
        assertEquals(next.reminderAt, scheduler.scheduled[next.id])

        repository.setCompleted(next.id, true)
        assertEquals(3, repository.tasks.first().size)
        assertEquals(2, repository.tasks.first().count { it.status == TaskStatus.COMPLETED })
    }

    @Test fun uncheckingSubtaskReopensCompletedTask() = runBlocking {
        val id = repository.save(
            null,
            taskInput(
                title = "Checklist",
                subtasks = listOf(SubtaskDraft("Uno"), SubtaskDraft("Dos")),
            ),
        )
        repository.setCompleted(id, true)
        val completed = repository.observeTask(id).first()!!
        val subtaskId = completed.subtasks.first().id

        now += 2_000L
        repository.setSubtaskCompleted(id, subtaskId, false)
        val reopened = repository.observeTask(id).first()!!
        assertEquals(TaskStatus.IN_PROGRESS, reopened.status)
        assertFalse(reopened.subtasks.first().completed)
        assertFalse(database.entryDao().observeAll().first().single().completed)
        assertTrue(
            database.entryDao().observeRecentActivity(5).first().any { it.kind == "TASK_REOPENED" }
        )
    }

    @Test fun editingReplacesDetailsAndStoppingSeriesAffectsAllOccurrences() = runBlocking {
        val due = now + 86_400_000L
        val firstId = repository.save(
            null,
            taskInput(
                title = "Serie",
                dueAt = due,
                dueHasTime = true,
                recurrence = TaskRecurrence.DAILY,
                labels = listOf("uno"),
                subtasks = listOf(SubtaskDraft("Paso")),
            ),
        )
        repository.setCompleted(firstId, true)
        val next = repository.tasks.first().first { it.id != firstId }
        now += 500L
        repository.save(
            next.id,
            taskInput(
                title = "Serie editada",
                dueAt = next.dueAt,
                dueHasTime = true,
                recurrence = TaskRecurrence.DAILY,
                labels = listOf("dos"),
                subtasks = listOf(SubtaskDraft("Nuevo paso")),
            ),
        )
        val edited = repository.observeTask(next.id).first()!!
        assertEquals("Serie editada", edited.title)
        assertEquals(listOf("dos"), edited.labels)
        assertEquals(listOf("Nuevo paso"), edited.subtasks.map { it.text })

        repository.stopRecurrence(firstId)
        assertTrue(repository.tasks.first().all { it.recurrence == TaskRecurrence.NONE })
    }

    @Test fun deletingTaskCascadesRelationsAndCancelsReminder() = runBlocking {
        val due = now + 86_400_000L
        val id = repository.save(
            null,
            taskInput(title = "Temporal", dueAt = due, dueHasTime = true, reminderEnabled = true),
        )
        repository.delete(id)
        assertNull(repository.observeTask(id).first())
        assertTrue(database.entryDao().observeAll().first().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(id in scheduler.cancelled)
    }

    private fun taskInput(
        title: String,
        dueAt: Long? = null,
        dueHasTime: Boolean = false,
        reminderEnabled: Boolean = false,
        priority: TaskPriority = TaskPriority.UNSPECIFIED,
        recurrence: TaskRecurrence = TaskRecurrence.NONE,
        labels: List<String> = emptyList(),
        subtasks: List<SubtaskDraft> = emptyList(),
    ) = TaskInput(
        title = title,
        dueAt = dueAt,
        dueHasTime = dueHasTime,
        reminderEnabled = reminderEnabled,
        priority = priority,
        recurrence = recurrence,
        labels = labels,
        subtasks = subtasks,
    )

    private fun addWeek(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        add(Calendar.DAY_OF_YEAR, 7)
    }.timeInMillis
}
