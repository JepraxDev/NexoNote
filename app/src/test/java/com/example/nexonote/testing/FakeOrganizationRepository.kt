package com.example.nexonote.testing

import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SavedView
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.repository.OrganizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Minimal in-memory organization repository for editor and UI state tests. */
class FakeOrganizationRepository : OrganizationRepository {
    override val folders = MutableStateFlow<List<Folder>>(emptyList())
    override val categories = MutableStateFlow<List<Category>>(emptyList())
    override val tags = MutableStateFlow<List<Tag>>(emptyList())
    override val favorites = MutableStateFlow<List<OrganizationItem>>(emptyList())
    override val savedViews = MutableStateFlow<List<SavedView>>(emptyList())
    override val activityTimes = MutableStateFlow<List<Long>>(emptyList())
    override val rowCreationTimes = MutableStateFlow<List<Long>>(emptyList())

    override fun search(filters: SearchFilters): Flow<List<OrganizationItem>> = flowOf(emptyList())

    override suspend fun saveFolder(
        id: Long?,
        name: String,
        description: String,
        icon: FolderIcon,
        color: String,
        parentId: Long?,
    ): Long = id ?: 0L

    override suspend fun deleteFolder(id: Long) = Unit

    override suspend fun saveCategory(id: Long?, name: String, color: String): Long = id ?: 0L

    override suspend fun deleteCategory(id: Long) = Unit

    override suspend fun saveTag(id: Long?, name: String, color: String): Long = id ?: 0L

    override suspend fun deleteTag(id: Long) = Unit

    override suspend fun setFavorite(type: ContentType, id: Long, favorite: Boolean) = Unit

    override suspend fun moveToFolder(type: ContentType, id: Long, folderId: Long?) = Unit

    override suspend fun assignCategory(type: ContentType, id: Long, categoryId: Long?): Boolean = true

    override suspend fun assignTag(type: ContentType, id: Long, tagId: Long, assigned: Boolean) = Unit

    override suspend fun saveSavedView(id: Long?, name: String, filters: SearchFilters): Long = id ?: 0L

    override suspend fun deleteSavedView(id: Long) = Unit
}
