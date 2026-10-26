package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.ActivityEntity
import com.example.nexonote.data.local.EntryEntity
import com.example.nexonote.data.local.LabelEntity
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.local.SubtaskEntity
import com.example.nexonote.data.local.TaskEntity
import com.example.nexonote.data.local.TaskLabelCrossRef
import com.example.nexonote.data.local.TaskWithDetails
import com.example.nexonote.domain.model.Subtask
import com.example.nexonote.domain.model.SubtaskDraft
import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskRecurrence
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.nextOccurrence
import com.example.nexonote.domain.model.taskReminderAt
import com.example.nexonote.domain.model.validateTaskInput
import com.example.nexonote.domain.reminder.ReminderScheduler
import com.example.nexonote.domain.repository.TaskRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalTaskRepository(
    private val database: NexoDatabase,
    private val scheduler: ReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : TaskRepository {
    private val taskDao = database.taskDao()
    private val entryDao = database.entryDao()

    override val tasks: Flow<List<Task>> = taskDao.observeAll().map { tasks ->
        tasks.map(TaskWithDetails::toDomain)
    }

    override val labels: Flow<List<String>> = taskDao.observeLabelNames()

    override fun observeTask(id: Long): Flow<Task?> = taskDao.observe(id).map { it?.toDomain() }

    override suspend fun findTask(id: Long): Task? = taskDao.find(id)?.toDomain()

    override suspend fun save(id: Long?, input: TaskInput): Long {
        val error = validateTaskInput(input)
        require(error == null) { error.orEmpty() }
        val now = clock()
        val title = input.title.trim()
        val description = input.description.trim()
        val labels = input.labels.map(::normalizeLabel).filter(String::isNotEmpty).distinct()
        val subtasks = normalizeSubtasks(input.subtasks, input.status == TaskStatus.COMPLETED)
        val reminderAt = if (input.reminderEnabled) {
            taskReminderAt(input.dueAt, input.dueHasTime)
        } else {
            null
        }
        var taskId = id ?: 0L
        var nextTask: Task? = null

        database.withTransaction {
            val existing = if (id == null) null else {
                taskDao.find(id) ?: error("La tarea ya no existe.")
            }
            val oldStatus = existing?.let { TaskStatus.entries.firstOrNull { status -> status.name == it.task.status } }
            val reopening = oldStatus == TaskStatus.COMPLETED && input.status != TaskStatus.COMPLETED
            val completing = input.status == TaskStatus.COMPLETED && oldStatus != TaskStatus.COMPLETED
            val seriesId = existing?.task?.seriesId
            val occurrenceAt = existing?.task?.occurrenceAt
            val createdAt = existing?.task?.createdAt

            if (id == null) {
                taskId = entryDao.insert(
                    EntryEntity(
                        title = title,
                        type = "TASK",
                        completed = input.status == TaskStatus.COMPLETED,
                        modifiedAt = now,
                        dueAt = input.dueAt,
                        priority = input.priority.rank,
                    )
                )
                taskDao.insertTask(
                    TaskEntity(
                        id = taskId,
                        title = title,
                        description = description,
                        status = input.status.name,
                        priority = input.priority.rank,
                        startAt = input.startAt,
                        dueAt = input.dueAt,
                        dueHasTime = input.dueHasTime,
                        reminderEnabled = input.reminderEnabled,
                        reminderAt = reminderAt,
                        recurrence = input.recurrence.name,
                        recurrenceEndAt = input.recurrenceEndAt,
                        seriesId = null,
                        occurrenceAt = null,
                        createdAt = now,
                        modifiedAt = now,
                    )
                )
                entryDao.insertActivity(
                    ActivityEntity(entryId = taskId, kind = "TASK_CREATED", occurredAt = now)
                )
                if (input.status == TaskStatus.COMPLETED) {
                    entryDao.insertActivity(
                        ActivityEntity(entryId = taskId, kind = "TASK_COMPLETED", occurredAt = now)
                    )
                }
            } else {
                val entity = existing ?: error("La tarea ya no existe.")
                taskDao.updateTask(
                    entity.task.copy(
                        title = title,
                        description = description,
                        status = input.status.name,
                        priority = input.priority.rank,
                        startAt = input.startAt,
                        dueAt = input.dueAt,
                        dueHasTime = input.dueHasTime,
                        reminderEnabled = input.reminderEnabled,
                        reminderAt = reminderAt,
                        recurrence = input.recurrence.name,
                        recurrenceEndAt = input.recurrenceEndAt,
                        seriesId = seriesId,
                        occurrenceAt = occurrenceAt,
                        createdAt = createdAt,
                        modifiedAt = now,
                    )
                )
                entryDao.updateTaskSummary(
                    id = taskId,
                    title = title,
                    modifiedAt = now,
                    dueAt = input.dueAt,
                    priority = input.priority.rank,
                )
                entryDao.updateEntryCompleted(taskId, input.status == TaskStatus.COMPLETED)
                if (reopening) {
                    entryDao.insertActivity(
                        ActivityEntity(entryId = taskId, kind = "TASK_REOPENED", occurredAt = now)
                    )
                }
                if (completing) {
                    entryDao.insertActivity(
                        ActivityEntity(entryId = taskId, kind = "TASK_COMPLETED", occurredAt = now)
                    )
                }
            }

            replaceSubtasks(taskId, subtasks)
            replaceLabels(taskId, labels)

            if (completing) {
                nextTask = createNextOccurrence(completedTask(taskId), now)
            }
        }

        val saved = taskDao.find(taskId)?.toDomain()
        if (saved != null) syncReminder(saved, now)
        nextTask?.let { syncReminder(it, now) }
        return taskId
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        val now = clock()
        var saved: Task? = null
        var nextTask: Task? = null
        database.withTransaction {
            val existing = taskDao.find(id) ?: return@withTransaction
            val oldStatus = TaskStatus.entries.firstOrNull { it.name == existing.task.status }
            if ((oldStatus == TaskStatus.COMPLETED) == completed) return@withTransaction
            val newStatus = when {
                completed -> TaskStatus.COMPLETED
                existing.subtasks.any { it.completed } -> TaskStatus.IN_PROGRESS
                else -> TaskStatus.PENDING
            }
            taskDao.updateStatus(id, newStatus.name, now)
            entryDao.updateTaskCompleted(id, completed, now)
            entryDao.insertActivity(
                ActivityEntity(
                    entryId = id,
                    kind = if (completed) "TASK_COMPLETED" else "TASK_REOPENED",
                    occurredAt = now,
                )
            )
            if (completed) {
                taskDao.completeSubtasks(id)
                nextTask = createNextOccurrence(completedTask(id), now)
            }
            saved = taskDao.find(id)?.toDomain()
        }
        saved?.let { syncReminder(it, now) }
        nextTask?.let { syncReminder(it, now) }
    }

    override suspend fun setSubtaskCompleted(taskId: Long, subtaskId: Long, completed: Boolean) {
        val now = clock()
        var saved: Task? = null
        database.withTransaction {
            val existing = taskDao.find(taskId) ?: return@withTransaction
            val subtask = taskDao.findSubtask(taskId, subtaskId) ?: return@withTransaction
            if (subtask.completed == completed) return@withTransaction
            if (taskDao.updateSubtaskCompleted(taskId, subtaskId, completed) == 0) return@withTransaction
            val taskWasCompleted = existing.task.status == TaskStatus.COMPLETED.name
            val status = if (taskWasCompleted && !completed) TaskStatus.IN_PROGRESS else null
            val updated = existing.task.copy(
                status = status?.name ?: existing.task.status,
                modifiedAt = now,
            )
            taskDao.updateTask(updated)
            entryDao.updateTaskSummary(
                id = taskId,
                title = updated.title,
                modifiedAt = now,
                dueAt = updated.dueAt,
                priority = updated.priority,
            )
            if (status != null) {
                entryDao.updateEntryCompleted(taskId, false)
                entryDao.insertActivity(
                    ActivityEntity(entryId = taskId, kind = "TASK_REOPENED", occurredAt = now)
                )
            }
            saved = taskDao.find(taskId)?.toDomain()
        }
        saved?.let { syncReminder(it, now) }
    }

    override suspend fun delete(id: Long) {
        val existing = taskDao.find(id)
        if (existing == null) {
            scheduler.cancel(id)
            return
        }
        database.withTransaction { taskDao.deleteTaskEntry(id) }
        scheduler.cancel(id)
    }

    override suspend fun stopRecurrence(id: Long) {
        val now = clock()
        database.withTransaction {
            val task = taskDao.find(id)?.task ?: return@withTransaction
            if (taskDao.stopRecurrence(id, task.seriesId, now) == 0) return@withTransaction
            entryDao.updateTaskSummary(
                id = id,
                title = task.title,
                modifiedAt = now,
                dueAt = task.dueAt,
                priority = task.priority,
            )
        }
    }

    override suspend fun rescheduleReminders() {
        val now = clock()
        taskDao.findPendingReminders().forEach { details ->
            val task = details.toDomain()
            syncReminder(task, now)
        }
    }

    private suspend fun createNextOccurrence(task: Task, now: Long): Task? {
        val occurrence = task.nextOccurrence() ?: return null
        var seriesTask = task
        if (task.seriesId == null || task.occurrenceAt == null) {
            seriesTask = task.copy(seriesId = task.id, occurrenceAt = task.dueAt)
            taskDao.updateTask(seriesTask.toEntity())
        }
        val seriesId = seriesTask.seriesId ?: return null
        if (taskDao.findByOccurrence(seriesId, occurrence.dueAt) != null) return null

        val nextId = entryDao.insert(
            EntryEntity(
                title = seriesTask.title,
                type = "TASK",
                completed = false,
                modifiedAt = now,
                dueAt = occurrence.dueAt,
                priority = seriesTask.priority.rank,
            )
        )
        taskDao.insertTask(
            TaskEntity(
                id = nextId,
                title = seriesTask.title,
                description = seriesTask.description,
                status = TaskStatus.PENDING.name,
                priority = seriesTask.priority.rank,
                startAt = occurrence.startAt,
                dueAt = occurrence.dueAt,
                dueHasTime = seriesTask.dueHasTime,
                reminderEnabled = seriesTask.reminderEnabled,
                reminderAt = occurrence.reminderAt,
                recurrence = seriesTask.recurrence.name,
                recurrenceEndAt = seriesTask.recurrenceEndAt,
                seriesId = seriesId,
                occurrenceAt = occurrence.dueAt,
                createdAt = now,
                modifiedAt = now,
            )
        )
        replaceSubtasks(
            nextId,
            seriesTask.subtasks.mapIndexed { index, subtask ->
                SubtaskEntity(
                    taskId = nextId,
                    text = subtask.text,
                    completed = false,
                    position = index,
                )
            },
        )
        replaceLabels(nextId, seriesTask.labels)
        entryDao.insertActivity(
            ActivityEntity(entryId = nextId, kind = "TASK_CREATED", occurredAt = now)
        )
        return taskDao.find(nextId)?.toDomain()
    }

    private suspend fun completedTask(id: Long): Task {
        val details = taskDao.find(id) ?: error("La tarea ya no existe.")
        return details.toDomain().copy(
            status = TaskStatus.COMPLETED,
            subtasks = details.subtasks.map { Subtask(it.id, it.text, completed = true) },
        )
    }

    private suspend fun replaceSubtasks(taskId: Long, subtasks: List<SubtaskEntity>) {
        taskDao.deleteSubtasks(taskId)
        if (subtasks.isNotEmpty()) {
            taskDao.insertSubtasks(subtasks.map { it.copy(id = 0, taskId = taskId) })
        }
    }

    private suspend fun replaceLabels(taskId: Long, labels: List<String>) {
        taskDao.deleteTaskLabels(taskId)
        labels.forEach { name ->
            val label = taskDao.findLabel(name) ?: run {
                val labelId = taskDao.insertLabel(LabelEntity(name = name))
                if (labelId > 0) LabelEntity(id = labelId, name = name) else taskDao.findLabel(name)
            }
            label?.let { taskDao.insertTaskLabel(TaskLabelCrossRef(taskId, it.id)) }
        }
    }

    private fun normalizeSubtasks(drafts: List<SubtaskDraft>, forceCompleted: Boolean): List<SubtaskEntity> =
        drafts.mapNotNull { draft ->
            val text = draft.text.trim()
            if (text.isEmpty()) null else text to draft.completed
        }.mapIndexed { index, (text, completed) ->
            SubtaskEntity(
                taskId = 0,
                text = text,
                completed = forceCompleted || completed,
                position = index,
            )
        }

    private fun syncReminder(task: Task, now: Long) {
        val reminderAt = task.reminderAt
        if (!task.isCompleted && task.reminderEnabled && reminderAt != null && reminderAt > now) {
            scheduler.schedule(task.id, reminderAt)
        } else {
            scheduler.cancel(task.id)
        }
    }

    private fun normalizeLabel(value: String): String =
        value.trim().lowercase(Locale.ROOT).take(30)
}

private fun TaskWithDetails.toDomain(): Task {
    val status = TaskStatus.entries.firstOrNull { it.name == task.status } ?: TaskStatus.PENDING
    val priority = TaskPriority.entries.firstOrNull { it.rank == task.priority }
        ?: TaskPriority.UNSPECIFIED
    val recurrence = TaskRecurrence.entries.firstOrNull { it.name == task.recurrence }
        ?: TaskRecurrence.NONE
    return Task(
        id = task.id,
        title = task.title,
        description = task.description,
        status = status,
        priority = priority,
        startAt = task.startAt,
        dueAt = task.dueAt,
        dueHasTime = task.dueHasTime,
        reminderEnabled = task.reminderEnabled,
        reminderAt = task.reminderAt,
        recurrence = recurrence,
        recurrenceEndAt = task.recurrenceEndAt,
        seriesId = task.seriesId,
        occurrenceAt = task.occurrenceAt,
        createdAt = task.createdAt,
        modifiedAt = task.modifiedAt,
        labels = labels.map { it.name }.sorted(),
        subtasks = subtasks.sortedBy { it.position }.map {
            Subtask(it.id, it.text, it.completed)
        },
    )
}

private fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    status = status.name,
    priority = priority.rank,
    startAt = startAt,
    dueAt = dueAt,
    dueHasTime = dueHasTime,
    reminderEnabled = reminderEnabled,
    reminderAt = reminderAt,
    recurrence = recurrence.name,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = seriesId,
    occurrenceAt = occurrenceAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)
