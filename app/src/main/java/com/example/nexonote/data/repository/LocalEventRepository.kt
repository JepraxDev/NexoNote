package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.*
import com.example.nexonote.domain.model.*
import com.example.nexonote.domain.repository.EventRepository
import com.example.nexonote.domain.repository.EventReminderScheduler
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalEventRepository(
    private val database: NexoDatabase,
    private val scheduler: EventReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : EventRepository {
    private val dao = database.eventDao()
    private val organizationDao = database.organizationDao()
    private val mutex = Mutex()
    override fun observeAll() = combine(
        dao.observeAll(),
        dao.observeAllLabels(),
        organizationDao.observeTags(),
    ) { events, links, tagRows ->
        val labels = tagRows.associate { it.label.id to it.label.name }
        val byEvent = links.groupBy { it.eventId }
        events.map { entity ->
            entity.domain(byEvent[entity.id].orEmpty().mapNotNull { labels[it.labelId] }.sorted())
        }
    }

    override suspend fun save(input: EventInput, id: Long?): Long = mutex.withLock {
        validateEvent(input)?.let { throw IllegalArgumentException(it) }
        if (input.folderId != null) {
            require(database.organizationDao().findFolder(input.folderId) != null) {
                "La carpeta seleccionada ya no existe."
            }
        }
        if (input.categoryId != null) {
            require(database.organizationDao().findCategory(input.categoryId) != null) {
                "La categoría seleccionada ya no existe."
            }
        }
        val removed = mutableListOf<Long>()
        val saved = database.withTransaction {
            val eventId = if (id == null) dao.insert(input.entity()) else {
                val old = requireNotNull(dao.find(id)) { "El evento ya no existe." }
                if (old.parentId == null) {
                    removed.addAll(dao.all().filter { it.parentId == id }.map { it.id })
                    dao.deleteExceptions(id)
                }
                dao.update(input.entity(id, old.revision + 1).copy(parentId = old.parentId,
                    originalStart = old.originalStart,
                    recurrence = if (old.parentId != null) TaskRecurrence.NONE.name else input.recurrence.name))
                id
            }
            replaceTags(eventId, input.tags)
            eventId
        }
        removed.forEach(scheduler::cancel)
        rescheduleLocked()
        saved
    }

    override suspend fun saveOccurrence(seriesId: Long, originalStart: Long, input: EventInput): Long = mutex.withLock {
        validateEvent(input)?.let { throw IllegalArgumentException(it) }
        val id = exceptionLocked(seriesId, originalStart, input, false)
        rescheduleLocked()
        id
    }

    private suspend fun exceptionLocked(seriesId: Long, start: Long, input: EventInput, cancelled: Boolean): Long =
        database.withTransaction {
            val series = requireNotNull(dao.find(seriesId)) { "La serie ya no existe." }
            require(series.parentId == null) { "Selecciona la serie original." }
            val old = dao.exception(seriesId, start)
            val replacement = input.copy(recurrence = TaskRecurrence.NONE, repeatUntil = null)
                .entity(old?.id ?: 0, (old?.revision ?: 0) + 1)
                .copy(parentId = seriesId, originalStart = start, cancelled = cancelled)
            val id = if (old == null) dao.insert(replacement) else { dao.update(replacement); old.id }
            replaceTags(id, input.tags)
            id
        }

    override suspend fun deleteOccurrence(seriesId: Long, originalStart: Long) = mutex.withLock {
        val source = requireNotNull(dao.find(seriesId)) { "La serie ya no existe." }
        exceptionLocked(seriesId, originalStart, source.domain().input, true)
        rescheduleLocked()
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        val ids = database.withTransaction {
            val all = dao.all()
            dao.delete(id)
            all.filter { it.id == id || it.parentId == id }.map { it.id }
        }
        ids.forEach(scheduler::cancel)
        rescheduleLocked()
    }

    override suspend fun rescheduleReminders() = mutex.withLock { rescheduleLocked() }

    private suspend fun replaceTags(eventId: Long, tags: List<String>) {
        val organization = database.organizationDao()
        dao.deleteEventLabels(eventId)
        tags.map { it.trim().lowercase(Locale.ROOT).take(30) }
            .filter(String::isNotEmpty)
            .distinct()
            .take(20)
            .forEach { name ->
                val label = organization.findTagByName(name) ?: run {
                    val id = organization.insertTag(LabelEntity(name = name))
                    if (id > 0) LabelEntity(id = id, name = name) else organization.findTagByName(name)
                }
                label?.let { dao.insertEventLabel(EventLabelCrossRef(eventId, it.id)) }
            }
    }

    private suspend fun rescheduleLocked() {
        val events = dao.all().map { it.domain() }
        val now = clock()
        events.forEach { event ->
            val minutes = event.input.reminderMinutes
            val from = maxOf(now - 86_400_000L, event.input.startAt)
            val related = events.filter { it.id == event.id || it.parentId == event.id }
            val periodDays = when (event.input.recurrence) {
                TaskRecurrence.MONTHLY -> 31L; TaskRecurrence.WEEKLY -> 7L; else -> 1L
            }
            val horizon = from + (related.size + 3L) * periodDays * 86_400_000L
            // Keep an already planned occurrence even if Android delayed its worker or the device rebooted.
            val pending = event.plannedStart?.takeIf { event.notifiedStart == null || it > event.notifiedStart }?.let { start ->
                eventOccurrences(related, start, start + 1).firstOrNull { it.event.id == event.id && it.startAt == start }
            }
            val next = if (minutes == null || event.cancelled) null else pending ?: eventOccurrences(related, from, horizon).firstOrNull {
                it.event.id == event.id && (event.notifiedStart == null || it.startAt > event.notifiedStart) && it.startAt - minutes * 60_000L >= now
            }
            dao.plan(event.id, event.revision, next?.startAt)
            if (next == null) scheduler.cancel(event.id) else scheduler.schedule(event.id, event.revision,
                next.startAt, next.startAt - requireNotNull(minutes) * 60_000L)
        }
    }

    override suspend fun deliverReminder(id: Long, revision: Long, start: Long, notify: (EventOccurrence) -> Unit) = mutex.withLock {
        val events = dao.all().map { it.domain() }
        val event = events.firstOrNull { it.id == id } ?: return@withLock
        val minutes = event.input.reminderMinutes ?: return@withLock
        if (event.revision != revision) return@withLock
        if (event.notifiedStart != null && event.notifiedStart >= start) {
            rescheduleLocked()
            return@withLock
        }
        val occurrence = eventOccurrences(events, start, start + 1).firstOrNull { it.event.id == id && it.startAt == start }
        if (occurrence != null && clock() >= start - minutes * 60_000L) {
            database.withTransaction { dao.markNotified(id, revision, start) }
            notify(occurrence)
        }
        rescheduleLocked()
    }
}
