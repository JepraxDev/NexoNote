package com.example.nexonote.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nexonote.data.local.*
import com.example.nexonote.data.repository.*
import com.example.nexonote.domain.model.*
import com.example.nexonote.domain.repository.EventReminderScheduler
import com.example.nexonote.testing.FakeReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

class RecordingEventScheduler : EventReminderScheduler {
    data class Request(val revision: Long, val start: Long, val at: Long)
    val scheduled = mutableMapOf<Long, Request>()
    val cancelled = mutableSetOf<Long>()
    override fun schedule(id: Long, revision: Long, start: Long, reminderAt: Long) {
        scheduled[id] = Request(revision, start, reminderAt)
    }
    override fun cancel(id: Long) { scheduled.remove(id); cancelled.add(id) }
}

@RunWith(AndroidJUnit4::class)
class EventRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NexoDatabase
    private lateinit var repository: LocalEventRepository
    private lateinit var scheduler: RecordingEventScheduler
    private var now = 1_800_000_000_000L
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NexoDatabase::class.java)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).build()
        scheduler = RecordingEventScheduler()
        repository = LocalEventRepository(db, scheduler) { now }
    }
    @After fun close() { db.close() }
    private fun input() = EventInput("Reunión", now + 3600000, now + 7200000, reminderMinutes = 15)

    @Test fun createEditDeleteAndCancelReminder() = runBlocking {
        val input = input()
        val id = repository.save(input)
        assertEquals(input.startAt - 900000, scheduler.scheduled.getValue(id).at)
        repository.save(input.copy(title = "Cambio", reminderMinutes = 5), id)
        assertEquals("Cambio", repository.observeAll().first().single().input.title)
        assertEquals(input.startAt - 300000, scheduler.scheduled.getValue(id).at)
        assertEquals(2L, scheduler.scheduled.getValue(id).revision)
        repository.delete(id)
        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(id in scheduler.cancelled)
        assertFalse(id in scheduler.scheduled)
    }

    @Test fun invalidEventDoesNotPersistOrSchedule() = runBlocking {
        assertTrue(runCatching { repository.save(input().copy(title = "")) }.isFailure)
        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun occurrenceEditDeleteAndSeriesResetAreTransactional() = runBlocking {
        val input = input().copy(recurrence = TaskRecurrence.DAILY)
        val id = repository.save(input)
        val second = occurrenceStart(input, 1)
        val exceptionId = repository.saveOccurrence(id, second, input.copy(title = "Excepción", startAt = second, endAt = second + 1000))
        repository.saveOccurrence(id, second, input.copy(title = "Actualizada", startAt = second, endAt = second + 1000))
        assertEquals(2, repository.observeAll().first().size)
        repository.deleteOccurrence(id, input.startAt)
        val occurrences = eventOccurrences(repository.observeAll().first(), input.startAt, second + 1)
        assertEquals("Actualizada", occurrences.single().event.input.title)
        repository.save(input.copy(title = "Serie nueva"), id)
        assertEquals(1, repository.observeAll().first().size)
        assertTrue(exceptionId in scheduler.cancelled)
        repository.delete(id)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test fun deliveredReminderIsDeduplicatedAndNextOccurrenceScheduled() = runBlocking {
        val input = input().copy(recurrence = TaskRecurrence.DAILY)
        val id = repository.save(input)
        val request = scheduler.scheduled.getValue(id)
        now = request.at
        var delivered = 0
        repository.deliverReminder(id, request.revision, request.start) { delivered++ }
        repository.deliverReminder(id, request.revision, request.start) { delivered++ }
        assertEquals(1, delivered)
        assertEquals(occurrenceStart(input, 1), scheduler.scheduled.getValue(id).start)
        repository.save(input.copy(title = "Modificado"), id)
        repository.deliverReminder(id, request.revision, request.start) { delivered++ }
        assertEquals(1, delivered)
    }

    @Test fun tasksRemainInTheirRepositoryAndProjectIntoCalendar() = runBlocking {
        val tasks = LocalTaskRepository(db, FakeReminderScheduler())
        val id = tasks.save(null, TaskInput("Tarea existente", dueAt = now + 1000))
        val list = calendarActivities(repository.observeAll().first(), tasks.tasks.first(), now, now + 2000)
        assertEquals(id, list.single().task!!.id)
        tasks.setCompleted(id, true)
        assertTrue(calendarActivities(emptyList(), tasks.tasks.first(), now, now + 2000).single().task!!.isCompleted)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test fun simultaneousRemindersAndDelayedRestartDoNotCancelUndeliveredEvents() = runBlocking {
        val input = input()
        val first = repository.save(input)
        val second = repository.save(input.copy(title = "Segundo"))
        val request = scheduler.scheduled.getValue(first)
        now = request.at + 60_000
        repository.deliverReminder(first, request.revision, request.start) { }
        assertEquals(input.startAt, scheduler.scheduled.getValue(second).start)
        val restarted = RecordingEventScheduler()
        val restored = LocalEventRepository(db, restarted) { now }
        restored.rescheduleReminders()
        assertTrue(second in restarted.scheduled)
        var delivered = 0
        restored.deliverReminder(second, 1, input.startAt) { delivered++ }
        assertEquals(1, delivered)
    }

    @Test fun futureEventAndRescheduleUsePersistentState() = runBlocking {
        val distant = input().copy(startAt = now + 800 * 86400000L, endAt = now + 800 * 86400000L + 1000)
        val id = repository.save(distant)
        assertEquals(distant.startAt, scheduler.scheduled.getValue(id).start)
        val replacement = RecordingEventScheduler()
        LocalEventRepository(db, replacement) { now }.rescheduleReminders()
        assertEquals(scheduler.scheduled[id], replacement.scheduled[id])
    }

    @Test fun migrationPreservesPreviousStagesAndCreatesEvents() {
        val name = "calendar-migration"
        migration.createDatabase(name, 6).apply {
            execSQL("INSERT INTO entries (id, title, type, completed, priority) VALUES (1, 'Histórico', 'TABLE', 0, 0)")
            execSQL("INSERT INTO custom_tables (id,name,description,icon,color) VALUES (1,'Histórico','','GRID','#6750A4')")
            close()
        }
        migration.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { migrated ->
            migrated.query("SELECT name FROM custom_tables WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("Histórico", it.getString(0)) }
            migrated.query("SELECT COUNT(*) FROM events").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun eventsSurviveDatabaseReopen() = runBlocking {
        val name = "calendar-persistence"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, NexoDatabase::class.java, name).build()
        try {
            val id = LocalEventRepository(disk, scheduler) { now }.save(input())
            disk.close()
            disk = Room.databaseBuilder(context, NexoDatabase::class.java, name).build()
            assertEquals(id, LocalEventRepository(disk, scheduler) { now }.observeAll().first().single().id)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
