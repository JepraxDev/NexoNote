package com.example.nexonote.data.transfer

import android.net.Uri
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.ColumnOption
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.ImportColumnSpec
import com.example.nexonote.domain.model.ImportMapping
import com.example.nexonote.domain.model.ImportOptions
import com.example.nexonote.domain.model.ImportPreview
import com.example.nexonote.domain.model.ImportResult
import com.example.nexonote.domain.model.ImportRowError
import com.example.nexonote.domain.model.ParsedImport
import com.example.nexonote.domain.model.normalizeCellValue
import com.example.nexonote.domain.repository.ImportRowValues
import com.example.nexonote.domain.repository.TableImportRepository
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CSV/XLSX import pipeline: parse, preview, map, validate every row and then hand the valid rows
 * to a single transactional writer. Formula prefixes added on export are removed again here.
 */
class TableImportService(
    private val files: FileGateway,
    private val repository: TableImportRepository,
) {
    suspend fun parse(
        uri: Uri,
        fileName: String? = null,
        sheetIndex: Int = 0,
        firstRowIsHeader: Boolean = true,
    ): ParsedImport = withContext(Dispatchers.IO) {
        val name = fileName?.takeIf { it.isNotBlank() }
            ?: files.displayName(uri)
            ?: "importacion"
        val input = files.openInput(uri).buffered()
        val sheets: List<Pair<String, List<List<String>>>> = if (isXlsx(name, input)) {
            XlsxCodec.read(input).map { it.name to it.textRows() }
        } else {
            listOf(name.substringBeforeLast('.') to csvRows(input))
        }
        val index = sheetIndex.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
        val sheet = sheets.getOrNull(index)
            ?: throw IllegalArgumentException("El archivo no contiene hojas.")
        buildParsed(name, sheets.map { it.first }, index, sheet.second, firstRowIsHeader)
    }

    fun preview(parsed: ParsedImport): ImportPreview {
        val headers = parsed.headers
        val types = headers.indices.map { index ->
            TransferFormats.detectType(parsed.rows.take(300).map { it.getOrNull(index).orEmpty() })
        }
        return ImportPreview(
            fileName = parsed.fileName,
            sheetNames = parsed.sheetNames,
            headers = headers,
            detectedTypes = types,
            sampleRows = parsed.rows.take(PREVIEW_ROWS).map { row -> headers.indices.map { row.getOrNull(it).orEmpty() } },
            totalRows = parsed.rows.size,
        )
    }

    /** Suggested mapping by column name, used as the initial state in the UI. */
    fun autoMapping(parsed: ParsedImport, columns: List<CustomColumn>): List<ImportMapping> =
        columns.map { column ->
            val index = parsed.headers.indexOfFirst { it.equals(column.name, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?: parsed.headers.indexOfFirst { it.trim().equals(column.displayName, ignoreCase = true) }
                    .takeIf { it >= 0 }
            ImportMapping(column.id, column.name, column.type, index)
        }

    fun mappingForNewTable(parsed: ParsedImport): List<ImportMapping> {
        val types = parsed.headers.indices.map { index ->
            TransferFormats.detectType(parsed.rows.take(300).map { it.getOrNull(index).orEmpty() })
        }
        return parsed.headers.mapIndexed { index, header ->
            ImportMapping(null, header, types[index], index)
        }
    }

    suspend fun import(
        parsed: ParsedImport,
        mappings: List<ImportMapping>,
        options: ImportOptions,
        targetTableId: Long?,
        newTableName: String? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        val mappingsWithSource = mappings.filter { it.sourceIndex != null }
        require(mappingsWithSource.isNotEmpty()) { "Asigna al menos una columna de origen." }

        val targetColumns: List<CustomColumn>
        val newColumns: List<ImportColumnSpec>
        if (targetTableId != null) {
            targetColumns = repository.columns(targetTableId)
            require(targetColumns.isNotEmpty()) { "La tabla de destino no existe o no tiene columnas." }
            newColumns = emptyList()
        } else {
            targetColumns = emptyList()
            newColumns = mappingsWithSource.map { mapping ->
                ImportColumnSpec(
                    name = mapping.targetName.ifBlank { "Columna" },
                    type = mapping.targetType,
                )
            }
        }

        val aligned = if (targetTableId != null) {
            targetColumns.map { column ->
                mappings.firstOrNull { it.targetColumnId == column.id }?.sourceIndex
            }
        } else {
            mappingsWithSource.map { it.sourceIndex }
        }
        val targetTypes: List<ColumnType> =
            if (targetTableId != null) targetColumns.map { it.type } else newColumns.map { it.type }
        val targetRequired: List<Boolean> =
            if (targetTableId != null) targetColumns.map { it.required } else newColumns.map { it.required }
        val targetOptions: List<List<ColumnOption>> =
            if (targetTableId != null) targetColumns.map { it.options } else newColumns.map { emptyList() }
        val primaryIndex = if (targetTableId != null) {
            targetColumns.indexOfFirst { it.primary }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        val validRows = mutableListOf<ImportRowValues>()
        val errors = mutableListOf<ImportRowError>()
        val mappedFlags = aligned.map { it != null }
        var invalid = 0
        parsed.rows.forEachIndexed { index, row ->
            val rowNumber = index + if (parsed.firstRowIsHeader) 2 else 1
            val values = mutableListOf<String?>()
            val messages = mutableListOf<String>()
            aligned.forEachIndexed { targetIndex, sourceIndex ->
                val raw = sourceIndex?.let { row.getOrNull(it) }
                    ?.trim()
                    .orEmpty()
                val type = targetTypes[targetIndex]
                val optionsForColumn = targetOptions[targetIndex]
                val normalized = try {
                    normalizeForType(type, raw, optionsForColumn, targetRequired[targetIndex])
                } catch (error: IllegalArgumentException) {
                    messages += "${newColumns.getOrNull(targetIndex)?.name ?: targetColumns.getOrNull(targetIndex)?.name ?: "Columna"}: " +
                        (error.message ?: "valor no válido")
                    null
                }
                values += normalized
            }
            if (messages.isNotEmpty()) {
                invalid++
                errors += ImportRowError(rowNumber, messages.joinToString(" · "))
            } else if (values.any { !it.isNullOrBlank() }) {
                validRows += ImportRowValues(rowNumber, values, mappedFlags)
            }
        }

        if (errors.isNotEmpty() && !options.skipInvalidRows) {
            return@withContext ImportResult(errors = errors)
        }
        if (validRows.isEmpty()) {
            return@withContext ImportResult(
                skipped = invalid,
                errors = errors,
                createdTableId = targetTableId,
            )
        }

        val written = repository.write(
            targetTableId = targetTableId,
            newTableName = newTableName?.trim()?.takeIf { it.isNotEmpty() }
                ?: parsed.fileName.substringBeforeLast('.').ifBlank { "Importación" },
            newColumns = newColumns,
            rows = validRows,
            primaryIndex = primaryIndex,
            strategy = options.duplicateStrategy,
        )
        written.copy(
            skipped = written.skipped + invalid,
            errors = errors,
            createdTableId = written.createdTableId ?: targetTableId,
        )
    }

    private fun normalizeForType(
        type: ColumnType,
        raw: String,
        options: List<ColumnOption>,
        required: Boolean,
    ): String? {
        if (type == ColumnType.DATE || type == ColumnType.DATETIME) {
            if (raw.isBlank()) {
                return normalizeCellValue(type, raw, options, required)
            }
            val millis = TransferFormats.parseDate(raw)
                ?: throw IllegalArgumentException("fecha no válida (usa AAAA-MM-DD)")
            return normalizeCellValue(type, millis.toString(), options, required)
        }
        if (type == ColumnType.CHECKBOX) {
            if (raw.isBlank()) return normalizeCellValue(type, raw, options, required)
            return normalizeCellValue(type, checkboxValue(raw), options, required)
        }
        return normalizeCellValue(type, raw, options, required)
    }

    private fun checkboxValue(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
        "1", "true", "sí", "si", "x", "✓", "yes" -> "1"
        "0", "false", "no" -> "0"
        else -> raw
    }

    private fun buildParsed(
        fileName: String,
        sheetNames: List<String>,
        sheetIndex: Int,
        rawRows: List<List<String>>,
        firstRowIsHeader: Boolean,
    ): ParsedImport {
        require(rawRows.isNotEmpty()) { "El archivo está vacío." }
        val width = rawRows.maxOf { it.size }
        require(width > 0) { "El archivo no contiene columnas." }
        require(rawRows.size <= MAX_ROWS) {
            "El archivo supera el límite de $MAX_ROWS filas por importación."
        }
        require(width <= MAX_COLUMNS) {
            "El archivo supera el límite de $MAX_COLUMNS columnas por importación."
        }
        val padded = rawRows.map { row -> (0 until width).map { row.getOrNull(it).orEmpty() } }
        val first = padded.first()
        val headers = if (firstRowIsHeader) {
            dedupeHeaders(first)
        } else {
            (1..width).map { "Columna $it" }
        }
        val rows = if (firstRowIsHeader) padded.drop(1) else padded
        return ParsedImport(
            fileName = fileName,
            sheetNames = sheetNames,
            sheetIndex = sheetIndex,
            headers = headers,
            rows = rows.map { row -> row.map(FormulaGuard::unescape) },
            firstRowIsHeader = firstRowIsHeader,
        )
    }

    private fun dedupeHeaders(values: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return values.mapIndexed { index, raw ->
            var name = raw.trim().take(80).ifBlank { "Columna ${index + 1}" }
            if (!used.add(name.lowercase(Locale.ROOT))) {
                var suffix = 2
                while (!used.add("$name ($suffix)".lowercase(Locale.ROOT))) suffix++
                name = "$name ($suffix)"
            }
            name
        }
    }

    private fun csvRows(input: InputStream): List<List<String>> {
        val reader: BufferedReader = input.bufferedReader(Charsets.UTF_8)
        val text = StringBuilder()
        val buffer = CharArray(8_192)
        var total = 0
        reader.use {
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_TEXT_CHARS) {
                    throw IllegalArgumentException("El archivo CSV es demasiado grande para importarlo con seguridad.")
                }
                text.append(buffer, 0, read)
            }
        }
        val rows = CsvCodec.parse(text.toString())
        require(rows.isNotEmpty()) { "El archivo CSV está vacío." }
        return rows
    }

    private fun isXlsx(name: String, input: java.io.BufferedInputStream): Boolean {
        if (name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xlsm", ignoreCase = true)) return true
        input.mark(4)
        val signature = ByteArray(4)
        val read = input.read(signature)
        input.reset()
        return read == 4 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte() &&
            signature[2] == 3.toByte() && signature[3] == 4.toByte()
    }

    private companion object {
        const val MAX_ROWS = 20_000
        const val MAX_COLUMNS = 200
        const val MAX_TEXT_CHARS = 32 * 1024 * 1024
        const val PREVIEW_ROWS = 5
    }
}
