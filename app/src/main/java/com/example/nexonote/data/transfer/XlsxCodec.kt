package com.example.nexonote.data.transfer

import com.example.nexonote.domain.export.SheetCell
import com.example.nexonote.domain.export.SheetCellKind
import com.example.nexonote.domain.export.SheetData
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Small OOXML SpreadsheetML writer/reader built on `java.util.zip` and the platform SAX parser.
 *
 * Why not Apache POI: `poi-ooxml` depends on `java.awt` and `javax.xml.stream`, which are not
 * available on Android, and its method count is excessive for a mobile app. The project only
 * needs plain values in one or more sheets, so a focused OOXML codec is safer and smaller.
 * Files are written with inline strings (never formulas), which also neutralises formula
 * injection in XLSX.
 */
object XlsxCodec {
    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
    private const val MAX_SHEETS = 64
    private const val MAX_ROWS = 50_000
    private const val MAX_COLUMNS = 1_024

    fun write(file: File, sheets: List<SheetData>) {
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { write(it, sheets) }
    }

    fun write(out: OutputStream, sheets: List<SheetData>) {
        require(sheets.isNotEmpty()) { "El libro debe tener al menos una hoja." }
        require(sheets.size <= MAX_SHEETS) { "El libro supera el límite de hojas." }
        val normalized = normalizeSheets(sheets)
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes(normalized.size).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(rootRelationships().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(workbook(normalized).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(workbookRelationships(normalized.size).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(styles().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            normalized.forEachIndexed { index, sheet ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
                writeSheetXml(zip, sheet)
                zip.closeEntry()
            }
        }
    }

    fun read(file: File): List<SheetData> = file.inputStream().buffered().use { read(it) }

    fun read(input: InputStream): List<SheetData> {
        val entries = readZipEntries(input)
        val workbook = entries["xl/workbook.xml"] ?: error("El archivo XLSX no contiene un libro válido.")
        val relationships = entries["xl/_rels/workbook.xml.rels"].orEmpty()
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val dateStyles = entries["xl/styles.xml"]?.let(::parseDateStyles).orEmpty()
        val relTargets = parseRelationships(relationships)
        val sheets = parseWorkbook(workbook)

        val result = mutableListOf<SheetData>()
        sheets.forEachIndexed { index, info ->
            val target = info.relationshipId?.let { relTargets[it] } ?: "worksheets/sheet${index + 1}.xml"
            val path = resolvePart(target)
            val xml = entries[path] ?: return@forEachIndexed
            result.add(SheetData(info.name.ifBlank { "Hoja ${index + 1}" }, parseSheet(xml, sharedStrings, dateStyles)))
        }
        if (result.isEmpty()) error("El archivo XLSX no contiene hojas legibles.")
        return result
    }

    private fun readZipEntries(input: InputStream): Map<String, String> {
        val wanted = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (!name.startsWith("xl/")) continue
                val bytes = zip.readLimited(MAX_ENTRY_BYTES)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) error("El archivo XLSX es demasiado grande para importarlo con seguridad.")
                wanted[name] = bytes
            }
        }
        return wanted.mapValues { (_, bytes) -> bytes.toString(Charsets.UTF_8) }
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArray(8_192)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            if (output.size() + read > limit) error("Una hoja del XLSX supera el tamaño permitido.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun normalizeSheets(sheets: List<SheetData>): List<SheetData> {
        val used = mutableSetOf<String>()
        return sheets.mapIndexed { index, sheet ->
            var base = sheet.name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().ifBlank { "Hoja ${index + 1}" }
            if (base.length > 31) base = base.take(31)
            var name = base
            var suffix = 2
            while (!used.add(name)) {
                val suffixText = " ($suffix)"
                name = base.take((31 - suffixText.length).coerceAtLeast(1)) + suffixText
                suffix++
            }
            require(sheet.rows.size <= MAX_ROWS) { "La hoja «${sheet.name}» supera el límite de $MAX_ROWS filas." }
            require(sheet.rows.all { it.size <= MAX_COLUMNS }) {
                "La hoja «${sheet.name}» supera el límite de $MAX_COLUMNS columnas."
            }
            SheetData(name, sheet.rows)
        }
    }

    private fun contentTypes(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (index in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$index.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRelationships(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbook(sheets: List<SheetData>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${xmlAttribute(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelationships(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (index in 1..count) {
            append(
                """<Relationship Id="rId$index" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$index.xml"/>"""
            )
        }
        append(
            """<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>"""
        )
        append("</Relationships>")
    }

    private fun styles(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>""" +
            """</styleSheet>"""

    private fun writeSheetXml(out: java.io.OutputStream, sheet: SheetData) {
        val writer = out.writer(Charsets.UTF_8).buffered()
        writer.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        writer.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            writer.append("<row r=\"$rowNumber\">")
            row.forEachIndexed { columnIndex, cell ->
                if (cell == null) return@forEachIndexed
                val reference = "${columnName(columnIndex)}$rowNumber"
                when (cell.kind) {
                    SheetCellKind.NUMBER -> writer.append("<c r=\"$reference\"><v>${cell.value}</v></c>")
                    SheetCellKind.BOOLEAN -> writer.append(
                        "<c r=\"$reference\" t=\"b\"><v>${if (cell.value == "1") "1" else "0"}</v></c>"
                    )
                    SheetCellKind.TEXT -> writer.append(
                        "<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlText(cell.value)}</t></is></c>"
                    )
                }
            }
            writer.append("</row>")
        }
        writer.append("</sheetData></worksheet>")
        writer.flush()
    }

    private fun columnName(index: Int): String {
        var value = index
        val builder = StringBuilder()
        while (true) {
            builder.insert(0, ('A' + value % 26))
            value = value / 26 - 1
            if (value < 0) break
        }
        return builder.toString()
    }

    private fun columnIndex(reference: String): Int {
        var value = 0
        for (char in reference) {
            val upper = char.uppercaseChar()
            if (upper !in 'A'..'Z') break
            value = value * 26 + (upper - 'A' + 1)
        }
        return (value - 1).coerceAtLeast(0)
    }

    private data class SheetInfo(val name: String, val relationshipId: String?)

    private fun parseWorkbook(xml: String): List<SheetInfo> {
        val sheets = mutableListOf<SheetInfo>()
        parseSax(xml) { handler ->
            handler.onElement = { name, attributes ->
                if (name == "sheet") {
                    sheets.add(
                        SheetInfo(
                            name = attributes.value("name").orEmpty(),
                            relationshipId = attributes.value("r:id") ?: attributes.value("id"),
                        )
                    )
                }
            }
        }
        return sheets
    }

    private fun parseRelationships(xml: String): Map<String, String> {
        if (xml.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        parseSax(xml) { handler ->
            handler.onElement = { name, attributes ->
                if (name == "Relationship") {
                    val id = attributes.value("Id")
                    val target = attributes.value("Target")
                    if (id != null && target != null) map[id] = target
                }
            }
        }
        return map
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val strings = mutableListOf<String>()
        var current: StringBuilder? = null
        var inText = false
        parseSax(xml) { handler ->
            handler.onStart = { name, _ ->
                when (name) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = true
                }
            }
            handler.onText = { text -> if (inText) current?.append(text) }
            handler.onEnd = { name ->
                when (name) {
                    "t" -> inText = false
                    "si" -> {
                        strings.add(current?.toString().orEmpty())
                        current = null
                    }
                }
            }
        }
        return strings
    }

    private fun parseDateStyles(xml: String): Set<Int> {
        val customFormats = mutableMapOf<Int, String>()
        val xfFormats = mutableListOf<Int>()
        var insideCellXfs = false
        parseSax(xml) { handler ->
            handler.onStart = { name, attributes ->
                when (name) {
                    "numFmt" -> {
                        val id = attributes.value("numFmtId")?.toIntOrNull()
                        val code = attributes.value("formatCode")
                        if (id != null && code != null) customFormats[id] = code
                    }
                    "cellXfs" -> insideCellXfs = true
                    "xf" -> if (insideCellXfs) xfFormats.add(attributes.value("numFmtId")?.toIntOrNull() ?: 0)
                }
            }
            handler.onEnd = { name -> if (name == "cellXfs") insideCellXfs = false }
        }
        return xfFormats.withIndex()
            .filter { (_, format) -> isDateFormat(format, customFormats[format]) }
            .map { (index, _) -> index }
            .toSet()
    }

    private fun isDateFormat(id: Int, custom: String?): Boolean {
        if (id in 14..22 || id in 27..36 || id in 45..47 || id in 50..58) return true
        val code = custom ?: return false
        val unquoted = code.replace(Regex("\"[^\"]*\""), "").lowercase(Locale.ROOT)
        return unquoted.any { it == 'y' || it == 'd' } && (unquoted.contains('m') || unquoted.contains('h'))
    }

    private fun parseSheet(xml: String, shared: List<String>, dateStyles: Set<Int>): List<List<SheetCell?>> {
        val rows = mutableMapOf<Int, MutableMap<Int, SheetCell?>>()
        var currentRow = -1
        var currentColumn = 0
        var cellType: String? = null
        var cellStyle: Int? = null
        var typeCaptured = false
        var value: StringBuilder? = null
        var inlineText: StringBuilder? = null
        var inValue = false
        var inInlineText = false

        fun flushCell() {
            if (!typeCaptured) return
            val raw = value?.toString().orEmpty()
            val resolved = when (cellType) {
                "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1)
                "inlineStr" -> inlineText?.toString()
                "b" -> if (raw == "1") "1" else "0"
                else -> raw
            }
            val cell = when {
                resolved == null -> null
                cellType == "b" -> SheetCell(resolved)
                (cellType == null || cellType == "n") && resolved.isNotBlank() -> {
                    val numeric = resolved.toDoubleOrNull()
                    if (numeric != null && cellStyle != null && cellStyle in dateStyles) {
                        SheetCell(formatExcelSerial(numeric))
                    } else {
                        SheetCell(resolved)
                    }
                }
                else -> SheetCell(resolved)
            }
            rows.getOrPut(currentRow + 1) { mutableMapOf() }[currentColumn] = cell
            typeCaptured = false
            value = null
            inlineText = null
            cellType = null
            cellStyle = null
        }

        parseSax(xml) { handler ->
            handler.onStart = { name, attributes ->
                when (name) {
                    "row" -> {
                        currentRow = attributes.value("r")?.toIntOrNull()?.minus(1) ?: (currentRow + 1)
                        currentColumn = 0
                    }
                    "c" -> {
                        flushCell()
                        val reference = attributes.value("r")
                        currentColumn = if (reference != null) columnIndex(reference) else currentColumn
                        cellType = attributes.value("t")
                        cellStyle = attributes.value("s")?.toIntOrNull()
                        typeCaptured = true
                    }
                    "v" -> { inValue = true; value = StringBuilder() }
                    "is" -> inlineText = StringBuilder()
                    "t" -> if (inlineText != null) inInlineText = true
                }
            }
            handler.onText = { text ->
                if (inValue) value?.append(text)
                if (inInlineText) inlineText?.append(text)
            }
            handler.onEnd = { name ->
                when (name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        flushCell()
                        currentColumn++
                    }
                }
            }
        }
        flushCell()
        if (rows.isEmpty()) return emptyList()
        val maxRow = rows.keys.max()
        if (maxRow > MAX_ROWS) error("La hoja supera el límite de filas permitido.")
        val maxColumn = rows.values.maxOfOrNull { it.keys.maxOrNull() ?: -1 } ?: -1
        if (maxColumn >= MAX_COLUMNS) error("La hoja supera el límite de columnas permitido.")
        return (0 until maxRow).map { index ->
            val cells = rows[index + 1] ?: emptyMap()
            (0..maxColumn).map { column -> cells[column] }
        }
    }

    private fun formatExcelSerial(serial: Double): String {
        val millis = ((serial - 25_569.0) * 86_400_000.0).toLong()
        val hasTime = serial - kotlin.math.floor(serial) > 0.000_001
        val format = SimpleDateFormat(if (hasTime) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd", Locale.ROOT)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }

    private fun resolvePart(target: String): String {
        val clean = target.removePrefix("/")
        if (clean.startsWith("xl/")) return clean
        if (clean.startsWith("../")) return clean.removePrefix("../")
        return "xl/$clean"
    }

    private fun parseSax(xml: String, configure: (Handler) -> Unit) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val handler = Handler()
        configure(handler)
        factory.newSAXParser().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), handler)
    }

    private class Handler : DefaultHandler() {
        var onStart: (String, Attributes) -> Unit = { _, _ -> }
        var onElement: (String, Attributes) -> Unit = { _, _ -> }
        var onEnd: (String) -> Unit = {}
        var onText: (String) -> Unit = {}

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty()
            onStart(name, attributes)
            onElement(name, attributes)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            onEnd(localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty())
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            onText(String(ch, start, length))
        }
    }

    private fun Attributes.value(name: String): String? =
        getValue(name) ?: (0 until length).map { getQName(it) }.firstOrNull { it.substringAfter(':') == name }
            ?.let { getValue(it) }

    private fun xmlText(value: String): String {
        val sanitized = buildString(value.length) {
            value.forEach { char ->
                val code = char.code
                if (char == '\t' || char == '\n' || char == '\r' ||
                    code in 0x20..0xD7FF || code in 0xE000..0xFFFD
                ) {
                    append(char)
                }
            }
        }
        return sanitized
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun xmlAttribute(value: String): String = xmlText(value).replace("'", "&apos;")
}
