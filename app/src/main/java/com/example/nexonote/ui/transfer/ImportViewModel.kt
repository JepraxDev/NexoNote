package com.example.nexonote.ui.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.data.transfer.TableImportService
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.DuplicateStrategy
import com.example.nexonote.domain.model.ImportMapping
import com.example.nexonote.domain.model.ImportOptions
import com.example.nexonote.domain.model.ImportPreview
import com.example.nexonote.domain.model.ImportResult
import com.example.nexonote.domain.model.ParsedImport
import com.example.nexonote.domain.repository.TableImportRepository
import com.example.nexonote.domain.repository.TableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportStage { PICK, REVIEW, DONE }

data class ImportTableOption(val id: Long, val name: String)

data class ImportUiState(
    val tables: List<ImportTableOption> = emptyList(),
    val tablesLoading: Boolean = true,
    val stage: ImportStage = ImportStage.PICK,
    val fileName: String? = null,
    val parsed: ParsedImport? = null,
    val preview: ImportPreview? = null,
    val targetTableId: Long? = null,
    val newTableName: String = "",
    val mappings: List<ImportMapping> = emptyList(),
    val options: ImportOptions = ImportOptions(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val result: ImportResult? = null,
) {
    val sourceHeaders: List<String> get() = parsed?.headers.orEmpty()
    val totalRows: Int get() = preview?.totalRows ?: 0
    val canImport: Boolean
        get() = parsed != null && mappings.any { it.sourceIndex != null } && !busy
}

class ImportViewModel(
    private val service: TableImportService,
    private val repository: TableImportRepository,
    tables: TableRepository,
    private val files: FileGateway,
) : ViewModel() {
    private val state = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = state.asStateFlow()

    private var sourceUri: Uri? = null
    private var sourceName: String? = null

    init {
        viewModelScope.launch {
            try {
                tables.tables.collect { items ->
                    state.update {
                        it.copy(
                            tables = items.map { table -> ImportTableOption(table.id, table.displayName) },
                            tablesLoading = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update {
                    it.copy(
                        tablesLoading = false,
                        error = "No pudimos cargar tus tablas. Comprueba el almacenamiento.",
                    )
                }
            }
        }
    }

    fun openFile(uri: Uri) {
        val displayName = files.displayName(uri)
        sourceUri = uri
        sourceName = displayName
        val derived = displayName?.substringBeforeLast('.')?.take(80).orEmpty()
        state.update {
            it.copy(
                fileName = displayName,
                newTableName = if (it.targetTableId == null && derived.isNotBlank()) derived else it.newTableName,
                error = null,
                result = null,
            )
        }
        parse(sheetIndex = 0, header = state.value.options.firstRowIsHeader)
    }

    fun setFirstRowIsHeader(value: Boolean) {
        if (sourceUri == null) {
            state.update { it.copy(options = it.options.copy(firstRowIsHeader = value)) }
            return
        }
        parse(state.value.parsed?.sheetIndex ?: 0, value)
    }

    fun selectSheet(index: Int) {
        parse(index, state.value.options.firstRowIsHeader)
    }

    fun selectTarget(tableId: Long?) {
        state.update { it.copy(targetTableId = tableId, error = null) }
        val parsed = state.value.parsed ?: return
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                val mappings = buildMappings(parsed, tableId)
                state.update { it.copy(busy = false, mappings = mappings) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo preparar la tabla de destino.",
                    )
                }
            }
        }
    }

    fun setNewTableName(value: String) {
        state.update { it.copy(newTableName = value.take(80)) }
    }

    fun setNewColumnIncluded(index: Int, included: Boolean) {
        state.update { current ->
            current.copy(
                mappings = current.mappings.mapIndexed { position, mapping ->
                    if (position != index) mapping
                    else mapping.copy(sourceIndex = if (included) mapping.sourceIndex ?: position else null)
                }
            )
        }
    }

    fun setNewColumnName(index: Int, name: String) {
        state.update { current ->
            current.copy(
                mappings = current.mappings.mapIndexed { position, mapping ->
                    if (position == index) mapping.copy(targetName = name.take(80)) else mapping
                }
            )
        }
    }

    fun setNewColumnType(index: Int, type: ColumnType) {
        if (type !in CREATABLE_TYPES) return
        state.update { current ->
            current.copy(
                mappings = current.mappings.mapIndexed { position, mapping ->
                    if (position == index) mapping.copy(targetType = type) else mapping
                }
            )
        }
    }

    fun setMappingSource(targetColumnId: Long, sourceIndex: Int?) {
        state.update { current ->
            current.copy(
                mappings = current.mappings.map { mapping ->
                    if (mapping.targetColumnId == targetColumnId) mapping.copy(sourceIndex = sourceIndex)
                    else mapping
                }
            )
        }
    }

    fun setDuplicateStrategy(strategy: DuplicateStrategy) {
        state.update { it.copy(options = it.options.copy(duplicateStrategy = strategy)) }
    }

    fun setSkipInvalidRows(value: Boolean) {
        state.update { it.copy(options = it.options.copy(skipInvalidRows = value)) }
    }

    fun runImport() {
        val current = state.value
        val parsed = current.parsed ?: return
        if (!current.canImport) {
            state.update {
                it.copy(error = "Asigna al menos una columna de origen antes de importar.")
            }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null) }
            try {
                val result = service.import(
                    parsed = parsed,
                    mappings = current.mappings,
                    options = current.options,
                    targetTableId = current.targetTableId,
                    newTableName = current.newTableName.ifBlank { null },
                )
                state.update {
                    it.copy(
                        busy = false,
                        stage = ImportStage.DONE,
                        result = result,
                        message = when {
                            result.imported + result.updated > 0 ->
                                "Importación completada: ${result.imported} nuevas, ${result.updated} actualizadas."
                            result.errors.isNotEmpty() -> null
                            else -> "No se importó ninguna fila."
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo completar la importación.",
                    )
                }
            }
        }
    }

    fun reviewAgain() {
        state.update { it.copy(stage = ImportStage.REVIEW, result = null, message = null) }
    }

    fun backToFile() {
        state.update {
            it.copy(
                stage = ImportStage.PICK,
                parsed = null,
                preview = null,
                mappings = emptyList(),
                result = null,
                error = null,
            )
        }
        sourceUri = null
        sourceName = null
    }

    fun consumeMessage() {
        state.update { it.copy(message = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    private fun parse(sheetIndex: Int, header: Boolean) {
        val uri = sourceUri ?: return
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null, result = null) }
            try {
                val parsed = service.parse(
                    uri = uri,
                    fileName = sourceName,
                    sheetIndex = sheetIndex,
                    firstRowIsHeader = header,
                )
                val options = state.value.options.copy(firstRowIsHeader = header)
                val mappings = buildMappings(parsed, state.value.targetTableId)
                state.update {
                    it.copy(
                        busy = false,
                        parsed = parsed,
                        preview = service.preview(parsed),
                        mappings = mappings,
                        options = options,
                        stage = ImportStage.REVIEW,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        stage = ImportStage.PICK,
                        parsed = null,
                        preview = null,
                        mappings = emptyList(),
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo leer el archivo. Comprueba que sea un CSV o XLSX válido.",
                    )
                }
            }
        }
    }

    private suspend fun buildMappings(parsed: ParsedImport, tableId: Long?): List<ImportMapping> {
        if (tableId == null) return service.mappingForNewTable(parsed)
        val columns = repository.columns(tableId)
        return service.autoMapping(parsed, columns)
    }

    companion object {
        /** Selection-style columns need existing options, so they cannot be created by import. */
        val CREATABLE_TYPES: List<ColumnType> = ColumnType.entries.filter {
            it != ColumnType.SELECT && it != ColumnType.MULTISELECT && it != ColumnType.STATUS
        }

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImportViewModel(
                    service = container.tableImportService,
                    repository = container.tableImportRepository,
                    tables = container.tableRepository,
                    files = container.fileGateway,
                )
            }
        }
    }
}
