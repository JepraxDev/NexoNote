package com.example.nexonote.ui.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.OrganizationItem
import com.example.nexonote.domain.repository.OrganizationRepository
import com.example.nexonote.ui.tables.colorFromHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<OrganizationItem> = emptyList(),
    val busy: Boolean = false,
    val operationError: String? = null,
)

private data class FavoritesOperation(val busy: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(
    private val repository: OrganizationRepository,
) : ViewModel() {
    private val operation = MutableStateFlow(FavoritesOperation())
    private val retry = MutableStateFlow(0)
    private val content = retry.flatMapLatest {
        repository.favorites
            .map<List<OrganizationItem>, Pair<List<OrganizationItem>, String?>> { it to null }
            .catch {
                emit(
                    emptyList<OrganizationItem>() to
                        "No pudimos cargar los favoritos. Comprueba el almacenamiento."
                )
            }
    }

    val uiState: StateFlow<FavoritesUiState> = combine(content, operation) { (items, error), status ->
        FavoritesUiState(
            loading = false,
            error = error,
            items = items,
            busy = status.busy,
            operationError = status.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    /** Re-subscribes after a database error so the retry button is not a no-op. */
    fun retry() {
        retry.update { it + 1 }
    }

    fun remove(item: OrganizationItem) {
        if (operation.value.busy) return
        operation.value = FavoritesOperation(busy = true)
        viewModelScope.launch {
            try {
                repository.setFavorite(item.type, item.id, false)
                operation.value = FavoritesOperation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                operation.value = FavoritesOperation(error = "No se pudo quitar de favoritos.")
            }
        }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FavoritesViewModel(container.organizationRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(
    model: FavoritesViewModel,
    onBack: () -> Unit,
    onOpen: (OrganizationItem) -> Unit,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbar.showSnackbar(it)
            model.dismissError()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> OrganizationLoading()
                state.error != null -> OrganizationError(state.error.orEmpty(), model::retry)
                state.items.isEmpty() -> OrganizationEmpty(
                    title = "Nada en favoritos todavía",
                    message = "Marca notas, tareas, tablas o eventos con la estrella para verlos aquí.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("favorites_list"),
                ) {
                    ContentType.entries.forEach { type ->
                        val group = state.items.filter { it.type == type }
                        if (group.isNotEmpty()) {
                            item(key = "header_${type.name}") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        contentTypeIcon(type),
                                        contentDescription = null,
                                        tint = contentTypeColor(type),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${type.label} · ${group.size}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                            items(group, key = { it.key }) { item ->
                                FavoriteRow(
                                    item = item,
                                    onOpen = { onOpen(item) },
                                    onRemove = { model.remove(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteRow(
    item: OrganizationItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                contentTypeIcon(item.type),
                contentDescription = item.type.singular,
                tint = contentTypeColor(item.type),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(item)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = "Quitar ${item.displayTitle} de favoritos",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
fun MetaLine(item: OrganizationItem) {
    val parts = buildList {
        item.folderName?.let { add(it) }
        item.categoryName?.let { add(it) }
        item.tags.take(3).forEach { add("#${it.name}") }
    }
    if (parts.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            parts.forEach { part ->
                Text(
                    part,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (part.startsWith("#")) colorFromHex(item.tags.firstOrNull { part == "#${it.name}" }?.color ?: "#6750A4")
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else if (item.preview.isNotBlank()) {
        Text(
            item.preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
