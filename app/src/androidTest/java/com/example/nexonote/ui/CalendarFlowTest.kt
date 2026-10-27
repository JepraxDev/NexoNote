package com.example.nexonote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.RecordingEventScheduler
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalEventRepository
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.domain.model.*
import com.example.nexonote.testing.FakeReminderScheduler
import com.example.nexonote.ui.calendar.CalendarRoute
import com.example.nexonote.ui.calendar.CalendarViewModel
import com.example.nexonote.ui.theme.NexoNoteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: NexoDatabase
    private lateinit var events: LocalEventRepository
    private val models = ViewModelStore()
    private var openedTask = 0L
    private var taskId = 0L

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, NexoDatabase::class.java).build()
        events = LocalEventRepository(db, RecordingEventScheduler())
        val tasks = LocalTaskRepository(db, FakeReminderScheduler())
        taskId = runBlocking { tasks.save(null, TaskInput("Tarea del calendario", dueAt = calendarDay(System.currentTimeMillis()) + 36000000)) }
        val model = CalendarViewModel(events, tasks, androidx.lifecycle.SavedStateHandle()).also { models.put("calendar", it) }
        compose.setContent { NexoNoteTheme { CalendarRoute(model, {}, { openedTask = it }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new_event").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun teardown() { compose.runOnIdle { models.clear() }; db.close() }

    @Test fun createEditDeleteSwitchViewsAndOpenExistingTask() {
        compose.onNodeWithTag("new_event").performClick()
        compose.onNodeWithTag("event_title").performTextInput("Reunión local")
        compose.onNodeWithTag("event_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("event_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Día", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Reunión local").performScrollTo().performClick()
        compose.onNodeWithTag("event_title").performTextReplacement("Reunión editada")
        compose.onNodeWithTag("event_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("event_title").fetchSemanticsNodes().isEmpty() }
        assertEquals("Reunión editada", runBlocking { events.observeAll().first().single().input.title })
        compose.onNodeWithText("Semana", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("calendar_screen").assertExists()
        compose.onNodeWithText("Mes", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Día", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tarea del calendario").performScrollTo().performClick()
        assertEquals(taskId, openedTask)
        compose.onNodeWithText("Reunión editada").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar evento").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("event_title").fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { events.observeAll().first().isEmpty() })
    }
}
