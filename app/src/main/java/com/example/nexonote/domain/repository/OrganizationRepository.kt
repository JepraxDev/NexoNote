package com.example.nexonote.domain.repository

import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SavedView
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface OrganizationRepository {
    val folders: Flow<List<Folder>>
    val categories: Flow<List<Category>>
    val tags: Flow<List<Tag>>
    val favorites: Flow<List<OrganizationItem>>
    val savedViews: Flow<List<SavedView>>
    val activityTimes: Flow<List<Long>>
    val rowCreationTimes: Flow<List<Long>>

    fun search(filters: SearchFilters): Flow<List<OrganizationItem>>

    suspend fun saveFolder(
        id: Long? = null,
        name: String,
        description: String = "",
        icon: FolderIcon = FolderIcon.FOLDER,
        color: String,
        parentId: Long? = null,
    ): Long

    /** Moves children and contents to the parent folder, then deletes the selected folder. */
    suspend fun deleteFolder(id: Long)

    suspend fun saveCategory(id: Long? = null, name: String, color: String): Long
    suspend fun deleteCategory(id: Long)

    suspend fun saveTag(id: Long? = null, name: String, color: String): Long
    suspend fun deleteTag(id: Long)

    suspend fun setFavorite(type: ContentType, id: Long, favorite: Boolean)
    suspend fun moveToFolder(type: ContentType, id: Long, folderId: Long?)
    suspend fun assignCategory(type: ContentType, id: Long, categoryId: Long?): Boolean
    suspend fun assignTag(type: ContentType, id: Long, tagId: Long, assigned: Boolean)

    suspend fun saveSavedView(id: Long? = null, name: String, filters: SearchFilters): Long
    suspend fun deleteSavedView(id: Long)
}
