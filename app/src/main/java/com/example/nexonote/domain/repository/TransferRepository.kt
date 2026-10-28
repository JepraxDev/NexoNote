package com.example.nexonote.domain.repository

import com.example.nexonote.domain.model.BackupFrequency
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.ImportColumnSpec
import com.example.nexonote.domain.model.ImportResult
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.RestoreMode
import com.example.nexonote.domain.model.TableRow
import com.example.nexonote.domain.model.Task
import kotlinx.coroutines.flow.Flow

/** Read-only snapshot access used by the export screens, built on top of the regular repositories. */
interface ExportDataRepository {
    suspend fun notes(): List<Note>
    suspend fun tasks(): List<Task>
    suspend fun tables(): List<CustomTable>
    suspend fun columns(tableId: Long): List<CustomColumn>
    suspend fun rows(tableId: Long): List<TableRow>
    suspend fun folders(): List<Folder>
    suspend fun categories(): List<Category>
}

data class ImportRowValues(
    val sourceRow: Int,
    val values: List<String?>,
    /**
     * Flags, parallel to [values], marking which target columns are actually bound to a source
     * column. Blank values in mapped columns clear the stored cell; unmapped columns must stay
     * untouched so an UPDATE import never erases data the file does not mention.
     */
    val mapped: List<Boolean> = emptyList(),
)

/**
 * Writes imported rows in a single transaction. The service validates every row before calling
 * this method, so an invalid file never leaves a partially imported table behind.
 */
interface TableImportRepository {
    suspend fun columns(tableId: Long): List<CustomColumn>
    suspend fun existingPrimaryKeys(tableId: Long, primary: CustomColumn): Map<String, Long>
    suspend fun write(
        targetTableId: Long?,
        newTableName: String?,
        newColumns: List<ImportColumnSpec>,
        rows: List<ImportRowValues>,
        primaryIndex: Int,
        strategy: com.example.nexonote.domain.model.DuplicateStrategy,
    ): ImportResult

    /** Normalises a raw value for duplicate comparison using the same rules as storage. */
    fun duplicateKey(type: ColumnType, value: String?): String?
}

interface BackupRepository {
    val schemaVersion: Int
    suspend fun snapshot(): DatabaseSnapshot
    suspend fun counts(): Map<String, Int>
    suspend fun restore(snapshot: DatabaseSnapshot, mode: RestoreMode): Map<String, Int>
    suspend fun readSettings(): Map<String, String?>
    suspend fun applySettings(values: Map<String, String?>)
}

interface BackupSettingsRepository {
    val automaticEnabled: Flow<Boolean>
    val frequency: Flow<BackupFrequency>
    val destinationUri: Flow<String?>
    val lastAutomaticAt: Flow<Long?>
    suspend fun setAutomaticEnabled(enabled: Boolean)
    suspend fun setFrequency(frequency: BackupFrequency)
    suspend fun setDestinationUri(uri: String?)
    suspend fun setLastAutomaticAt(value: Long)
}

interface BackupHistoryStore {
    suspend fun entries(): List<BackupHistoryEntry>
    suspend fun add(entry: BackupHistoryEntry)
    suspend fun remove(id: String): Boolean
    suspend fun update(entry: BackupHistoryEntry)
    suspend fun exists(entry: BackupHistoryEntry): Boolean

    /** Absolute path for managed backups, original URI for external ones. */
    fun locationFor(entry: BackupHistoryEntry): String?
}

interface BackupScheduler {
    fun schedule(frequency: BackupFrequency)
    fun cancel()
}
