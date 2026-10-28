package com.example.nexonote.domain.model

/** One table inside a backup payload. Values are stored as text exactly like SQLite returns them. */
data class TableSnapshot(
    val columns: List<String>,
    val rows: List<Map<String, String?>>,
)

data class DatabaseSnapshot(
    val schemaVersion: Int,
    val tables: Map<String, TableSnapshot>,
) {
    val counts: Map<String, Int> get() = tables.mapValues { it.value.rows.size }
    val totalRows: Int get() = tables.values.sumOf { it.rows.size }
}

data class BackupPayload(
    val data: DatabaseSnapshot,
    val settings: Map<String, String?>,
)

/** Authenticated-encryption parameters stored in clear text inside the backup manifest. */
class CryptoParameters(
    val kdf: String,
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray,
)
