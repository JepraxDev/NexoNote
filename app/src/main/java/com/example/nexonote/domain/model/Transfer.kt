package com.example.nexonote.domain.model

const val BACKUP_FORMAT = "nexonote-backup"
const val BACKUP_FORMAT_VERSION = 1

enum class NoteExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    TXT("Texto plano", "txt", "text/plain"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    PDF("PDF", "pdf", "application/pdf"),
}

enum class SpreadsheetFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    CSV("CSV", "csv", "text/csv"),
    XLSX("Excel (XLSX)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

enum class DuplicateStrategy(val label: String, val description: String) {
    SKIP("Omitir duplicados", "Las filas que ya existen no se modifican."),
    UPDATE("Actualizar existentes", "Las filas que ya existen se actualizan con los valores del archivo."),
    ALLOW("Importar de todos modos", "Se añaden aunque coincidan con filas existentes."),
}

enum class ExportSection(val label: String) {
    NOTES("Notas"),
    TASKS("Tareas"),
    TABLES("Tablas"),
}

data class ExportOutcome(
    val fileName: String,
    val mimeType: String,
    val bytes: Long,
)

data class ImportPreview(
    val fileName: String,
    val sheetNames: List<String>,
    val headers: List<String>,
    val detectedTypes: List<ColumnType>,
    val sampleRows: List<List<String>>,
    val totalRows: Int,
)

/** Raw, already parsed spreadsheet content kept by the import screen between preview and import. */
data class ParsedImport(
    val fileName: String,
    val sheetNames: List<String>,
    val sheetIndex: Int,
    val headers: List<String>,
    val rows: List<List<String>>,
    val firstRowIsHeader: Boolean = true,
)

data class ImportColumnSpec(
    val name: String,
    val type: ColumnType,
    val required: Boolean = false,
)

data class TableExportData(
    val table: CustomTable,
    val columns: List<CustomColumn>,
    val rows: List<TableRow>,
)

/** Maps a destination column (by name or existing id) to a source column index. */
data class ImportMapping(
    val targetColumnId: Long?,
    val targetName: String,
    val targetType: ColumnType,
    val sourceIndex: Int?,
)

data class ImportRowError(val rowNumber: Int, val message: String)

data class ImportOptions(
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP,
    val skipInvalidRows: Boolean = true,
    val firstRowIsHeader: Boolean = true,
)

data class ImportResult(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val errors: List<ImportRowError> = emptyList(),
    val createdTableId: Long? = null,
) {
    val totalProcessed: Int get() = imported + updated + skipped
}

data class BackupManifest(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val encrypted: Boolean,
    val checksumSha256: String,
    val payloadBytes: Long,
    val counts: Map<String, Int> = emptyMap(),
    val attachments: Int = 0,
    val kdf: String? = null,
    val iterations: Int? = null,
    val salt: String? = null,
    val iv: String? = null,
)

enum class BackupStatus(val label: String) {
    OK("Completada"),
    FAILED("Con error"),
}

enum class BackupLocation(val label: String) {
    MANAGED("Copia gestionada por NexoNote"),
    EXTERNAL("Destino elegido con el selector de documentos"),
}

enum class BackupFrequency(val label: String, val days: Long?) {
    MANUAL("Solo manual", null),
    DAILY("Diaria", 1),
    WEEKLY("Semanal", 7),
}

data class BackupHistoryEntry(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val status: BackupStatus = BackupStatus.OK,
    val location: BackupLocation = BackupLocation.MANAGED,
    val managedPath: String? = null,
    val uri: String? = null,
    val message: String? = null,
)

data class RestorePreview(
    val fileName: String,
    val manifest: BackupManifest,
    val counts: Map<String, Int>,
    val attachmentCount: Int,
    val compatible: Boolean,
    val problems: List<String> = emptyList(),
)

data class RestoreResult(
    val restoredRows: Map<String, Int>,
    val attachmentsRestored: Int,
    val mode: RestoreMode,
)

enum class RestoreMode(val label: String) {
    REPLACE("Reemplazar todos los datos"),
    MERGE("Combinar solo si no hay conflictos"),
}
