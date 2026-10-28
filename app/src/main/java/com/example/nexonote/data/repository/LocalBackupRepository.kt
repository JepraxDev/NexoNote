package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.RestoreMode
import com.example.nexonote.domain.model.TableSnapshot
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.BackupRepository
import com.example.nexonote.domain.repository.SettingsRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Generic, allow-listed SQL snapshot/restore. It preserves primary keys and relations, never
 * touches sqlite internal tables and rejects unknown tables, columns or values before deleting
 * anything. Foreign keys are deferred so a consistent snapshot can be restored atomically.
 */
class LocalBackupRepository(
    private val database: NexoDatabase,
    private val settings: SettingsRepository,
) : BackupRepository {
    override val schemaVersion: Int
        get() = database.openHelper.readableDatabase.version

    override suspend fun snapshot(): DatabaseSnapshot = withContext(Dispatchers.IO) {
        // One read transaction keeps every table from the same committed state, so a restore can
        // never mix rows from before and after a concurrent write.
        database.withTransaction {
            val version = schemaVersion
            val tables = TABLES.associateWith { table ->
                database.openHelper.writableDatabase.query("SELECT * FROM \"$table\"").use { cursor ->
                    val columns = cursor.columnNames.toList()
                    val rows = ArrayList<Map<String, String?>>(cursor.count.coerceAtLeast(0))
                    while (cursor.moveToNext()) {
                        rows += columns.associateWith { column ->
                            val index = cursor.getColumnIndexOrThrow(column)
                            if (cursor.isNull(index)) null else cursor.getString(index)
                        }
                    }
                    TableSnapshot(columns, rows)
                }
            }
            DatabaseSnapshot(version, tables)
        }
    }

    override suspend fun counts(): Map<String, Int> = withContext(Dispatchers.IO) {
        TABLES.associateWith { table ->
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM \"$table\"").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    override suspend fun restore(snapshot: DatabaseSnapshot, mode: RestoreMode): Map<String, Int> =
        withContext(Dispatchers.IO) {
            validate(snapshot)
            if (mode == RestoreMode.MERGE) ensureMergeIsSafe(snapshot)
            database.withTransaction {
                database.openHelper.writableDatabase.query("PRAGMA defer_foreign_keys = ON").use { /* deferred until COMMIT */ }
                if (mode == RestoreMode.REPLACE) {
                    TABLES.asReversed().forEach { table -> database.openHelper.writableDatabase.execSQL("DELETE FROM \"$table\"") }
                }
                TABLES.forEach { table ->
                    val data = snapshot.tables[table] ?: return@forEach
                    insert(table, data)
                }
            }
            snapshot.counts
        }

    override suspend fun readSettings(): Map<String, String?> = mapOf(
        "displayName" to settings.displayName.first(),
        "themeMode" to settings.themeMode.first().name,
    )

    override suspend fun applySettings(values: Map<String, String?>) {
        values["displayName"]?.let { name ->
            runCatching { settings.setDisplayName(name.take(40)) }
        }
        values["themeMode"]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }?.let { settings.setThemeMode(it) }
        }
    }

    private fun validate(snapshot: DatabaseSnapshot) {
        require(snapshot.schemaVersion == schemaVersion) {
            "La copia pertenece a otra versión del esquema (${snapshot.schemaVersion}); " +
                "esta aplicación usa la versión $schemaVersion."
        }
        val unknown = snapshot.tables.keys - TABLES.toSet()
        require(unknown.isEmpty()) {
            "La copia contiene tablas desconocidas y no se puede restaurar de forma segura."
        }
        val missing = TABLES.toSet() - snapshot.tables.keys
        require(missing.isEmpty()) { "La copia está incompleta: faltan tablas de datos." }
        var totalRows = 0
        snapshot.tables.forEach { (table, data) ->
            val actual = tableColumns(table)
            require(actual.isNotEmpty()) { "La tabla $table no existe en esta versión." }
            require(data.columns.toSet() == actual) {
                "La copia no coincide con el esquema de la tabla $table."
            }
            data.rows.forEach { row ->
                totalRows++
                require(totalRows <= MAX_ROWS) { "La copia contiene demasiadas filas para restaurarla." }
                require(row.keys.all { it in actual }) {
                    "La copia contiene columnas no válidas en la tabla $table."
                }
                require(row.values.all { it == null || it.length <= MAX_VALUE_CHARS }) {
                    "La copia contiene un valor demasiado largo."
                }
            }
        }
    }

    private fun ensureMergeIsSafe(snapshot: DatabaseSnapshot) {
        val idConflicts = mutableListOf<String>()
        snapshot.tables.forEach { (table, data) ->
            if ("id" !in data.columns) return@forEach
            val incoming = data.rows.mapNotNull { it["id"]?.toLongOrNull() }.toSet()
            if (incoming.isEmpty()) return@forEach
            val existing = existingIds(table)
            if (existing.any { it in incoming }) idConflicts += table
        }
        require(idConflicts.isEmpty()) {
            "La combinación no es posible: hay identificadores que ya existen en ${idConflicts.joinToString(", ")}. " +
                "Usa «Reemplazar todos los datos» o restaura en una instalación vacía."
        }

        val nameConflicts = mutableListOf<String>()
        checkNameConflict(snapshot, "labels", "name", nameConflicts)
        checkNameConflict(snapshot, "categories", "name", nameConflicts)
        checkNameConflict(snapshot, "saved_views", "name", nameConflicts)
        require(nameConflicts.isEmpty()) {
            "La combinación no es posible: ya existe un elemento con el mismo nombre en " +
                nameConflicts.joinToString(", ") + "."
        }

        snapshot.tables["tasks"]?.let { tasks ->
            if ("seriesId" in tasks.columns && "occurrenceAt" in tasks.columns) {
                val existing = database.openHelper.writableDatabase.query(
                    "SELECT seriesId, occurrenceAt FROM tasks WHERE seriesId IS NOT NULL AND occurrenceAt IS NOT NULL"
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
                    }
                }
                val incoming = tasks.rows.mapNotNull { row ->
                    val series = row["seriesId"]?.toLongOrNull() ?: return@mapNotNull null
                    val occurrence = row["occurrenceAt"]?.toLongOrNull() ?: return@mapNotNull null
                    series to occurrence
                }
                require(existing.none { it in incoming }) {
                    "La combinación no es posible: hay tareas recurrentes con la misma serie y fecha."
                }
            }
        }
        snapshot.tables["events"]?.let { events ->
            if ("parentId" in events.columns && "originalStart" in events.columns) {
                val existing = database.openHelper.writableDatabase.query(
                    "SELECT parentId, originalStart FROM events WHERE parentId IS NOT NULL AND originalStart IS NOT NULL"
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
                    }
                }
                val incoming = events.rows.mapNotNull { row ->
                    val parent = row["parentId"]?.toLongOrNull() ?: return@mapNotNull null
                    val start = row["originalStart"]?.toLongOrNull() ?: return@mapNotNull null
                    parent to start
                }
                require(existing.none { it in incoming }) {
                    "La combinación no es posible: hay excepciones de calendario duplicadas."
                }
            }
        }
    }

    private fun checkNameConflict(
        snapshot: DatabaseSnapshot,
        table: String,
        column: String,
        conflicts: MutableList<String>,
    ) {
        val data = snapshot.tables[table] ?: return
        val incoming = data.rows.mapNotNull { it[column]?.trim()?.lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (incoming.isEmpty()) return
        val existing = database.openHelper.writableDatabase.query("SELECT \"$column\" FROM \"$table\"").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0)) add(cursor.getString(0).trim().lowercase(Locale.ROOT))
                }
            }
        }
        if (existing.any { it in incoming }) conflicts += table
    }

    private fun existingIds(table: String): Set<Long> =
        database.openHelper.writableDatabase.query("SELECT id FROM \"$table\"").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }

    private fun insert(table: String, data: TableSnapshot) {
        if (data.rows.isEmpty()) return
        val quoted = data.columns.joinToString(", ") { "\"$it\"" }
        val placeholders = data.columns.joinToString(", ") { "?" }
        val sql = "INSERT INTO \"$table\" ($quoted) VALUES ($placeholders)"
        data.rows.forEach { row ->
            val args = data.columns.map { row[it] }.toTypedArray()
            database.openHelper.writableDatabase.execSQL(sql, args)
        }
    }

    private fun tableColumns(table: String): Set<String> =
        database.openHelper.writableDatabase.query("PRAGMA table_info(\"$table\")").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    companion object {
        /** Insert order respects foreign keys; deletes run in reverse. */
        val TABLES = listOf(
            "entries",
            "folders",
            "categories",
            "labels",
            "notes",
            "checklist_items",
            "tasks",
            "subtasks",
            "custom_tables",
            "custom_columns",
            "column_options",
            "custom_rows",
            "custom_cells",
            "events",
            "activities",
            "note_labels",
            "task_labels",
            "table_labels",
            "event_labels",
            "saved_views",
        )
        private const val MAX_ROWS = 500_000
        private const val MAX_VALUE_CHARS = 1_000_000
    }
}
