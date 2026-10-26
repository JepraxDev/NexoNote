package com.example.nexonote.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val completed: Boolean = false,
    val modifiedAt: Long? = null,
    val dueAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
)

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val kind: String,
    val occurredAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class NoteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val content: String,
    val type: String,
    val color: String,
    val pinned: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val trashedAt: Long?,
    val createdAt: Long?,
    val modifiedAt: Long?,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("noteId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    val checked: Boolean,
    val position: Int,
)

@Entity(tableName = "labels", indices = [Index(value = ["name"], unique = true)])
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "note_labels",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
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
data class NoteLabelCrossRef(
    val noteId: Long,
    val labelId: Long,
)

data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val checklist: List<ChecklistItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteLabelCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "labelId",
        ),
    )
    val labels: List<LabelEntity>,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("status"),
        Index("dueAt"),
        Index(value = ["seriesId", "occurrenceAt"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String,
    val status: String,
    val priority: Int,
    val startAt: Long?,
    val dueAt: Long?,
    val dueHasTime: Boolean,
    val reminderEnabled: Boolean,
    val reminderAt: Long?,
    val recurrence: String,
    val recurrenceEndAt: Long?,
    val seriesId: Long?,
    val occurrenceAt: Long?,
    val createdAt: Long?,
    val modifiedAt: Long?,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    val completed: Boolean,
    val position: Int,
)

@Entity(
    tableName = "task_labels",
    primaryKeys = ["taskId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
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
data class TaskLabelCrossRef(
    val taskId: Long,
    val labelId: Long,
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskLabelCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "labelId",
        ),
    )
    val labels: List<LabelEntity>,
)

@Entity(
    tableName = "custom_tables",
    foreignKeys = [ForeignKey(
        entity = EntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("modifiedAt")],
)
data class CustomTableEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String,
    val icon: String,
    val color: String,
    val createdAt: Long?,
    val modifiedAt: Long?,
)

@Entity(
    tableName = "custom_columns",
    foreignKeys = [ForeignKey(
        entity = CustomTableEntity::class,
        parentColumns = ["id"],
        childColumns = ["tableId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tableId")],
)
data class CustomColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val name: String,
    val type: String,
    val required: Boolean,
    val isPrimary: Boolean,
    val defaultValue: String?,
    val position: Int,
)

@Entity(
    tableName = "column_options",
    foreignKeys = [ForeignKey(
        entity = CustomColumnEntity::class,
        parentColumns = ["id"],
        childColumns = ["columnId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("columnId"),
        Index(value = ["columnId", "name"], unique = true),
    ],
)
data class ColumnOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val columnId: Long,
    val name: String,
    val color: String,
    val position: Int,
)

@Entity(
    tableName = "custom_rows",
    foreignKeys = [ForeignKey(
        entity = CustomTableEntity::class,
        parentColumns = ["id"],
        childColumns = ["tableId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tableId"), Index("modifiedAt")],
)
data class CustomRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val title: String,
    val createdAt: Long?,
    val modifiedAt: Long?,
)

@Entity(
    tableName = "custom_cells",
    primaryKeys = ["rowId", "columnId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomRowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomColumnEntity::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("columnId")],
)
data class CustomCellEntity(
    val rowId: Long,
    val columnId: Long,
    val value: String?,
    val updatedAt: Long,
)

data class TableSummaryEntity(
    val id: Long,
    val name: String,
    val description: String,
    val icon: String,
    val color: String,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val columnCount: Int,
    val rowCount: Int,
)

data class RecentTableRowEntity(
    val rowId: Long,
    val tableId: Long,
    val tableName: String,
    val title: String,
    val modifiedAt: Long?,
)

data class ActivityRecord(
    val id: Long,
    val entryId: Long,
    val kind: String,
    val occurredAt: Long,
    val title: String?,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY id DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT activities.id, activities.entryId, activities.kind, activities.occurredAt, entries.title
        FROM activities
        LEFT JOIN entries ON entries.id = activities.entryId
        ORDER BY activities.occurredAt DESC, activities.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentActivity(limit: Int): Flow<List<ActivityRecord>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertActivity(activity: ActivityEntity)

    @Query(
        "UPDATE entries SET completed = :completed, modifiedAt = :modifiedAt " +
            "WHERE id = :id AND type = 'TASK' AND completed != :completed"
    )
    suspend fun updateTaskCompleted(id: Long, completed: Boolean, modifiedAt: Long): Int

    @Query("UPDATE entries SET completed = :completed WHERE id = :id AND type = 'TASK'")
    suspend fun updateEntryCompleted(id: Long, completed: Boolean)

    @Query("UPDATE entries SET title = :title, modifiedAt = :modifiedAt WHERE id = :id AND type = 'NOTE'")
    suspend fun updateNoteSummary(id: Long, title: String, modifiedAt: Long)

    @Query(
        "UPDATE entries SET title = :title, modifiedAt = :modifiedAt, dueAt = :dueAt, " +
            "priority = :priority WHERE id = :id AND type = 'TASK'"
    )
    suspend fun updateTaskSummary(
        id: Long,
        title: String,
        modifiedAt: Long,
        dueAt: Long?,
        priority: Int,
    )

    @Query(
        "UPDATE entries SET title = :title, modifiedAt = :modifiedAt " +
            "WHERE id = :id AND type = 'TABLE'"
    )
    suspend fun updateTableSummary(id: Long, title: String, modifiedAt: Long): Int

    @Query("UPDATE entries SET modifiedAt = :modifiedAt WHERE id = :id AND type = 'TABLE'")
    suspend fun touchTable(id: Long, modifiedAt: Long): Int

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM entries WHERE id IN (SELECT id FROM notes WHERE trashed = 1)")
    suspend fun deleteTrashedNotes()

    @Transaction
    suspend fun createAndLog(entry: EntryEntity, occurredAt: Long): Long {
        val id = insert(entry)
        val kind = when (entry.type) {
            "NOTE" -> "NOTE_CREATED"
            "TASK" -> "TASK_CREATED"
            else -> "TABLE_CREATED"
        }
        insertActivity(ActivityEntity(entryId = id, kind = kind, occurredAt = occurredAt))
        return id
    }

    @Transaction
    suspend fun setTaskCompletedAndLog(id: Long, completed: Boolean, occurredAt: Long): Boolean {
        if (updateTaskCompleted(id, completed, occurredAt) == 0) return false
        insertActivity(ActivityEntity(
            entryId = id,
            kind = if (completed) "TASK_COMPLETED" else "TASK_REOPENED",
            occurredAt = occurredAt,
        ))
        return true
    }
}

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes ORDER BY pinned DESC, modifiedAt DESC, id DESC")
    fun observeAll(): Flow<List<NoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteWithDetails?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun find(id: Long): NoteWithDetails?

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("SELECT id FROM notes WHERE trashed = 1")
    suspend fun trashedIds(): List<Long>

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE notes SET archived = :archived, trashed = 0, trashedAt = NULL WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE notes SET trashed = 1, trashedAt = :trashedAt, archived = 0 WHERE id = :id")
    suspend fun trash(id: Long, trashedAt: Long)

    @Query("UPDATE notes SET trashed = 0, trashedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteChecklist(noteId: Long)

    @Insert
    suspend fun insertChecklist(items: List<ChecklistItemEntity>)

    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1")
    suspend fun findLabel(name: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabel(label: LabelEntity): Long

    @Query("SELECT name FROM labels ORDER BY name ASC")
    fun observeLabelNames(): Flow<List<String>>

    @Query("DELETE FROM note_labels WHERE noteId = :noteId")
    suspend fun deleteNoteLabels(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteLabel(crossRef: NoteLabelCrossRef)
}

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY dueAt IS NULL, dueAt, priority DESC, id DESC")
    fun observeAll(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun find(id: Long): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId AND occurrenceAt = :occurrenceAt LIMIT 1")
    suspend fun findByOccurrence(seriesId: Long, occurrenceAt: Long): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks WHERE reminderEnabled = 1 AND status != 'COMPLETED'")
    suspend fun findPendingReminders(): List<TaskWithDetails>

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, modifiedAt: Long)

    @Query(
        "UPDATE tasks SET recurrence = 'NONE', modifiedAt = :modifiedAt " +
            "WHERE recurrence != 'NONE' AND (id = :id OR seriesId = :seriesId)"
    )
    suspend fun stopRecurrence(id: Long, seriesId: Long?, modifiedAt: Long): Int

    @Query("UPDATE subtasks SET completed = 1 WHERE taskId = :taskId")
    suspend fun completeSubtasks(taskId: Long)

    @Query(
        "UPDATE subtasks SET completed = :completed WHERE id = :subtaskId AND taskId = :taskId"
    )
    suspend fun updateSubtaskCompleted(taskId: Long, subtaskId: Long, completed: Boolean): Int

    @Query("SELECT * FROM subtasks WHERE id = :subtaskId AND taskId = :taskId LIMIT 1")
    suspend fun findSubtask(taskId: Long, subtaskId: Long): SubtaskEntity?

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasks(taskId: Long)

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1")
    suspend fun findLabel(name: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabel(label: LabelEntity): Long

    @Query("SELECT name FROM labels ORDER BY name ASC")
    fun observeLabelNames(): Flow<List<String>>

    @Query("DELETE FROM task_labels WHERE taskId = :taskId")
    suspend fun deleteTaskLabels(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskLabel(crossRef: TaskLabelCrossRef)

    @Query("DELETE FROM entries WHERE id = :id AND type = 'TASK'")
    suspend fun deleteTaskEntry(id: Long)
}

@Dao
interface TableDao {
    @Query(
        """
        SELECT t.id, t.name, t.description, t.icon, t.color, t.createdAt, t.modifiedAt,
            (SELECT COUNT(*) FROM custom_columns c WHERE c.tableId = t.id) AS columnCount,
            (SELECT COUNT(*) FROM custom_rows r WHERE r.tableId = t.id) AS rowCount
        FROM custom_tables t
        ORDER BY t.modifiedAt DESC, t.id DESC
        """
    )
    fun observeTables(): Flow<List<TableSummaryEntity>>

    @Query("SELECT * FROM custom_tables WHERE id = :id")
    fun observeTable(id: Long): Flow<CustomTableEntity?>

    @Query("SELECT * FROM custom_columns WHERE tableId = :tableId ORDER BY position, id")
    fun observeColumns(tableId: Long): Flow<List<CustomColumnEntity>>

    @Query(
        """
        SELECT o.* FROM column_options o
        INNER JOIN custom_columns c ON c.id = o.columnId
        WHERE c.tableId = :tableId
        ORDER BY o.position, o.id
        """
    )
    fun observeOptions(tableId: Long): Flow<List<ColumnOptionEntity>>

    @Query(
        """
        SELECT r.id AS rowId, r.tableId, t.name AS tableName, r.title, r.modifiedAt
        FROM custom_rows r
        INNER JOIN custom_tables t ON t.id = r.tableId
        ORDER BY r.modifiedAt DESC, r.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentRows(limit: Int): Flow<List<RecentTableRowEntity>>

    @Query("SELECT * FROM custom_tables WHERE id = :id")
    suspend fun findTable(id: Long): CustomTableEntity?

    @Query("SELECT * FROM custom_columns WHERE tableId = :tableId ORDER BY position, id")
    suspend fun findColumns(tableId: Long): List<CustomColumnEntity>

    @Query("SELECT * FROM custom_columns WHERE id = :id")
    suspend fun findColumn(id: Long): CustomColumnEntity?

    @Query("SELECT * FROM custom_columns WHERE tableId = :tableId AND isPrimary = 1 LIMIT 1")
    suspend fun findPrimaryColumn(tableId: Long): CustomColumnEntity?

    @Query("SELECT * FROM column_options WHERE columnId = :columnId ORDER BY position, id")
    suspend fun findOptionsForColumn(columnId: Long): List<ColumnOptionEntity>

    @Query(
        """
        SELECT o.* FROM column_options o
        INNER JOIN custom_columns c ON c.id = o.columnId
        WHERE c.tableId = :tableId
        ORDER BY o.position, o.id
        """
    )
    suspend fun findOptions(tableId: Long): List<ColumnOptionEntity>

    @Query("SELECT * FROM column_options WHERE id = :id")
    suspend fun findOption(id: Long): ColumnOptionEntity?

    @Query("SELECT * FROM column_options WHERE columnId = :columnId AND name = :name LIMIT 1")
    suspend fun findOptionByName(columnId: Long, name: String): ColumnOptionEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM custom_columns WHERE tableId = :tableId")
    suspend fun maxColumnPosition(tableId: Long): Int

    @Query("SELECT COUNT(*) FROM custom_columns WHERE tableId = :tableId")
    suspend fun countColumns(tableId: Long): Int

    @Query("SELECT * FROM custom_rows WHERE id = :id")
    suspend fun findRow(id: Long): CustomRowEntity?

    @Query("SELECT * FROM custom_rows WHERE tableId = :tableId AND id = :id LIMIT 1")
    suspend fun findRowInTable(tableId: Long, id: Long): CustomRowEntity?

    @Query("SELECT * FROM custom_rows WHERE tableId = :tableId ORDER BY id")
    suspend fun findRows(tableId: Long): List<CustomRowEntity>

    @Query(
        """
        SELECT c.* FROM custom_cells c
        INNER JOIN custom_rows r ON r.id = c.rowId
        WHERE r.tableId = :tableId
        """
    )
    suspend fun findCellsForTable(tableId: Long): List<CustomCellEntity>

    @Query("SELECT * FROM custom_cells WHERE rowId = :rowId")
    suspend fun findCells(rowId: Long): List<CustomCellEntity>

    @Query("SELECT * FROM custom_cells WHERE rowId IN (:rowIds)")
    suspend fun findCellsForRows(rowIds: List<Long>): List<CustomCellEntity>

    @Query("SELECT * FROM custom_cells WHERE columnId = :columnId")
    suspend fun findCellsForColumn(columnId: Long): List<CustomCellEntity>

    @Query("SELECT * FROM custom_cells WHERE rowId = :rowId AND columnId = :columnId LIMIT 1")
    suspend fun findCell(rowId: Long, columnId: Long): CustomCellEntity?

    @Insert
    suspend fun insertTable(table: CustomTableEntity)

    @Update
    suspend fun updateTable(table: CustomTableEntity)

    @Query("UPDATE custom_tables SET name = :name, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateTableName(id: Long, name: String, modifiedAt: Long)

    @Insert
    suspend fun insertColumn(column: CustomColumnEntity): Long

    @Update
    suspend fun updateColumn(column: CustomColumnEntity)

    @Query("UPDATE custom_columns SET position = :position WHERE id = :id")
    suspend fun updateColumnPosition(id: Long, position: Int)

    @Query("UPDATE custom_columns SET isPrimary = :isPrimary WHERE id = :id")
    suspend fun updateColumnPrimary(id: Long, isPrimary: Boolean)

    @Query("DELETE FROM custom_columns WHERE id = :id")
    suspend fun deleteColumn(id: Long)

    @Insert
    suspend fun insertOption(option: ColumnOptionEntity): Long

    @Update
    suspend fun updateOption(option: ColumnOptionEntity)

    @Query("DELETE FROM column_options WHERE id = :id")
    suspend fun deleteOption(id: Long)

    @Query("DELETE FROM column_options WHERE columnId = :columnId")
    suspend fun deleteOptions(columnId: Long)

    @Insert
    suspend fun insertRow(row: CustomRowEntity): Long

    @Update
    suspend fun updateRow(row: CustomRowEntity)

    @Query("UPDATE custom_rows SET title = :title, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateRowTitle(id: Long, title: String, modifiedAt: Long)

    @Query("DELETE FROM custom_rows WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCell(cell: CustomCellEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CustomCellEntity>)

    @Query("DELETE FROM custom_cells WHERE rowId = :rowId AND columnId = :columnId")
    suspend fun deleteCell(rowId: Long, columnId: Long)
}

@Database(
    entities = [
        EntryEntity::class,
        ActivityEntity::class,
        NoteEntity::class,
        ChecklistItemEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        TaskEntity::class,
        SubtaskEntity::class,
        TaskLabelCrossRef::class,
        CustomTableEntity::class,
        CustomColumnEntity::class,
        ColumnOptionEntity::class,
        CustomRowEntity::class,
        CustomCellEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class NexoDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun tableDao(): TableDao
}
