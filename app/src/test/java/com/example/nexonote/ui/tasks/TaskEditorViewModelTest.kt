package com.example.nexonote.ui.tasks

import androidx.lifecycle.ViewModelStore
import com.example.nexonote.domain.model.Subtask
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelTest {
    private val store = ViewModelStore()
    private val repository = FakeTasks()
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }
    @After fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun newTaskRequiresTitleAndSavesAllFields() = runTest {
        val model = TaskEditorViewModel(repository, null) { 5_000L }.also { store.put("editor", it) }
        runCurrent()
        var result: Boolean? = null
        model.save { result = it }
        runCurrent()
        assertEquals(false, result)
        assertEquals(0, repository.saveCalls)
        assertNotNull(model.uiState.value.error)
        model.dismissError()

        model.setTitle("Enviar informe")
        model.setDescription("Revisar cifras")
        model.setPriority(TaskPriority.URGENT)
        model.setDueDate(date(2026, 5, 12))
        model.setDueHasTime(true)
        model.setDueTime(18, 30)
        model.setReminderEnabled(true)
        model.setRecurrence(TaskRecurrence.WEEKLY)
        model.addLabel("Trabajo")
        model.addSubtask()
        val subtaskKey = model.uiState.value.subtasks.single().key
        model.setSubtaskText(subtaskKey, "Adjuntar gráficos")

        model.save { result = it }
        runCurrent()
        assertEquals(true, result)
        assertEquals(1, repository.saveCalls)
        val saved = repository.saved!!
        assertEquals("Enviar informe", saved.title)
        assertEquals("Revisar cifras", saved.description)
        assertEquals(TaskPriority.URGENT, saved.priority)
        assertEquals(true, saved.dueHasTime)
        assertEquals(18, calendar(saved.dueAt!!).get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar(saved.dueAt).get(java.util.Calendar.MINUTE))
        assertTrue(saved.reminderEnabled)
        assertEquals(TaskRecurrence.WEEKLY, saved.recurrence)
        assertEquals(listOf("trabajo"), saved.labels)
        assertEquals("Adjuntar gráficos", saved.subtasks.single().text)
        assertFalse(model.uiState.value.dirty)
        assertEquals(7L, model.uiState.value.taskId)
    }

    @Test fun completingMarksSubtasksAndUncheckingOneReopensTask() = runTest {
        repository.data.value = Task(
            id = 3,
            title = "Preparar viaje",
            status = TaskStatus.PENDING,
            subtasks = listOf(Subtask(1, "Maleta", false), Subtask(2, "Pasaporte", false)),
        )
        val model = TaskEditorViewModel(repository, 3).also { store.put("editor", it) }
        runCurrent()
        assertEquals("Preparar viaje", model.uiState.value.title)

        model.setStatus(TaskStatus.COMPLETED)
        assertTrue(model.uiState.value.subtasks.all { it.completed })
        model.setSubtaskCompleted(model.uiState.value.subtasks.first().key, false)
        assertEquals(TaskStatus.IN_PROGRESS, model.uiState.value.status)
        assertTrue(model.uiState.value.dirty)
    }

    @Test fun clearingDueDateDisablesReminderAndRecurrence() = runTest {
        repository.data.value = Task(
            id = 4,
            title = "Repetir",
            dueAt = date(2026, 5, 12),
            reminderEnabled = true,
            recurrence = TaskRecurrence.DAILY,
            recurrenceEndAt = date(2026, 5, 20),
        )
        val model = TaskEditorViewModel(repository, 4).also { store.put("editor", it) }
        runCurrent()
        model.setDueDate(null)
        val state = model.uiState.value
        assertNull(state.dueAt)
        assertFalse(state.reminderEnabled)
        assertEquals(TaskRecurrence.NONE, state.recurrence)
        assertNull(state.recurrenceEndAt)
    }

    @Test fun stoppingSeriesUpdatesStateAndRepository() = runTest {
        repository.data.value = Task(
            id = 5,
            title = "Serie",
            dueAt = date(2026, 5, 12),
            recurrence = TaskRecurrence.MONTHLY,
        )
        val model = TaskEditorViewModel(repository, 5).also { store.put("editor", it) }
        runCurrent()
        model.stopRecurrence()
        runCurrent()
        assertEquals(listOf(5L), repository.stoppedRecurrence)
        assertEquals(TaskRecurrence.NONE, model.uiState.value.recurrence)
        assertNull(model.uiState.value.recurrenceEndAt)
    }

    private fun date(year: Int, month: Int, day: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private fun calendar(timestamp: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    private class FakeTasks : TaskRepository {
        val data = MutableStateFlow<Task?>(null)
        var saved: TaskInput? = null
        var saveCalls = 0
        val stoppedRecurrence = mutableListOf<Long>()
        override val tasks: Flow<List<Task>> = data.map { listOfNotNull(it) }
        override val labels = MutableStateFlow(listOf("trabajo"))
        override fun observeTask(id: Long): Flow<Task?> = data
        override suspend fun findTask(id: Long): Task? = data.value
        override suspend fun save(id: Long?, input: TaskInput): Long {
            saveCalls++
            saved = input
            val taskId = id ?: 7L
            data.value = Task(
                id = taskId,
                title = input.title,
                description = input.description,
                status = input.status,
                priority = input.priority,
                startAt = input.startAt,
                dueAt = input.dueAt,
                dueHasTime = input.dueHasTime,
                reminderEnabled = input.reminderEnabled,
                recurrence = input.recurrence,
                recurrenceEndAt = input.recurrenceEndAt,
                labels = input.labels,
                subtasks = input.subtasks.mapIndexed { index, draft ->
                    Subtask(index + 1L, draft.text, draft.completed)
                },
            )
            return taskId
        }
        override suspend fun setCompleted(id: Long, completed: Boolean) = Unit
        override suspend fun setSubtaskCompleted(taskId: Long, subtaskId: Long, completed: Boolean) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun stopRecurrence(id: Long) {
            stoppedRecurrence += id
        }
        override suspend fun rescheduleReminders() = Unit
    }
}
