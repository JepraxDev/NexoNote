package com.example.nexonote.data.transfer

import com.example.nexonote.domain.model.BACKUP_FORMAT
import com.example.nexonote.domain.model.BACKUP_FORMAT_VERSION
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.BackupLocation
import com.example.nexonote.domain.model.BackupManifest
import com.example.nexonote.domain.model.BackupStatus
import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.TableSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Explicit JSON codecs for the backup format. Manual encoding keeps the on-disk format documented
 * and independent from Kotlin class names or compiler plugins.
 */
object BackupJson {
    const val DATA_FORMAT = "nexonote-data"
    const val DATA_FORMAT_VERSION = 1
    const val HISTORY_FORMAT = "nexonote-history"
    const val HISTORY_FORMAT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_TOTAL_ROWS = 500_000
    private const val MAX_VALUE_CHARS = 1_000_000

    fun encodeSnapshot(snapshot: DatabaseSnapshot): String = buildJsonObject {
        put("format", DATA_FORMAT)
        put("formatVersion", DATA_FORMAT_VERSION)
        put("schemaVersion", snapshot.schemaVersion)
        put("tables", buildJsonObject {
            snapshot.tables.forEach { (table, data) ->
                put(table, buildJsonObject {
                    put("columns", buildJsonArray { data.columns.forEach { add(JsonPrimitive(it)) } })
                    put("rows", buildJsonArray {
                        data.rows.forEach { row ->
                            add(buildJsonObject {
                                data.columns.forEach { column ->
                                    val value = row[column]
                                    put(column, if (value == null) JsonNull else JsonPrimitive(value))
                                }
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decodeSnapshot(text: String): DatabaseSnapshot {
        val root = parseObject(text, "datos")
        val format = root.string("format") ?: error("La copia no contiene un formato de datos reconocible.")
        require(format == DATA_FORMAT) { "El contenido de la copia no es un respaldo de NexoNote." }
        val version = root.int("formatVersion") ?: 0
        require(version in 1..DATA_FORMAT_VERSION) { "La versión interna de los datos no es compatible." }
        val schema = root.int("schemaVersion") ?: error("La copia no indica la versión del esquema.")
        val tablesElement = root["tables"] as? JsonObject ?: error("La copia no contiene tablas de datos.")
        var totalRows = 0
        val tables = tablesElement.mapValues { (_, value) ->
            val table = value.jsonObject
            val columns = (table["columns"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: error("Una tabla de la copia no tiene columnas.")
            require(columns.isNotEmpty() && columns.size <= 128) { "Una tabla de la copia tiene columnas no válidas." }
            require(columns.distinct().size == columns.size) { "Una tabla de la copia repite columnas." }
            val rows = (table["rows"] as? JsonArray)?.map { rowElement ->
                totalRows++
                if (totalRows > MAX_TOTAL_ROWS) error("La copia contiene demasiadas filas.")
                val row = rowElement.jsonObject
                columns.associateWith { column ->
                    val element = row[column]
                    when (element) {
                        null, JsonNull -> null
                        else -> {
                            val value = element.jsonPrimitive.contentOrNull
                            require(value == null || value.length <= MAX_VALUE_CHARS) {
                                "La copia contiene un valor demasiado largo."
                            }
                            value
                        }
                    }
                }
            } ?: error("Una tabla de la copia no contiene filas.")
            TableSnapshot(columns, rows)
        }
        return DatabaseSnapshot(schema, tables)
    }

    fun encodeSettings(values: Map<String, String?>): String = buildJsonObject {
        values.forEach { (key, value) -> put(key, value?.let(::JsonPrimitive) ?: JsonNull) }
    }.toString()

    fun decodeSettings(text: String): Map<String, String?> {
        val root = parseObject(text, "ajustes")
        return root.mapValues { (_, value) -> if (value is JsonNull) null else value.jsonPrimitive.contentOrNull }
    }

    fun encodeManifest(manifest: BackupManifest): String = buildJsonObject {
        put("format", manifest.format)
        put("formatVersion", manifest.formatVersion)
        put("schemaVersion", manifest.schemaVersion)
        put("createdAt", manifest.createdAt)
        put("appVersion", manifest.appVersion)
        put("encrypted", manifest.encrypted)
        put("checksumSha256", manifest.checksumSha256)
        put("payloadBytes", manifest.payloadBytes)
        put("attachments", manifest.attachments)
        put("counts", buildJsonObject { manifest.counts.forEach { (k, v) -> put(k, v) } })
        manifest.kdf?.let { put("kdf", it) }
        manifest.iterations?.let { put("iterations", it) }
        manifest.salt?.let { put("salt", it) }
        manifest.iv?.let { put("iv", it) }
    }.toString()

    fun decodeManifest(text: String): BackupManifest {
        val root = parseObject(text, "manifiesto")
        val format = root.string("format") ?: error("El archivo no es una copia de NexoNote.")
        require(format == BACKUP_FORMAT) { "El archivo no es una copia de NexoNote." }
        val counts = (root["counts"] as? JsonObject)?.mapValues { (_, v) -> v.jsonPrimitive.intOrNull ?: 0 }.orEmpty()
        return BackupManifest(
            format = format,
            formatVersion = root.int("formatVersion") ?: 0,
            schemaVersion = root.int("schemaVersion") ?: 0,
            createdAt = root.long("createdAt") ?: 0L,
            appVersion = root.string("appVersion").orEmpty(),
            encrypted = root.boolean("encrypted") ?: false,
            checksumSha256 = root.string("checksumSha256").orEmpty(),
            payloadBytes = root.long("payloadBytes") ?: 0L,
            counts = counts,
            attachments = root.int("attachments") ?: 0,
            kdf = root.string("kdf"),
            iterations = root.int("iterations"),
            salt = root.string("salt"),
            iv = root.string("iv"),
        )
    }

    fun encodeHistory(entries: List<BackupHistoryEntry>): String = buildJsonObject {
        put("format", HISTORY_FORMAT)
        put("version", HISTORY_FORMAT_VERSION)
        put("entries", buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("id", entry.id)
                    put("fileName", entry.fileName)
                    put("createdAt", entry.createdAt)
                    put("sizeBytes", entry.sizeBytes)
                    put("encrypted", entry.encrypted)
                    put("status", entry.status.name)
                    put("location", entry.location.name)
                    entry.managedPath?.let { put("managedPath", it) }
                    entry.uri?.let { put("uri", it) }
                    entry.message?.let { put("message", it) }
                })
            }
        })
    }.toString()

    fun decodeHistory(text: String): List<BackupHistoryEntry> {
        if (text.isBlank()) return emptyList()
        val root = parseObject(text, "historial")
        require(root.string("format") == HISTORY_FORMAT) { "El historial de copias no es válido." }
        val entries = root["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                BackupHistoryEntry(
                    id = item.string("id") ?: return@runCatching null,
                    fileName = item.string("fileName").orEmpty(),
                    createdAt = item.long("createdAt") ?: 0L,
                    sizeBytes = item.long("sizeBytes") ?: 0L,
                    encrypted = item.boolean("encrypted") ?: false,
                    status = BackupStatus.entries.firstOrNull { it.name == item.string("status") }
                        ?: BackupStatus.OK,
                    location = BackupLocation.entries.firstOrNull { it.name == item.string("location") }
                        ?: BackupLocation.MANAGED,
                    managedPath = item.string("managedPath"),
                    uri = item.string("uri"),
                    message = item.string("message"),
                )
            }.getOrNull()
        }
    }

    private fun parseObject(text: String, label: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) {
        error("El $label de la copia no tiene un formato JSON válido.")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
