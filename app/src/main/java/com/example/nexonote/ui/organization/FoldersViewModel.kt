package com.example.nexonote.ui.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.FolderIcon
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.repository.OrganizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrganizationTab(val label: String) { FOLDERS("Carpetas"), CATEGORIES("Categorías"), TAGS("Etiquetas") }

data class FoldersUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val items: List<OrganizationItem> = emptyList(),
    val tab: OrganizationTab = OrganizationTab.FOLDERS,
    val expanded: Set<Long> = emptySet(),
    val busy: Boolean = false,
    val operationError: String? = null,
) {
    /** Direct contents of each folder, grouped for the expanded folder rows. */
    val itemsByFolder: Map<Long, List<OrganizationItem>>
        get() = items.filter { it.folderId != null }.groupBy { it.folderId!! }
}

private data class OrganizationContent(
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val items: List<OrganizationItem> = emptyList(),
    val error: String? = null,
)

private data class OrganizationOperation(val busy: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel(
    private val repository: OrganizationRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(OrganizationTab.FOLDERS)
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())
    private val operation = MutableStateFlow(OrganizationOperation())
    private val retry = MutableStateFlow(0)

    private val content = retry.flatMapLatest {
        combine(
            repository.folders,
            repository.categories,
            repository.tags,
            repository.search(SearchFilters()),
        ) { folders, categories, tags, items ->
            OrganizationContent(folders, categories, tags, items)
        }.catch {
            emit(
                OrganizationContent(
                    error = "No pudimos cargar la organización. Comprueba el almacenamiento.",
                )
            )
        }
    }

    val uiState: StateFlow<FoldersUiState> =
        combine(content, tab, expanded, operation) { data, currentTab, open, status ->
            FoldersUiState(
                loading = false,
                error = data.error,
                folders = data.folders,
                categories = data.categories,
                tags = data.tags,
                items = data.items,
                tab = currentTab,
                expanded = open,
                busy = status.busy,
                operationError = status.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun selectTab(value: OrganizationTab) {
        tab.value = value
    }

    /** Re-subscribes after a database error so the retry button is not a no-op. */
    fun retry() {
        retry.update { it + 1 }
    }

    fun toggleExpanded(id: Long) {
        val allIds = uiState.value.folders.map { it.id }.toSet()
        expanded.update { current ->
            val base = if (current.isEmpty()) allIds else current
            if (id in base) base - id else base + id
        }
    }

    fun saveFolder(
        id: Long? = null,
        name: String,
        description: String = "",
        icon: FolderIcon = FolderIcon.FOLDER,
        color: String,
        parentId: Long? = null,
        onDone: () -> Unit = {},
    ) = perform(onDone) {
        repository.saveFolder(id, name, description, icon, color, parentId)
    }

    fun deleteFolder(id: Long, onDone: () -> Unit = {}) = perform(onDone) {
        repository.deleteFolder(id)
    }

    fun saveCategory(id: Long? = null, name: String, color: String, onDone: () -> Unit = {}) =
        perform(onDone) { repository.saveCategory(id, name, color) }

    fun deleteCategory(id: Long, onDone: () -> Unit = {}) = perform(onDone) {
        repository.deleteCategory(id)
    }

    fun saveTag(id: Long? = null, name: String, color: String, onDone: () -> Unit = {}) =
        perform(onDone) { repository.saveTag(id, name, color) }

    fun deleteTag(id: Long, onDone: () -> Unit = {}) = perform(onDone) {
        repository.deleteTag(id)
    }

    fun moveItem(
        type: ContentType,
        id: Long,
        folderId: Long?,
        onDone: () -> Unit = {},
    ) = perform(onDone) {
        repository.moveToFolder(type, id, folderId)
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    private fun perform(onDone: () -> Unit, action: suspend () -> Unit) {
        if (operation.value.busy) return
        operation.value = OrganizationOperation(busy = true)
        viewModelScope.launch {
            try {
                action()
                operation.value = OrganizationOperation()
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operation.value = OrganizationOperation(
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo completar la acción. Inténtalo de nuevo."
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FoldersViewModel(container.organizationRepository) }
        }
    }
}
