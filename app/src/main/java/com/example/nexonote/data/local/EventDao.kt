package com.example.nexonote.data.local

import androidx.room.*
import com.example.nexonote.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events", foreignKeys = [ForeignKey(
    entity = EventEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("parentId", "originalStart", unique = true), Index("startAt")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val startAt: Long, val endAt: Long,
    val description: String, val allDay: Boolean, val color: String, val category: String,
    val zoneId: String, val recurrence: String, val repeatUntil: Long?, val reminderMinutes: Int?,
    val parentId: Long? = null, val originalStart: Long? = null, val cancelled: Boolean = false,
    val revision: Long = 1, val notifiedStart: Long? = null,
    val plannedStart: Long? = null,
) {
    fun domain() = CalendarEvent(id, EventInput(title, startAt, endAt, description, allDay, color,
        category, zoneId, TaskRecurrence.valueOf(recurrence), repeatUntil, reminderMinutes),
        parentId, originalStart, cancelled, revision, notifiedStart, plannedStart)
}

fun EventInput.entity(id: Long = 0, revision: Long = 1) = EventEntity(
    id, title.trim(), startAt, endAt, description.trim(), allDay, color, category.trim(), zoneId,
    recurrence.name, repeatUntil, reminderMinutes, revision = revision,
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startAt, id") fun observeAll(): Flow<List<EventEntity>>
    @Query("SELECT * FROM events ORDER BY startAt, id") suspend fun all(): List<EventEntity>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun find(id: Long): EventEntity?
    @Query("SELECT * FROM events WHERE parentId = :id AND originalStart = :start")
    suspend fun exception(id: Long, start: Long): EventEntity?
    @Insert suspend fun insert(event: EventEntity): Long
    @Update suspend fun update(event: EventEntity)
    @Query("DELETE FROM events WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM events WHERE parentId = :id") suspend fun deleteExceptions(id: Long)
    @Query("UPDATE events SET notifiedStart = :start WHERE id = :id AND revision = :revision")
    suspend fun markNotified(id: Long, revision: Long, start: Long)
    @Query("UPDATE events SET plannedStart = :start WHERE id = :id AND revision = :revision")
    suspend fun plan(id: Long, revision: Long, start: Long?)
}
