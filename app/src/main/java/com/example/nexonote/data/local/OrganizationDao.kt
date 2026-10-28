package com.example.nexonote.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "folders",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "FOLDER",
    val color: String = "#6750A4",
    val parentId: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: String = "#6750A4",
    val createdAt: Long,
    val modifiedAt: Long,
)

@Entity(
    tableName = "saved_views",
    foreignKeys = [
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
    indices = [Index("folderId"), Index("categoryId"), Index(value = ["name"], unique = true)],
)
data class SavedViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val query: String = "",
    val categoryId: Long? = null,
    val folderId: Long? = null,
    val includeSubfolders: Boolean = false,
    val tagIds: String = "",
    val types: String = "",
    val favoriteOnly: Boolean = false,
    val dateFilter: String = "ANY",
    val stateFilter: String = "ANY",
    val sort: String = "DATE_DESC",
    val createdAt: Long,
    val modifiedAt: Long,
)

@Entity(
    tableName = "table_labels",
    primaryKeys = ["tableId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
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
data class TableLabelCrossRef(
    val tableId: Long,
    val labelId: Long,
)

data class FolderSummaryEntity(
    @Embedded val folder: FolderEntity,
    val itemCount: Int,
)

data class CategorySummaryEntity(
    @Embedded val category: CategoryEntity,
    val itemCount: Int,
)

data class TagSummaryEntity(
    @Embedded val label: LabelEntity,
    val itemCount: Int,
)

data class SearchRowEntity(
    val type: String,
    val id: Long,
    val title: String,
    val snippet: String?,
    val date: Long?,
    val favorite: Boolean,
    val folderId: Long?,
    val categoryId: Long?,
    val folderName: String?,
    val categoryName: String?,
    val folderColor: String?,
    val tagIds: String?,
    val status: String?,
    val priority: Int,
)

@Dao
interface OrganizationDao {
    @Query(
        """
        SELECT f.*,
            (SELECT COUNT(*) FROM notes n WHERE n.folderId = f.id AND n.trashed = 0)
            + (SELECT COUNT(*) FROM tasks t WHERE t.folderId = f.id)
            + (SELECT COUNT(*) FROM custom_tables ct WHERE ct.folderId = f.id)
            + (SELECT COUNT(*) FROM events e WHERE e.folderId = f.id AND e.parentId IS NULL AND e.cancelled = 0)
            AS itemCount
        FROM folders f
        ORDER BY f.name COLLATE NOCASE, f.id
        """
    )
    fun observeFolders(): Flow<List<FolderSummaryEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findFolder(id: Long): FolderEntity?

    @Query("SELECT * FROM folders")
    suspend fun allFolders(): List<FolderEntity>

    @Query("SELECT COUNT(*) FROM folders WHERE parentId = :parentId AND name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countFolderName(parentId: Long?, name: String, excludeId: Long): Int

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE folders SET parentId = :parentId, modifiedAt = :modifiedAt WHERE parentId = :id")
    suspend fun moveChildFolders(id: Long, parentId: Long?, modifiedAt: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM notes n WHERE n.categoryId = c.id AND n.trashed = 0)
            + (SELECT COUNT(*) FROM tasks t WHERE t.categoryId = c.id)
            + (SELECT COUNT(*) FROM custom_tables ct WHERE ct.categoryId = c.id)
            + (SELECT COUNT(*) FROM events e WHERE e.categoryId = c.id AND e.parentId IS NULL AND e.cancelled = 0)
            AS itemCount
        FROM categories c
        ORDER BY c.name COLLATE NOCASE, c.id
        """
    )
    fun observeCategories(): Flow<List<CategorySummaryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findCategory(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countCategoryName(name: String, excludeId: Long): Int

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query(
        """
        SELECT l.*,
            (SELECT COUNT(*) FROM note_labels x WHERE x.labelId = l.id)
            + (SELECT COUNT(*) FROM task_labels x WHERE x.labelId = l.id)
            + (SELECT COUNT(*) FROM table_labels x WHERE x.labelId = l.id)
            + (SELECT COUNT(*) FROM event_labels x WHERE x.labelId = l.id)
            AS itemCount
        FROM labels l
        ORDER BY l.name COLLATE NOCASE, l.id
        """
    )
    fun observeTags(): Flow<List<TagSummaryEntity>>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun findTag(id: Long): LabelEntity?

    @Query("SELECT * FROM labels WHERE id IN (:ids)")
    suspend fun findTags(ids: List<Long>): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagByName(name: String): LabelEntity?

    @Query("SELECT COUNT(*) FROM labels WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countTagName(name: String, excludeId: Long): Int

    @Insert
    suspend fun insertTag(tag: LabelEntity): Long

    @Update
    suspend fun updateTag(tag: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun moveNote(id: Long, folderId: Long?)

    @Query("SELECT folderId FROM notes WHERE id = :id")
    suspend fun findNoteFolder(id: Long): Long?

    @Query("SELECT folderId FROM tasks WHERE id = :id")
    suspend fun findTaskFolder(id: Long): Long?

    @Query("UPDATE tasks SET folderId = :folderId WHERE id = :id")
    suspend fun moveTask(id: Long, folderId: Long?)

    @Query("UPDATE custom_tables SET folderId = :folderId WHERE id = :id")
    suspend fun moveTable(id: Long, folderId: Long?)

    @Query("UPDATE events SET folderId = :folderId WHERE id = :id")
    suspend fun moveEvent(id: Long, folderId: Long?)

    @Query("UPDATE notes SET folderId = :target WHERE folderId = :source")
    suspend fun moveNotesFromFolder(source: Long, target: Long?)

    @Query("UPDATE tasks SET folderId = :target WHERE folderId = :source")
    suspend fun moveTasksFromFolder(source: Long, target: Long?)

    @Query("UPDATE custom_tables SET folderId = :target WHERE folderId = :source")
    suspend fun moveTablesFromFolder(source: Long, target: Long?)

    @Query("UPDATE events SET folderId = :target WHERE folderId = :source")
    suspend fun moveEventsFromFolder(source: Long, target: Long?)

    @Query("UPDATE saved_views SET folderId = :target, modifiedAt = :modifiedAt WHERE folderId = :source")
    suspend fun moveSavedViewsFromFolder(source: Long, target: Long?, modifiedAt: Long)

    @Query("UPDATE notes SET categoryId = :categoryId WHERE id = :id")
    suspend fun categorizeNote(id: Long, categoryId: Long?)

    @Query("UPDATE tasks SET categoryId = :categoryId WHERE id = :id")
    suspend fun categorizeTask(id: Long, categoryId: Long?)

    @Query("UPDATE custom_tables SET categoryId = :categoryId WHERE id = :id")
    suspend fun categorizeTable(id: Long, categoryId: Long?)

    @Query("UPDATE events SET categoryId = :categoryId WHERE id = :id")
    suspend fun categorizeEvent(id: Long, categoryId: Long?)

    @Query("UPDATE notes SET favorite = :favorite WHERE id = :id")
    suspend fun favoriteNote(id: Long, favorite: Boolean)

    @Query("UPDATE tasks SET favorite = :favorite WHERE id = :id")
    suspend fun favoriteTask(id: Long, favorite: Boolean)

    @Query("UPDATE custom_tables SET favorite = :favorite WHERE id = :id")
    suspend fun favoriteTable(id: Long, favorite: Boolean)

    @Query("UPDATE events SET favorite = :favorite WHERE id = :id")
    suspend fun favoriteEvent(id: Long, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM note_labels WHERE noteId = :noteId AND labelId = :labelId")
    suspend fun noteTagLinkCount(noteId: Long, labelId: Long): Int

    @Query("SELECT COUNT(*) FROM task_labels WHERE taskId = :taskId AND labelId = :labelId")
    suspend fun taskTagLinkCount(taskId: Long, labelId: Long): Int

    @Query("SELECT COUNT(*) FROM table_labels WHERE tableId = :tableId AND labelId = :labelId")
    suspend fun tableTagLinkCount(tableId: Long, labelId: Long): Int

    @Query("DELETE FROM table_labels WHERE tableId = :tableId")
    suspend fun deleteTableLabels(tableId: Long)

    @Query("SELECT COUNT(*) FROM event_labels WHERE eventId = :eventId AND labelId = :labelId")
    suspend fun eventTagLinkCount(eventId: Long, labelId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteLabel(crossRef: NoteLabelCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskLabel(crossRef: TaskLabelCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTableLabel(crossRef: TableLabelCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventLabel(crossRef: EventLabelCrossRef)

    @Query("DELETE FROM note_labels WHERE noteId = :id AND labelId = :labelId")
    suspend fun deleteNoteLabel(id: Long, labelId: Long)

    @Query("DELETE FROM task_labels WHERE taskId = :id AND labelId = :labelId")
    suspend fun deleteTaskLabel(id: Long, labelId: Long)

    @Query("DELETE FROM table_labels WHERE tableId = :id AND labelId = :labelId")
    suspend fun deleteTableLabel(id: Long, labelId: Long)

    @Query("DELETE FROM event_labels WHERE eventId = :id AND labelId = :labelId")
    suspend fun deleteEventLabel(id: Long, labelId: Long)

    @Query("SELECT labelId FROM note_labels WHERE noteId = :id")
    suspend fun noteTagIds(id: Long): List<Long>

    @Query("SELECT labelId FROM task_labels WHERE taskId = :id")
    suspend fun taskTagIds(id: Long): List<Long>

    @Query("SELECT labelId FROM table_labels WHERE tableId = :id")
    suspend fun tableTagIds(id: Long): List<Long>

    @Query("SELECT labelId FROM event_labels WHERE eventId = :id")
    suspend fun eventTagIds(id: Long): List<Long>

    @Query(
        """
        SELECT * FROM (
            SELECT 'NOTE' AS type, n.id AS id, n.title AS title, n.content AS snippet,
                COALESCE(n.modifiedAt, n.createdAt) AS date, n.favorite AS favorite,
                n.folderId AS folderId, n.categoryId AS categoryId,
                f.name AS folderName, c.name AS categoryName, f.color AS folderColor,
                (SELECT GROUP_CONCAT(x.labelId) FROM note_labels x WHERE x.noteId = n.id) AS tagIds,
                NULL AS status, 0 AS priority
            FROM notes n
            LEFT JOIN folders f ON f.id = n.folderId
            LEFT JOIN categories c ON c.id = n.categoryId
            WHERE n.trashed = 0 AND n.archived = 0
            UNION ALL
            SELECT 'TASK', t.id, t.title, t.description, COALESCE(t.dueAt, t.modifiedAt), t.favorite,
                t.folderId, t.categoryId, f.name, c.name, f.color,
                (SELECT GROUP_CONCAT(x.labelId) FROM task_labels x WHERE x.taskId = t.id),
                t.status, t.priority
            FROM tasks t
            LEFT JOIN folders f ON f.id = t.folderId
            LEFT JOIN categories c ON c.id = t.categoryId
            UNION ALL
            SELECT 'TABLE', ct.id, ct.name, ct.description, ct.modifiedAt, ct.favorite,
                ct.folderId, ct.categoryId, f.name, c.name, f.color,
                (SELECT GROUP_CONCAT(x.labelId) FROM table_labels x WHERE x.tableId = ct.id),
                NULL, 0
            FROM custom_tables ct
            LEFT JOIN folders f ON f.id = ct.folderId
            LEFT JOIN categories c ON c.id = ct.categoryId
            UNION ALL
            SELECT 'EVENT', e.id, e.title, e.description, e.startAt, e.favorite,
                e.folderId, e.categoryId, f.name, c.name, f.color,
                (SELECT GROUP_CONCAT(x.labelId) FROM event_labels x WHERE x.eventId = e.id),
                NULL, 0
            FROM events e
            LEFT JOIN folders f ON f.id = e.folderId
            LEFT JOIN categories c ON c.id = e.categoryId
            WHERE e.parentId IS NULL AND e.cancelled = 0
        )
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR IFNULL(snippet, '') LIKE '%' || :query || '%'
                OR IFNULL(folderName, '') LIKE '%' || :query || '%'
                OR IFNULL(categoryName, '') LIKE '%' || :query || '%'
                OR EXISTS (
                    SELECT 1 FROM labels l
                    WHERE l.name LIKE '%' || :query || '%'
                        AND ',' || IFNULL(tagIds, '') || ',' LIKE '%,' || l.id || ',%'
                ))
            AND (:categoryId IS NULL OR categoryId = :categoryId)
            AND (:favoriteOnly = 0 OR favorite = 1)
            AND (:from IS NULL OR date >= :from)
            AND (:to IS NULL OR date <= :to)
        ORDER BY date DESC, id DESC
        LIMIT 500
        """
    )
    fun observeSearch(
        query: String,
        categoryId: Long?,
        favoriteOnly: Boolean,
        from: Long?,
        to: Long?,
    ): Flow<List<SearchRowEntity>>

    @Query("SELECT occurredAt FROM activities ORDER BY occurredAt DESC")
    fun observeActivityTimes(): Flow<List<Long>>

    @Query("SELECT createdAt FROM custom_rows WHERE createdAt IS NOT NULL")
    fun observeRowCreationTimes(): Flow<List<Long>>

    @Query("SELECT * FROM saved_views ORDER BY name COLLATE NOCASE, id")
    fun observeSavedViews(): Flow<List<SavedViewEntity>>

    @Query("SELECT * FROM saved_views")
    suspend fun allSavedViews(): List<SavedViewEntity>

    @Query("SELECT COUNT(*) FROM saved_views WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countSavedViewName(name: String, excludeId: Long): Int

    @Insert
    suspend fun insertSavedView(view: SavedViewEntity): Long

    @Update
    suspend fun updateSavedView(view: SavedViewEntity)

    @Query("DELETE FROM saved_views WHERE id = :id")
    suspend fun deleteSavedView(id: Long)
}
