package com.example.nexonote.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DEFAULT_COLUMN_NAME = "Nombre"
const val DEFAULT_TABLE_COLOR = "#6750A4"

enum class TableIcon(val label: String) {
    GRID("Cuadrícula"),
    FOLDER("Carpeta"),
    LIGHTBULB("Idea"),
    STAR("Favorita"),
    WORK("Trabajo"),
    HOME("Casa"),
    BOOK("Libro"),
    CALENDAR("Calendario"),
    FLAG("Objetivo"),
    LIST("Lista"),
}

enum class ColumnType(
    val label: String,
    val usesOptions: Boolean = false,
    val isNumeric: Boolean = false,
) {
    SHORT_TEXT("Texto corto"),
    LONG_TEXT("Texto largo"),
    INTEGER("Número entero", isNumeric = true),
    DECIMAL("Número decimal", isNumeric = true),
    CURRENCY("Moneda", isNumeric = true),
    DATE("Fecha"),
    DATETIME("Fecha y hora"),
    CHECKBOX("Casilla"),
    SELECT("Selección", usesOptions = true),
    MULTISELECT("Selección múltiple", usesOptions = true),
    STATUS("Estado", usesOptions = true),
    URL("URL"),
    EMAIL("Correo electrónico"),
    PHONE("Teléfono"),
}

enum class TableFilterOperator(val label: String) {
    CONTAINS("Contiene"),
    EQUALS("Es igual a"),
    NOT_EMPTY("Tiene valor"),
    GREATER("Es mayor que"),
    LESS("Es menor que"),
    BEFORE("Antes de"),
    AFTER("Después de"),
    ON("En la fecha"),
    IS_TRUE("Está marcado"),
    IS_FALSE("No está marcado"),
}

data class ColumnOption(
    val id: Long = 0,
    val columnId: Long = 0,
    val name: String,
    val color: String,
    val position: Int = 0,
)

data class CustomColumn(
    val id: Long = 0,
    val tableId: Long = 0,
    val name: String,
    val type: ColumnType,
    val required: Boolean = false,
    val primary: Boolean = false,
    val defaultValue: String? = null,
    val position: Int = 0,
    val options: List<ColumnOption> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { "Columna sin nombre" }
}

data class CustomTable(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: TableIcon = TableIcon.GRID,
    val color: String = DEFAULT_TABLE_COLOR,
    val columnCount: Int = 0,
    val rowCount: Int = 0,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { "Tabla sin título" }
}

data class TableRow(
    val id: Long,
    val tableId: Long,
    val title: String,
    val cells: Map<Long, String> = emptyMap(),
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
) {
    val displayTitle: String get() = title.ifBlank { "Registro sin título" }
    fun value(columnId: Long): String? = cells[columnId]?.takeIf { it.isNotBlank() }
}

data class RecentTableRow(
    val rowId: Long,
    val tableId: Long,
    val tableName: String,
    val title: String,
    val modifiedAt: Long?,
)

data class TablePage(
    val rows: List<TableRow>,
    val hasMore: Boolean,
)

data class TableInput(
    val name: String,
    val description: String = "",
    val icon: TableIcon = TableIcon.GRID,
    val color: String = DEFAULT_TABLE_COLOR,
    val folderId: Long? = null,
    val categoryId: Long? = null,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
)

data class ColumnInput(
    val name: String,
    val type: ColumnType,
    val required: Boolean = false,
    val defaultValue: String? = null,
)

data class OptionInput(
    val name: String,
    val color: String,
)

data class TableFilter(
    val columnId: Long,
    val operator: TableFilterOperator,
    val value: String = "",
)

data class TableSort(
    val columnId: Long,
    val ascending: Boolean = true,
)

data class TableQuery(
    val search: String = "",
    val filters: List<TableFilter> = emptyList(),
    val sort: TableSort? = null,
)

fun validateTableInput(input: TableInput): String? {
    if (input.name.isBlank()) return "El nombre de la tabla es obligatorio."
    if (input.name.trim().length > 80) return "El nombre no puede superar 80 caracteres."
    if (input.description.length > 500) return "La descripción no puede superar 500 caracteres."
    if (!input.color.matches(Regex("^#[0-9a-fA-F]{6}$"))) return "El color no es válido."
    return null
}

fun validateColumnInput(input: ColumnInput): String? {
    if (input.name.isBlank()) return "El nombre de la columna es obligatorio."
    if (input.name.trim().length > 80) return "El nombre no puede superar 80 caracteres."
    if (input.defaultValue != null) {
        if (input.defaultValue.length > 20_000) return "El valor predeterminado es demasiado largo."
        if (!input.type.usesOptions && input.defaultValue.isNotBlank()) {
            runCatching { normalizeCellValue(input.type, input.defaultValue, emptyList(), required = false) }
                .onFailure { return it.message ?: "El valor predeterminado no es válido." }
        }
    }
    return null
}

fun validateOptionInput(input: OptionInput): String? {
    if (input.name.isBlank()) return "El nombre de la opción es obligatorio."
    if (input.name.trim().length > 60) return "La opción no puede superar 60 caracteres."
    if (!input.color.matches(Regex("^#[0-9a-fA-F]{6}$"))) return "El color no es válido."
    return null
}

fun filterOperatorsFor(type: ColumnType): List<TableFilterOperator> = when (type) {
    ColumnType.SHORT_TEXT, ColumnType.LONG_TEXT, ColumnType.URL,
    ColumnType.EMAIL, ColumnType.PHONE -> listOf(
        TableFilterOperator.CONTAINS,
        TableFilterOperator.EQUALS,
        TableFilterOperator.NOT_EMPTY,
    )
    ColumnType.INTEGER, ColumnType.DECIMAL, ColumnType.CURRENCY -> listOf(
        TableFilterOperator.EQUALS,
        TableFilterOperator.GREATER,
        TableFilterOperator.LESS,
        TableFilterOperator.NOT_EMPTY,
    )
    ColumnType.DATE, ColumnType.DATETIME -> listOf(
        TableFilterOperator.ON,
        TableFilterOperator.BEFORE,
        TableFilterOperator.AFTER,
        TableFilterOperator.NOT_EMPTY,
    )
    ColumnType.CHECKBOX -> listOf(TableFilterOperator.IS_TRUE, TableFilterOperator.IS_FALSE)
    ColumnType.SELECT, ColumnType.STATUS -> listOf(
        TableFilterOperator.EQUALS,
        TableFilterOperator.NOT_EMPTY,
    )
    ColumnType.MULTISELECT -> listOf(
        TableFilterOperator.CONTAINS,
        TableFilterOperator.NOT_EMPTY,
    )
}

fun normalizeCellValue(
    type: ColumnType,
    raw: String?,
    options: List<ColumnOption>,
    required: Boolean = false,
): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) {
        if (required && type != ColumnType.CHECKBOX) throw IllegalArgumentException("Este campo es obligatorio.")
        return null
    }
    return when (type) {
        ColumnType.SHORT_TEXT -> {
            if (value.length > 500) throw IllegalArgumentException("El texto no puede superar 500 caracteres.")
            value
        }
        ColumnType.LONG_TEXT -> {
            if (value.length > 20_000) throw IllegalArgumentException("El texto es demasiado largo.")
            value
        }
        ColumnType.INTEGER -> {
            value.replace(" ", "").toLongOrNull()?.toString()
                ?: throw IllegalArgumentException("Introduce un número entero válido.")
        }
        ColumnType.DECIMAL, ColumnType.CURRENCY -> {
            val decimal = value.replace(" ", "").replace(',', '.').toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Introduce un número válido.")
            if (type == ColumnType.CURRENCY) {
                decimal.setScale(2, RoundingMode.HALF_UP).toPlainString()
            } else {
                decimal.stripTrailingZeros().toPlainString()
            }
        }
        ColumnType.DATE -> {
            val parsed = value.toLongOrNull()
                ?: throw IllegalArgumentException("Selecciona una fecha válida.")
            startOfDay(parsed).toString()
        }
        ColumnType.DATETIME -> {
            value.toLongOrNull()?.toString()
                ?: throw IllegalArgumentException("Selecciona una fecha y hora válidas.")
        }
        ColumnType.CHECKBOX -> when (value.lowercase(Locale.ROOT)) {
            "1", "true", "sí", "si" -> "1"
            "0", "false", "no" -> "0"
            else -> throw IllegalArgumentException("La casilla solo admite sí o no.")
        }
        ColumnType.SELECT, ColumnType.STATUS -> {
            val option = options.firstOrNull { it.id.toString() == value || it.name.equals(value, true) }
                ?: throw IllegalArgumentException("Selecciona una opción válida.")
            option.id.toString()
        }
        ColumnType.MULTISELECT -> {
            val ids = value.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
            if (ids.isEmpty()) return null
            val resolved = ids.map { token ->
                options.firstOrNull { it.id.toString() == token || it.name.equals(token, true) }
                    ?: throw IllegalArgumentException("Una de las opciones ya no existe.")
            }.distinctBy { it.id }
            resolved.joinToString(",") { it.id.toString() }
        }
        ColumnType.URL -> {
            val uri = runCatching { URI(value) }.getOrNull()
            if (uri == null || uri.host.isNullOrBlank() || uri.scheme !in setOf("http", "https")) {
                throw IllegalArgumentException("Introduce una URL http o https válida.")
            }
            value
        }
        ColumnType.EMAIL -> {
            if (!value.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                throw IllegalArgumentException("Introduce un correo electrónico válido.")
            }
            value
        }
        ColumnType.PHONE -> {
            val digits = value.count(Char::isDigit)
            if (digits < 6 || !value.matches(Regex("^[+()0-9 .-]+$"))) {
                throw IllegalArgumentException("Introduce un teléfono válido.")
            }
            value
        }
    }
}

fun displayCellValue(
    type: ColumnType,
    value: String?,
    options: List<ColumnOption>,
): String {
    if (value.isNullOrBlank()) return ""
    return when (type) {
        ColumnType.CHECKBOX -> if (value == "1") "Sí" else "No"
        ColumnType.SELECT, ColumnType.STATUS ->
            options.firstOrNull { it.id.toString() == value }?.name.orEmpty()
        ColumnType.MULTISELECT -> value.split(',')
            .mapNotNull { token -> options.firstOrNull { it.id.toString() == token }?.name }
            .joinToString(", ")
        ColumnType.DATE -> formatTableDate(value.toLongOrNull(), withTime = false)
        ColumnType.DATETIME -> formatTableDate(value.toLongOrNull(), withTime = true)
        ColumnType.CURRENCY -> value.toBigDecimalOrNull()?.let { amount ->
            java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es"))
                .format(amount)
        } ?: value
        ColumnType.INTEGER, ColumnType.DECIMAL -> value
        else -> value
    }
}

fun tableRowTitle(primary: CustomColumn?, value: String?, options: List<ColumnOption>): String {
    val text = displayCellValue(primary?.type ?: ColumnType.SHORT_TEXT, value, options).trim()
    return text.ifBlank { "Sin título" }.take(120)
}

fun primaryColumn(columns: List<CustomColumn>): CustomColumn? =
    columns.firstOrNull { it.primary } ?: columns.minByOrNull { it.position }

private fun formatTableDate(timestamp: Long?, withTime: Boolean): String {
    if (timestamp == null) return ""
    val pattern = if (withTime) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
    return SimpleDateFormat(pattern, Locale.forLanguageTag("es")).format(Date(timestamp))
}
