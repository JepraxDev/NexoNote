package com.example.nexonote.data.transfer

import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.TableSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupJsonTest {
    @Test fun snapshotRoundTripsNullsAndUnicode() {
        val snapshot = DatabaseSnapshot(
            schemaVersion = 8,
            tables = mapOf(
                "notes" to TableSnapshot(
                    columns = listOf("id", "title", "content"),
                    rows = listOf(
                        mapOf("id" to "1", "title" to "Ñandú 🦜", "content" to null),
                        mapOf("id" to "2", "title" to "Línea\nnueva", "content" to "\"comillas\""),
                    ),
                ),
            ),
        )
        val encoded = BackupJson.encodeSnapshot(snapshot)
        val decoded = BackupJson.decodeSnapshot(encoded)
        assertEquals(8, decoded.schemaVersion)
        assertEquals(snapshot.tables["notes"]!!.columns, decoded.tables["notes"]!!.columns)
        assertEquals(snapshot.tables["notes"]!!.rows, decoded.tables["notes"]!!.rows)
    }

    @Test fun settingsRoundTripIncludingNulls() {
        val settings = mapOf("displayName" to "Ana", "themeMode" to null)
        assertEquals(settings, BackupJson.decodeSettings(BackupJson.encodeSettings(settings)))
    }

    @Test fun unknownDataFormatIsRejected() {
        try {
            BackupJson.decodeSnapshot("""{"format":"otra-app","formatVersion":1,"tables":{}}""")
            fail("Expected a validation error")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("NexoNote"))
        }
    }

    @Test fun duplicateColumnsAreRejected() {
        try {
            BackupJson.decodeSnapshot(
                """{"format":"nexonote-data","formatVersion":1,"schemaVersion":8,""" +
                    """"tables":{"notes":{"columns":["id","id"],"rows":[]}}}"""
            )
            fail("Expected a validation error")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("columnas", ignoreCase = true))
        }
    }

    @Test fun manifestRoundTripsCryptoMetadata() {
        val manifest = com.example.nexonote.domain.model.BackupManifest(
            schemaVersion = 8,
            createdAt = 1_700_000_000_000,
            appVersion = "1.0",
            encrypted = true,
            checksumSha256 = "abc123",
            payloadBytes = 2048,
            counts = mapOf("notes" to 1),
            attachments = 2,
            kdf = BackupCrypto.KDF_SHA256,
            iterations = BackupCrypto.ITERATIONS,
            salt = "00112233",
            iv = "44556677",
        )
        val decoded = BackupJson.decodeManifest(BackupJson.encodeManifest(manifest))
        assertEquals(manifest, decoded)
    }
}
