package com.example.nexonote.data.transfer

import java.io.BufferedReader
import java.io.Reader
import java.io.Writer

/**
 * RFC 4180 style CSV reader/writer with UTF-8 BOM tolerance and no third-party dependency.
 *
 * The parser accepts comma or semicolon separated files (the delimiter is inferred from the
 * first record) because Spanish spreadsheets commonly export with semicolons.
 */
object CsvCodec {
    fun write(rows: List<List<String?>>, delimiter: Char = ','): String = buildString {
        appendRows(rows, delimiter, this)
    }

    fun appendRows(rows: Iterable<List<String?>>, delimiter: Char = ',', out: Appendable = StringBuilder()) {
        rows.forEach { row ->
            row.forEachIndexed { index, value ->
                if (index > 0) out.append(delimiter)
                out.append(escape(value.orEmpty(), delimiter))
            }
            out.append("\r\n")
        }
    }

    fun writeTo(writer: Writer, rows: Iterable<List<String?>>, delimiter: Char = ',') {
        rows.forEach { row ->
            row.forEachIndexed { index, value ->
                if (index > 0) writer.append(delimiter)
                writer.append(escape(value.orEmpty(), delimiter))
            }
            writer.append("\r\n")
        }
        writer.flush()
    }

    fun parse(text: String): List<List<String>> = parse(text, maxRows = null)

    /**
     * Reads at most [maxRows] records. The header row counts as a record.
     * Values are returned unescaped by the CSV layer; formula prefixes are handled by callers.
     */
    fun parse(text: String, maxRows: Int? = null): List<List<String>> {
        val normalized = text.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(normalized)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun commitField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun commitRow() {
            commitField()
            rows.add(row)
            row = mutableListOf()
        }

        while (index < normalized.length) {
            val char = normalized[index]
            when {
                inQuotes && char == '"' && index + 1 < normalized.length && normalized[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == delimiter -> commitField()
                !inQuotes && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && index + 1 < normalized.length && normalized[index + 1] == '\n') index++
                    commitRow()
                    if (maxRows != null && rows.size >= maxRows) break
                }
                else -> field.append(char)
            }
            index++
        }
        if (maxRows == null || rows.size < maxRows) {
            if (field.isNotEmpty() || row.isNotEmpty()) commitRow()
        }
        return rows.filterNot { record -> record.size == 1 && record[0].isEmpty() }
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        var commas = 0
        var semicolons = 0
        var quoted = false
        for (char in firstLine) {
            when {
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> commas++
                !quoted && char == ';' -> semicolons++
            }
        }
        return if (semicolons > commas) ';' else ','
    }

    private fun escape(value: String, delimiter: Char): String {
        val needsQuotes = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    /** Incremental reader used by the preview to avoid loading an entire file in memory. */
    fun readRecords(reader: Reader, limit: Int = Int.MAX_VALUE): List<List<String>> {
        val buffered = if (reader is BufferedReader) reader else reader.buffered()
        val text = buildString {
            val buffer = CharArray(8_192)
            var total = 0
            while (true) {
                val read = buffered.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_TEXT_BYTES) break
                append(buffer, 0, read)
                if (limit < Int.MAX_VALUE && countLines(this) > limit) break
            }
        }
        return if (limit == Int.MAX_VALUE) parse(text) else parse(text, limit)
    }

    private fun countLines(text: CharSequence): Int {
        var count = 0
        var quoted = false
        for (char in text) {
            when {
                char == '"' -> quoted = !quoted
                !quoted && char == '\n' -> count++
            }
        }
        return count
    }

    private const val MAX_TEXT_BYTES = 32 * 1024 * 1024
}
