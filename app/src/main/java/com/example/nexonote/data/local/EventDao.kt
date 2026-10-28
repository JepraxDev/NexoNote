package com.example.nexonote.data.local

import androidx.room.*
import com.example.nexonote.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("parentId", "originalStart", unique = true),
        Index("startAt"),
        Index("folderId"),
        Index("categoryId"),
        Index("favorite"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val startAt: Long, val endAt: Long,
    val description: String, val allDay: Boolean, val color: String, val category: String,
    val zoneId: String, val recurrence: String, val repeatUntil: Long?, val reminderMinutes: Int?,
    val parentId: Long? = null, val originalStart: Long? = null, val cancelled: Boolean = false,
    val revision: Long = 1, val notifiedStart: Long? = null,
    val plannedStart: Long? = null,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
) {
    fun domain(tags: List<String> = emptyList()) = CalendarEvent(
        id,
        EventInput(
            title, startAt, endAt, description, allDay, color, category, zoneId,
            TaskRecurrence.valueOf(recurrence), repeatUntil, reminderMinutes,
            favorite, folderId, categoryId, tags,
        ),
        parentId, originalStart, cancelled, revision, notifiedStart, plannedStart,
    )
}

fun EventInput.entity(id: Long = 0, revision: Long = 1) = EventEntity(
    id, title.trim(), startAt, endAt, description.trim(), allDay, color, category.trim(), zoneId,
    recurrence.name, repeatUntil, reminderMinutes, revision = revision,
    favorite = favorite, folderId = folderId, categoryId = categoryId,
)

@Entity(
    tableName = "event_labels",
    primaryKeys = ["eventId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("labelId")],
)
data class EventLabelCrossRef(
    val eventId: Long,
    val labelId: Long,
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

    @Query("SELECT * FROM event_labels") suspend fun allEventLabels(): List<EventLabelCrossRef>
    @Query("SELECT * FROM event_labels") fun observeAllLabels(): Flow<List<EventLabelCrossRef>>
    @Query("SELECT * FROM event_labels WHERE eventId = :eventId")
    suspend fun labelsForEvent(eventId: Long): List<EventLabelCrossRef>
    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1") suspend fun findLabel(name: String): LabelEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLabel(label: LabelEntity): Long
    @Query("DELETE FROM event_labels WHERE eventId = :eventId") suspend fun deleteEventLabels(eventId: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventLabel(crossRef: EventLabelCrossRef)
}
