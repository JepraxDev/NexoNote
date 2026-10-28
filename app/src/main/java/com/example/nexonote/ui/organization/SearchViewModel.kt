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
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.model.SavedView
import com.example.nexonote.domain.model.SearchDateFilter
import com.example.nexonote.domain.model.SearchFilters
import com.example.nexonote.domain.model.SearchSort
import com.example.nexonote.domain.model.SearchStateFilter
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.repository.OrganizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filters: SearchFilters = SearchFilters(),
    val results: List<OrganizationItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val savedViews: List<SavedView> = emptyList(),
    val busy: Boolean = false,
    val operationError: String? = null,
) {
    val hasQuery: Boolean
        get() = filters.query.isNotBlank() || !filters.isDefault
}

private data class SearchOperation(val busy: Boolean = false, val error: String? = null)

private data class SearchMeta(
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val savedViews: List<SavedView> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: OrganizationRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(SearchFilters())
    private val operation = MutableStateFlow(SearchOperation())
    private val retry = MutableStateFlow(0)

    private val results = combine(retry, filters) { _, value -> value }
        .debounce(180)
        .flatMapLatest { value ->
            repository.search(value)
                .map { items -> items to null as String? }
                .catch { emit(emptyList<OrganizationItem>() to "No se pudo completar la búsqueda.") }
        }

    private val meta = combine(
        repository.folders,
        repository.categories,
        repository.tags,
        repository.savedViews,
    ) { folders, categories, tags, views -> SearchMeta(folders, categories, tags, views) }
        .catch { emit(SearchMeta()) }

    val uiState: StateFlow<SearchUiState> = combine(results, meta, filters, operation) {
            (items, error), metadata, current, status ->
        SearchUiState(
            loading = false,
            error = error,
            filters = current,
            results = items,
            folders = metadata.folders,
            categories = metadata.categories,
            tags = metadata.tags,
            savedViews = metadata.savedViews,
            busy = status.busy,
            operationError = status.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        filters.update { it.copy(query = value.take(120)) }
    }

    fun toggleType(type: ContentType) {
        filters.update { current ->
            val types = current.types.toMutableSet()
            if (type in types) types.remove(type) else types.add(type)
            current.copy(types = types.ifEmpty { ContentType.entries.toSet() })
        }
    }

    fun setCategory(id: Long?) {
        filters.update { it.copy(categoryId = id) }
    }

    fun setFolder(id: Long?, includeSubfolders: Boolean = true) {
        filters.update { it.copy(folderId = id, includeSubfolders = includeSubfolders) }
    }

    fun toggleTag(id: Long) {
        filters.update { current ->
            val tags = current.tagIds.toMutableSet()
            if (id in tags) tags.remove(id) else tags.add(id)
            current.copy(tagIds = tags)
        }
    }

    fun setFavoriteOnly(value: Boolean) {
        filters.update { it.copy(favoriteOnly = value) }
    }

    fun setDate(value: SearchDateFilter) {
        filters.update { it.copy(date = value) }
    }

    fun setState(value: SearchStateFilter) {
        filters.update { it.copy(state = value) }
    }

    fun setSort(value: SearchSort) {
        filters.update { it.copy(sort = value) }
    }

    fun clearFilters() {
        filters.value = SearchFilters()
    }

    fun applyView(view: SavedView) {
        filters.value = view.filters
    }

    fun toggleFavorite(item: OrganizationItem) {
        if (operation.value.busy) return
        operation.value = SearchOperation(busy = true)
        viewModelScope.launch {
            try {
                repository.setFavorite(item.type, item.id, !item.favorite)
                operation.value = SearchOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = SearchOperation(error = "No se pudo actualizar el favorito.")
            }
        }
    }

    fun saveView(name: String, onSaved: () -> Unit = {}) = perform(onSaved) {
        repository.saveSavedView(null, name, filters.value)
    }

    fun updateView(view: SavedView, name: String, onSaved: () -> Unit = {}) = perform(onSaved) {
        repository.saveSavedView(view.id, name, view.filters)
    }

    fun deleteView(id: Long) = perform({}) {
        repository.deleteSavedView(id)
    }

    fun saveSuggested(name: String, value: SearchFilters, onSaved: () -> Unit = {}) =
        perform(onSaved) { repository.saveSavedView(null, name, value) }

    fun retry() {
        retry.update { it + 1 }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    private fun perform(onDone: () -> Unit, action: suspend () -> Unit) {
        if (operation.value.busy) return
        operation.value = SearchOperation(busy = true)
        viewModelScope.launch {
            try {
                action()
                operation.value = SearchOperation()
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operation.value = SearchOperation(
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo completar la acción."
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(container.organizationRepository) }
        }
    }
}
