package com.example.nexonote.domain.export

import com.example.nexonote.domain.model.ColumnType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Locale-independent date/number helpers shared by every export and table import. */
object TransferFormats {
    private val datePatterns = listOf(
        "yyyy-MM-dd",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "dd/MM/yyyy",
        "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy",
    )

    fun formatDate(millis: Long?): String = format(millis, "yyyy-MM-dd")

    fun formatDateTime(millis: Long?): String = format(millis, "yyyy-MM-dd HH:mm")

    private fun format(millis: Long?, pattern: String): String {
        if (millis == null) return ""
        return SimpleDateFormat(pattern, Locale.ROOT).format(Date(millis))
    }

    fun parseDate(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return null
        if (value.matches(Regex("^-?\\d{10,}$"))) return value.toLongOrNull()
        for (pattern in datePatterns) {
            val format = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
            val position = java.text.ParsePosition(0)
            val parsed = format.parse(value, position)
            // Requiring the whole string to be consumed prevents a date-only pattern from
            // accepting "2026-10-28 14:30" and silently dropping the time.
            if (parsed != null && position.index == value.length) return parsed.time
        }
        return null
    }

    fun isDateLike(text: String): Boolean = parseDate(text) != null && !text.trim().matches(Regex("^-?\\d{10,}$"))

    fun parseCheckbox(text: String): Boolean? = when (text.trim().lowercase(Locale.ROOT)) {
        "1", "true", "sí", "si", "x", "✓", "yes" -> true
        "0", "false", "no", "" -> false
        else -> null
    }

    fun parseDecimal(text: String): BigDecimal? =
        text.trim().replace(" ", "").replace(',', '.').toBigDecimalOrNull()

    fun detectType(values: List<String>): ColumnType {
        val samples = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (samples.isEmpty()) return ColumnType.SHORT_TEXT
        val checkboxTokens = setOf("1", "0", "true", "false", "sí", "si", "no", "x", "✓", "yes")
        if (samples.all { it.lowercase(Locale.ROOT) in checkboxTokens } &&
            samples.any { it.lowercase(Locale.ROOT) !in setOf("1", "0") }
        ) {
            return ColumnType.CHECKBOX
        }
        if (samples.all { it.matches(Regex("^-?\\d{1,18}$")) }) return ColumnType.INTEGER
        if (samples.all { parseDecimal(it) != null }) return ColumnType.DECIMAL
        if (samples.all { isDateLike(it) }) {
            return if (samples.any { it.contains(':') }) ColumnType.DATETIME else ColumnType.DATE
        }
        if (samples.all { it.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) }) {
            return ColumnType.EMAIL
        }
        if (samples.all { it.startsWith("http://") || it.startsWith("https://") }) return ColumnType.URL
        if (samples.any { it.length > 500 }) return ColumnType.LONG_TEXT
        return ColumnType.SHORT_TEXT
    }

    /** Normalises an ISO-ish date or numeric value so it can be written back to the table. */
    fun toEpochMillis(text: String): Long? = parseDate(text)

    /** Human-readable size used by export/backup result messages. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun sanitizeFileName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().trim('.')
        return cleaned.ifBlank { "NexoNote" }.take(80)
    }

    fun uniqueFileName(base: String, extension: String, existing: Set<String>): String {
        val safeBase = sanitizeFileName(base)
        var candidate = "$safeBase.$extension"
        var suffix = 2
        while (candidate.lowercase(Locale.ROOT) in existing) {
            candidate = "$safeBase ($suffix).$extension"
            suffix++
        }
        return candidate
    }
}
