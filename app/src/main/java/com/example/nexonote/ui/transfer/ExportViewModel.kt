package com.example.nexonote.ui.transfer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.data.transfer.ExportService
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.ExportOutcome
import com.example.nexonote.domain.model.ExportSection
import com.example.nexonote.domain.model.NoteExportFormat
import com.example.nexonote.domain.model.SpreadsheetFormat
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportItem(val id: Long, val title: String, val detail: String)

/** A cache file ready to be handed to the Android Sharesheet through the FileProvider. */
data class ShareRequest(val file: File, val mimeType: String, val title: String)

data class ExportUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val section: ExportSection = ExportSection.NOTES,
    val notes: List<ExportItem> = emptyList(),
    val tasks: List<ExportItem> = emptyList(),
    val tables: List<ExportItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val allSelected: Boolean = true,
    val noteFormat: NoteExportFormat = NoteExportFormat.PDF,
    val spreadsheetFormat: SpreadsheetFormat = SpreadsheetFormat.XLSX,
    val busy: Boolean = false,
    val message: String? = null,
    val actionError: String? = null,
    val shareRequest: ShareRequest? = null,
) {
    val items: List<ExportItem>
        get() = when (section) {
            ExportSection.NOTES -> notes
            ExportSection.TASKS -> tasks
            ExportSection.TABLES -> tables
        }

    val selectedCount: Int get() = selected.size
    val canExport: Boolean get() = selected.isNotEmpty() && !busy

    val mimeType: String
        get() = when (section) {
            ExportSection.NOTES -> noteFormat.mimeType
            ExportSection.TASKS, ExportSection.TABLES -> spreadsheetFormat.mimeType
        }

    val fileExtension: String
        get() = when (section) {
            ExportSection.NOTES -> noteFormat.extension
            ExportSection.TASKS, ExportSection.TABLES -> spreadsheetFormat.extension
        }
}

private data class ExportContent(
    val loading: Boolean = false,
    val error: String? = null,
    val section: ExportSection = ExportSection.NOTES,
    val notes: List<ExportItem> = emptyList(),
    val tasks: List<ExportItem> = emptyList(),
    val tables: List<ExportItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val allSelected: Boolean = true,
)

private data class ExportFormats(
    val noteFormat: NoteExportFormat = NoteExportFormat.PDF,
    val sheetFormat: SpreadsheetFormat = SpreadsheetFormat.XLSX,
)

private data class ExportOperation(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val share: ShareRequest? = null,
)

class ExportViewModel(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val tables: TableRepository,
    private val service: ExportService,
    private val files: FileGateway,
) : ViewModel() {
    private val section = MutableStateFlow(ExportSection.NOTES)
    /** null means "all items of the current section"; an explicit set records user edits. */
    private val selection = MutableStateFlow<Set<Long>?>(null)
    private val formats = MutableStateFlow(ExportFormats())
    private val operation = MutableStateFlow(ExportOperation())

    private val content = combine(
        notes.notes,
        tasks.tasks,
        tables.tables,
        section,
        selection,
    ) { noteItems, taskItems, tableItems, currentSection, chosen ->
        val noteRows = noteItems.map { note ->
            ExportItem(
                id = note.id,
                title = note.title.ifBlank { "Nota sin título" },
                detail = note.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120),
            )
        }
        val taskRows = taskItems.map { task ->
            ExportItem(
                id = task.id,
                title = task.title.ifBlank { "Tarea sin título" },
                detail = buildString {
                    append(if (task.status.name == "COMPLETED") "Completada" else "Pendiente")
                    task.dueAt?.let { append(" · vence ").append(TransferFormats.formatDate(it)) }
                    if (task.subtasks.isNotEmpty()) append(" · ").append(task.subtasks.size).append(" subtareas")
                },
            )
        }
        val tableRows = tableItems.map { table ->
            ExportItem(
                id = table.id,
                title = table.displayName,
                detail = listOfNotNull(
                    table.columnCount.takeIf { it > 0 }?.let { "$it columnas" },
                    table.rowCount.takeIf { it > 0 }?.let { "$it filas" },
                ).joinToString(" · ").ifBlank { table.description.take(120) },
            )
        }
        val allIds = when (currentSection) {
            ExportSection.NOTES -> noteRows
            ExportSection.TASKS -> taskRows
            ExportSection.TABLES -> tableRows
        }.map { it.id }.toSet()
        val resolved = (chosen ?: allIds).intersect(allIds)
        ExportContent(
            loading = false,
            section = currentSection,
            notes = noteRows,
            tasks = taskRows,
            tables = tableRows,
            selected = resolved,
            allSelected = chosen == null || resolved.size == allIds.size,
        )
    }
        .onStart { emit(ExportContent(loading = true, section = section.value)) }
        .catch {
            emit(
                ExportContent(
                    loading = false,
                    section = section.value,
                    error = "No pudimos cargar el contenido para exportar. Comprueba el almacenamiento.",
                )
            )
        }

    val uiState: StateFlow<ExportUiState> = combine(content, formats, operation) { data, format, status ->
        ExportUiState(
            loading = data.loading,
            error = data.error,
            section = data.section,
            notes = data.notes,
            tasks = data.tasks,
            tables = data.tables,
            selected = data.selected,
            allSelected = data.allSelected,
            noteFormat = format.noteFormat,
            spreadsheetFormat = format.sheetFormat,
            busy = status.busy,
            message = status.message,
            actionError = status.error,
            shareRequest = status.share,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    fun setSection(value: ExportSection) {
        if (section.value == value) return
        section.value = value
        selection.value = null
    }

    fun toggle(id: Long) {
        val current = uiState.value.selected
        selection.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        selection.value = null
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun setNoteFormat(format: NoteExportFormat) {
        formats.update { it.copy(noteFormat = format) }
    }

    fun setSpreadsheetFormat(format: SpreadsheetFormat) {
        formats.update { it.copy(sheetFormat = format) }
    }

    fun suggestedFileName(): String = fileName(uiState.value)

    fun currentMimeType(): String = uiState.value.mimeType

    fun saveTo(uri: Uri) {
        val snapshot = uiState.value
        if (snapshot.selected.isEmpty()) {
            operation.value = ExportOperation(error = "Selecciona al menos un elemento para exportar.")
            return
        }
        viewModelScope.launch {
            operation.value = ExportOperation(busy = true)
            try {
                val outcome = files.write(uri) { output -> export(snapshot, output) }
                operation.value = ExportOperation(
                    message = "Archivo guardado: ${outcome.fileName} (${TransferFormats.formatSize(outcome.bytes)})"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operation.value = ExportOperation(
                    error = error.message?.takeIf { it.isNotBlank() } ?: "No se pudo exportar el archivo."
                )
            }
        }
    }

    fun share() {
        val snapshot = uiState.value
        if (snapshot.selected.isEmpty()) {
            operation.value = ExportOperation(error = "Selecciona al menos un elemento para exportar.")
            return
        }
        viewModelScope.launch {
            operation.value = ExportOperation(busy = true)
            var file: File? = null
            try {
                val target = files.createExportFile(fileName(snapshot))
                file = target
                val outcome = target.outputStream().buffered().use { output -> export(snapshot, output) }
                operation.value = ExportOperation(
                    share = ShareRequest(target, outcome.mimeType, outcome.fileName)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                file?.delete()
                operation.value = ExportOperation(
                    error = error.message?.takeIf { it.isNotBlank() } ?: "No se pudo preparar el archivo para compartir."
                )
            }
        }
    }

    fun onShareHandled() {
        operation.update { it.copy(share = null) }
    }

    fun shareIntent(request: ShareRequest): Intent =
        files.shareIntent(request.file, request.mimeType, "Compartir ${request.title}")

    fun consumeMessage() {
        operation.update { it.copy(message = null) }
    }

    fun dismissError() {
        operation.update { it.copy(error = null) }
    }

    private suspend fun export(state: ExportUiState, out: OutputStream): ExportOutcome {
        val ids = state.selected
        return when (state.section) {
            ExportSection.NOTES -> service.exportNotes(ids, state.noteFormat, out)
            ExportSection.TASKS -> service.exportTasks(ids, state.spreadsheetFormat, out)
            ExportSection.TABLES -> service.exportTables(ids, state.spreadsheetFormat, out)
        }
    }

    private fun fileName(state: ExportUiState): String {
        val single = when (state.section) {
            ExportSection.NOTES -> state.notes.singleOrNull { it.id in state.selected }?.title
            ExportSection.TABLES -> state.tables.singleOrNull { it.id in state.selected }?.title
            ExportSection.TASKS -> null
        }
        return service.suggestedFileName(state.section, state.fileExtension, single)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExportViewModel(
                    notes = container.noteRepository,
                    tasks = container.taskRepository,
                    tables = container.tableRepository,
                    service = container.exportService,
                    files = container.fileGateway,
                )
            }
        }
    }
}
