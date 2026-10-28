package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.CategoryEntity
import com.example.nexonote.data.local.CategorySummaryEntity
import com.example.nexonote.data.local.EventLabelCrossRef
import com.example.nexonote.data.local.FolderEntity
import com.example.nexonote.data.local.FolderSummaryEntity
import com.example.nexonote.data.local.LabelEntity
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.local.NoteLabelCrossRef
import com.example.nexonote.data.local.OrganizationDao
import com.example.nexonote.data.local.SavedViewEntity
import com.example.nexonote.data.local.SearchRowEntity
import com.example.nexonote.data.local.TableLabelCrossRef
import com.example.nexonote.data.local.TagSummaryEntity
import com.example.nexonote.data.local.TaskLabelCrossRef
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SavedView
import com.example.nexonote.domain.model.SearchDateFilter
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.SearchSort
import com.example.nexonote.domain.model.SearchStateFilter
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.model.TaskPriority
import com.example.nexonote.domain.model.TaskStatus
import com.example.nexonote.domain.model.addDays
import com.example.nexonote.domain.model.descendantsOf
import com.example.nexonote.domain.model.endOfDay
import com.example.nexonote.domain.model.folderCreatesCycle
import com.example.nexonote.domain.model.startOfDay
import com.example.nexonote.domain.model.validateCategoryName
import com.example.nexonote.domain.model.validateColor
import com.example.nexonote.domain.model.validateFolderDescription
import com.example.nexonote.domain.model.validateFolderName
import com.example.nexonote.domain.model.validateSavedViewName
import com.example.nexonote.domain.model.validateTagName
import com.example.nexonote.domain.repository.OrganizationRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalOrganizationRepository(
    private val database: NexoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : OrganizationRepository {
    private val dao: OrganizationDao = database.organizationDao()

    override val folders: Flow<List<Folder>> = dao.observeFolders().map { items ->
        items.map(FolderSummaryEntity::toDomain)
    }

    override val categories: Flow<List<Category>> = dao.observeCategories().map { items ->
        items.map(CategorySummaryEntity::toDomain)
    }

    override val tags: Flow<List<Tag>> = dao.observeTags().map { items ->
        items.map(TagSummaryEntity::toDomain)
    }

    override val favorites: Flow<List<OrganizationItem>> =
        search(SearchFilters(favoriteOnly = true, sort = SearchSort.DATE_DESC))

    override val activityTimes: Flow<List<Long>> = dao.observeActivityTimes()
    override val rowCreationTimes: Flow<List<Long>> = dao.observeRowCreationTimes()

    override val savedViews: Flow<List<SavedView>> =
        combine(dao.observeSavedViews(), dao.observeTags()) { views, tagRows ->
            val validTags = tagRows.map { it.label.id }.toSet()
            views.map { it.toDomain(validTags) }
        }

    override fun search(filters: SearchFilters): Flow<List<OrganizationItem>> {
        val now = clock()
        val range = dateRange(filters.date, now)
        return combine(
            dao.observeSearch(
                query = filters.query.trim(),
                categoryId = filters.categoryId,
                favoriteOnly = filters.favoriteOnly,
                from = range.first,
                to = range.second,
            ),
            dao.observeFolders(),
            dao.observeTags(),
        ) { rows, folderRows, tagRows ->
            val allFolders = folderRows.map(FolderSummaryEntity::toDomain)
            val folderFilter = filters.folderId?.let { root ->
                if (filters.includeSubfolders) setOf(root) + descendantsOf(root, allFolders) else setOf(root)
            }
            val tagsById = tagRows.associate { it.label.id to it.toDomain() }
            rows.asSequence()
                .map { it.toDomain(tagsById) }
                .filter { item ->
                    folderFilter == null || item.folderId in folderFilter
                }
                .filter { item -> filters.matches(item, now) }
                .sortedWith(comparator(filters.sort))
                .toList()
        }
    }

    override suspend fun saveFolder(
        id: Long?,
        name: String,
        description: String,
        icon: FolderIcon,
        color: String,
        parentId: Long?,
    ): Long = database.withTransaction {
        validateFolderName(name)?.let { throw IllegalArgumentException(it) }
        validateFolderDescription(description)?.let { throw IllegalArgumentException(it) }
        validateColor(color)?.let { throw IllegalArgumentException(it) }
        val normalizedName = name.trim()
        val now = clock()
        val all = dao.allFolders()
        val existing = id?.let { folderId ->
            all.firstOrNull { it.id == folderId } ?: throw IllegalArgumentException("La carpeta ya no existe.")
        }
        if (folderCreatesCycle(id, parentId, all.associate { it.id to it.parentId })) {
            throw IllegalArgumentException("No puedes mover una carpeta dentro de sí misma o de una subcarpeta.")
        }
        if (parentId != null && all.none { it.id == parentId }) {
            throw IllegalArgumentException("La carpeta superior ya no existe.")
        }
        if (dao.countFolderName(parentId, normalizedName, id ?: 0L) > 0) {
            throw IllegalArgumentException("Ya existe una carpeta con ese nombre en este nivel.")
        }
        if (existing == null) {
            dao.insertFolder(
                FolderEntity(
                    name = normalizedName,
                    description = description.trim(),
                    icon = icon.name,
                    color = color.lowercase(Locale.ROOT),
                    parentId = parentId,
                    createdAt = now,
                    modifiedAt = now,
                )
            )
        } else {
            dao.updateFolder(
                existing.copy(
                    name = normalizedName,
                    description = description.trim(),
                    icon = icon.name,
                    color = color.lowercase(Locale.ROOT),
                    parentId = parentId,
                    modifiedAt = now,
                )
            )
            existing.id
        }
    }

    override suspend fun deleteFolder(id: Long) = database.withTransaction {
        val folder = dao.findFolder(id) ?: return@withTransaction
        val now = clock()
        val target = folder.parentId
        dao.moveChildFolders(id, target, now)
        dao.moveNotesFromFolder(id, target)
        dao.moveTasksFromFolder(id, target)
        dao.moveTablesFromFolder(id, target)
        dao.moveEventsFromFolder(id, target)
        dao.moveSavedViewsFromFolder(id, target, now)
        dao.deleteFolder(id)
    }

    override suspend fun saveCategory(id: Long?, name: String, color: String): Long =
        database.withTransaction {
            validateCategoryName(name)?.let { throw IllegalArgumentException(it) }
            validateColor(color)?.let { throw IllegalArgumentException(it) }
            val normalized = name.trim()
            if (dao.countCategoryName(normalized, id ?: 0) > 0) {
                throw IllegalArgumentException("Ya existe una categoría con ese nombre.")
            }
            val now = clock()
            val existing = id?.let { categoryId ->
                dao.findCategory(categoryId)
                    ?: throw IllegalArgumentException("La categoría ya no existe.")
            }
            if (existing == null) {
                dao.insertCategory(
                    CategoryEntity(
                        name = normalized,
                        color = color.lowercase(Locale.ROOT),
                        createdAt = now,
                        modifiedAt = now,
                    )
                )
            } else {
                dao.updateCategory(
                    existing.copy(
                        name = normalized,
                        color = color.lowercase(Locale.ROOT),
                        modifiedAt = now,
                    )
                )
                existing.id
            }
        }

    override suspend fun deleteCategory(id: Long) {
        // SQLite clears every referencing content row and saved view through ON DELETE SET NULL.
        database.withTransaction { dao.deleteCategory(id) }
    }

    override suspend fun saveTag(id: Long?, name: String, color: String): Long =
        database.withTransaction {
            validateTagName(name)?.let { throw IllegalArgumentException(it) }
            validateColor(color)?.let { throw IllegalArgumentException(it) }
            val normalized = name.trim().lowercase(Locale.ROOT)
            if (dao.countTagName(normalized, id ?: 0) > 0) {
                throw IllegalArgumentException("Ya existe una etiqueta con ese nombre.")
            }
            val existing = id?.let { tagId ->
                dao.findTag(tagId) ?: throw IllegalArgumentException("La etiqueta ya no existe.")
            }
            if (existing == null) {
                dao.insertTag(
                    LabelEntity(name = normalized, color = color.lowercase(Locale.ROOT))
                )
            } else {
                dao.updateTag(
                    existing.copy(name = normalized, color = color.lowercase(Locale.ROOT))
                )
                existing.id
            }
        }

    override suspend fun deleteTag(id: Long) {
        database.withTransaction { dao.deleteTag(id) }
    }

    override suspend fun setFavorite(type: ContentType, id: Long, favorite: Boolean) {
        database.withTransaction {
            when (type) {
                ContentType.NOTE -> dao.favoriteNote(id, favorite)
                ContentType.TASK -> dao.favoriteTask(id, favorite)
                ContentType.TABLE -> dao.favoriteTable(id, favorite)
                ContentType.EVENT -> dao.favoriteEvent(id, favorite)
            }
        }
    }

    override suspend fun moveToFolder(type: ContentType, id: Long, folderId: Long?) {
        database.withTransaction {
            if (folderId != null && dao.findFolder(folderId) == null) {
                throw IllegalArgumentException("La carpeta de destino ya no existe.")
            }
            when (type) {
                ContentType.NOTE -> dao.moveNote(id, folderId)
                ContentType.TASK -> dao.moveTask(id, folderId)
                ContentType.TABLE -> dao.moveTable(id, folderId)
                ContentType.EVENT -> dao.moveEvent(id, folderId)
            }
        }
    }

    override suspend fun assignCategory(type: ContentType, id: Long, categoryId: Long?): Boolean =
        database.withTransaction {
            if (categoryId != null && dao.findCategory(categoryId) == null) return@withTransaction false
            when (type) {
                ContentType.NOTE -> dao.categorizeNote(id, categoryId)
                ContentType.TASK -> dao.categorizeTask(id, categoryId)
                ContentType.TABLE -> dao.categorizeTable(id, categoryId)
                ContentType.EVENT -> dao.categorizeEvent(id, categoryId)
            }
            true
        }

    override suspend fun assignTag(type: ContentType, id: Long, tagId: Long, assigned: Boolean) {
        database.withTransaction {
            if (assigned && dao.findTag(tagId) == null) return@withTransaction
            when (type) {
                ContentType.NOTE -> if (assigned) {
                    dao.insertNoteLabel(NoteLabelCrossRef(id, tagId))
                } else {
                    dao.deleteNoteLabel(id, tagId)
                }
                ContentType.TASK -> if (assigned) {
                    dao.insertTaskLabel(TaskLabelCrossRef(id, tagId))
                } else {
                    dao.deleteTaskLabel(id, tagId)
                }
                ContentType.TABLE -> if (assigned) {
                    dao.insertTableLabel(TableLabelCrossRef(id, tagId))
                } else {
                    dao.deleteTableLabel(id, tagId)
                }
                ContentType.EVENT -> if (assigned) {
                    dao.insertEventLabel(EventLabelCrossRef(id, tagId))
                } else {
                    dao.deleteEventLabel(id, tagId)
                }
            }
        }
    }

    override suspend fun saveSavedView(id: Long?, name: String, filters: SearchFilters): Long =
        database.withTransaction {
            validateSavedViewName(name)?.let { throw IllegalArgumentException(it) }
            val normalized = name.trim()
            if (dao.countSavedViewName(normalized, id ?: 0) > 0) {
                throw IllegalArgumentException("Ya existe una vista con ese nombre.")
            }
            val now = clock()
            val existing = id?.let { viewId ->
                dao.allSavedViews().firstOrNull { it.id == viewId }
                    ?: throw IllegalArgumentException("La vista ya no existe.")
            }
            val entity = SavedViewEntity(
                id = existing?.id ?: 0,
                name = normalized,
                query = filters.query,
                categoryId = filters.categoryId,
                folderId = filters.folderId,
                includeSubfolders = filters.includeSubfolders,
                tagIds = filters.tagIds.sorted().joinToString(","),
                types = filters.types.map { it.name }.sorted().joinToString(","),
                favoriteOnly = filters.favoriteOnly,
                dateFilter = filters.date.name,
                stateFilter = filters.state.name,
                sort = filters.sort.name,
                createdAt = existing?.createdAt ?: now,
                modifiedAt = now,
            )
            if (existing == null) dao.insertSavedView(entity) else {
                dao.updateSavedView(entity)
                existing.id
            }
        }

    override suspend fun deleteSavedView(id: Long) {
        database.withTransaction { dao.deleteSavedView(id) }
    }

    private fun dateRange(filter: SearchDateFilter, now: Long): Pair<Long?, Long?> = when (filter) {
        SearchDateFilter.ANY -> null to null
        SearchDateFilter.TODAY -> startOfDay(now) to endOfDay(now)
        SearchDateFilter.WEEK -> startOfDay(now) to endOfDay(addDays(now, 7))
        SearchDateFilter.MONTH -> startOfDay(now) to endOfDay(addDays(now, 30))
        SearchDateFilter.OVERDUE -> null to now
    }

    private fun comparator(sort: SearchSort): Comparator<OrganizationItem> = when (sort) {
        SearchSort.DATE_DESC -> compareByDescending<OrganizationItem> { it.date }.thenBy { it.type.ordinal }
        SearchSort.DATE_ASC -> compareBy<OrganizationItem> { it.date == null }.thenBy { it.date }
        SearchSort.TITLE -> compareBy<OrganizationItem> { it.title.lowercase(Locale.ROOT) }
        SearchSort.TYPE -> compareBy<OrganizationItem> { it.type.ordinal }
            .thenByDescending { it.date }
        SearchSort.PRIORITY -> compareByDescending<OrganizationItem> { it.priority.rank }
            .thenByDescending { it.date }
    }
}

private fun FolderSummaryEntity.toDomain() = Folder(
    id = folder.id,
    name = folder.name,
    description = folder.description,
    icon = FolderIcon.entries.firstOrNull { it.name == folder.icon } ?: FolderIcon.FOLDER,
    color = folder.color,
    parentId = folder.parentId,
    itemCount = itemCount,
    createdAt = folder.createdAt,
    modifiedAt = folder.modifiedAt,
)

private fun CategorySummaryEntity.toDomain() = Category(
    id = category.id,
    name = category.name,
    color = category.color,
    itemCount = itemCount,
    createdAt = category.createdAt,
    modifiedAt = category.modifiedAt,
)

private fun TagSummaryEntity.toDomain() = Tag(
    id = label.id,
    name = label.name,
    color = label.color,
    itemCount = itemCount,
)

private fun SearchRowEntity.toDomain(tagsById: Map<Long, Tag>): OrganizationItem {
    val type = ContentType.entries.firstOrNull { it.name == type } ?: ContentType.NOTE
    val ids = tagIds.orEmpty().split(',').mapNotNull(String::toLongOrNull)
    return OrganizationItem(
        type = type,
        id = id,
        title = title,
        preview = snippet.orEmpty(),
        date = date,
        favorite = favorite,
        folderId = folderId,
        categoryId = categoryId,
        folderName = folderName,
        categoryName = categoryName,
        folderColor = folderColor,
        tags = ids.mapNotNull(tagsById::get).sortedBy { it.name },
        taskStatus = TaskStatus.entries.firstOrNull { it.name == status },
        priority = TaskPriority.entries.firstOrNull { it.rank == priority } ?: TaskPriority.UNSPECIFIED,
    )
}

private fun SavedViewEntity.toDomain(validTagIds: Set<Long>) = SavedView(
    id = id,
    name = name,
    filters = SearchFilters(
        query = query,
        categoryId = categoryId,
        folderId = folderId,
        includeSubfolders = includeSubfolders,
        tagIds = tagIds.split(',').mapNotNull(String::toLongOrNull).filter { it in validTagIds }.toSet(),
        types = types.split(',').mapNotNull { token ->
            ContentType.entries.firstOrNull { it.name == token }
        }.toSet().ifEmpty { ContentType.entries.toSet() },
        favoriteOnly = favoriteOnly,
        date = SearchDateFilter.entries.firstOrNull { it.name == dateFilter } ?: SearchDateFilter.ANY,
        state = SearchStateFilter.entries.firstOrNull { it.name == stateFilter } ?: SearchStateFilter.ANY,
        sort = SearchSort.entries.firstOrNull { it.name == sort } ?: SearchSort.DATE_DESC,
    ),
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)
