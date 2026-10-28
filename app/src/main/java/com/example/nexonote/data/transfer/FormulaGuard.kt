package com.example.nexonote.data.transfer

/**
 * Protects spreadsheet exports from CSV formula injection.
 *
 * Values coming from the user are prefixed with an apostrophe when they could be interpreted
 * as a formula by Excel, LibreOffice or Google Sheets. XLSX exports never emit formula cells,
 * so only CSV needs this transformation; the prefix is removed again on import.
 */
object FormulaGuard {
    private val dangerousPrefixes = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun escape(value: String): String {
        if (value.isEmpty()) return value
        val firstMeaningful = value.indexOfFirst { !it.isWhitespace() && it != '\uFEFF' }
        if (firstMeaningful < 0) return value
        val candidate = value[firstMeaningful]
        val dangerous = candidate in dangerousPrefixes ||
            value.firstOrNull() == '\uFEFF' ||
            (firstMeaningful > 0 && candidate in dangerousPrefixes)
        return if (dangerous) "'$value" else value
    }

    /** Reverses [escape] without touching values that only happen to start with an apostrophe. */
    fun unescape(value: String): String {
        if (value.length < 2 || value[0] != '\'') return value
        val next = value.substring(1).firstOrNull { !it.isWhitespace() && it != '\uFEFF' } ?: return value
        return if (next in dangerousPrefixes || value[1] == '\uFEFF') value.substring(1) else value
    }
}
