package com.example.nexonote.data.repository

import com.example.nexonote.data.local.ActivityRecord
import com.example.nexonote.data.local.EntryDao
import com.example.nexonote.data.local.EntryEntity
import com.example.nexonote.domain.model.Activity
import com.example.nexonote.domain.model.ActivityKind
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.repository.ActivityRepository
import com.example.nexonote.domain.repository.EntryRepository
import kotlinx.coroutines.flow.map

private const val RECENT_ACTIVITY_LIMIT = 5

class LocalEntryRepository(private val dao: EntryDao) : EntryRepository {
    override val entries = dao.observeAll().map { entities ->
        entities.map {
            Entry(it.id, it.title, EntryType.valueOf(it.type), it.completed,
                it.modifiedAt, it.dueAt,
                TaskPriority.entries.firstOrNull { priority -> priority.rank == it.priority }
                    ?: TaskPriority.UNSPECIFIED)
        }
    }

    override suspend fun create(title: String, type: EntryType) {
        val normalized = title.trim()
        require(normalized.isNotEmpty() && normalized.length <= 120)
        val now = System.currentTimeMillis()
        dao.createAndLog(
            EntryEntity(title = normalized, type = type.name, modifiedAt = now),
            occurredAt = now,
        )
    }

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        dao.setTaskCompletedAndLog(id, completed, System.currentTimeMillis())
    }
}

class LocalActivityRepository(private val dao: EntryDao) : ActivityRepository {
    override val recent = dao.observeRecentActivity(RECENT_ACTIVITY_LIMIT).map { records ->
        records.mapNotNull(ActivityRecord::toDomain)
    }
}

private fun ActivityRecord.toDomain(): Activity? {
    val activityKind = when (kind) {
        "NOTE_CREATED" -> ActivityKind.NOTE_CREATED
        "TASK_CREATED" -> ActivityKind.TASK_CREATED
        "TABLE_CREATED" -> ActivityKind.TABLE_CREATED
        "TASK_COMPLETED" -> ActivityKind.TASK_COMPLETED
        "TASK_REOPENED" -> ActivityKind.TASK_REOPENED
        else -> return null
    }
    return Activity(id, entryId, activityKind, occurredAt, title)
}
