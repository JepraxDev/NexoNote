package com.example.nexonote.data.transfer

import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.TableSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveTest {
    private val snapshot = DatabaseSnapshot(
        schemaVersion = 8,
        tables = mapOf(
            "entries" to TableSnapshot(
                columns = listOf("id", "type", "title"),
                rows = listOf(
                    mapOf("id" to "1", "type" to "NOTE", "title" to "Café ☕"),
                    mapOf("id" to "2", "type" to "TASK", "title" to "Cita 10:30"),
                ),
            ),
            "note_labels" to TableSnapshot(
                columns = listOf("note_id", "label"),
                rows = listOf(mapOf("note_id" to "1", "label" to "ideas")),
            ),
        ),
    )
    private val settings = mapOf("displayName" to "Soporte", "themeMode" to "DARK")

    @Test fun writesAndReadsVersionedContainerWithRelations() {
        val workDir = tempDir("archive-write")
        val attachment = File(workDir, "foto.txt").apply { writeText("adjunto ✓") }
        val bytes = ByteArrayOutputStream()
        try {
            val manifest = BackupArchive.write(
                out = bytes,
                snapshot = snapshot,
                settings = settings,
                password = null,
                attachments = listOf(BackupAttachment("fotos/foto.txt", attachment)),
                appVersion = "1.0-test",
                createdAt = 1_700_000_000_000,
                workDir = workDir,
            )
            assertFalse(manifest.encrypted)
            assertEquals(8, manifest.schemaVersion)
            assertEquals(2, manifest.counts["entries"])
            assertEquals(1, manifest.attachments)
            assertEquals(64, manifest.checksumSha256.length)
        } finally {
            workDir.deleteRecursively()
        }

        val readDir = tempDir("archive-read")
        try {
            val contents = BackupArchive.read(ByteArrayInputStream(bytes.toByteArray()), null, readDir)
            assertEquals(8, contents.manifest.schemaVersion)
            assertEquals(snapshot.tables["entries"]!!.rows, contents.snapshot.tables["entries"]!!.rows)
            assertEquals(snapshot.tables["note_labels"]!!.rows, contents.snapshot.tables["note_labels"]!!.rows)
            assertEquals(settings, contents.settings)
            assertEquals(1, contents.attachments.size)
            assertEquals("fotos/foto.txt", contents.attachments.single().name)
            assertEquals("adjunto ✓", contents.attachments.single().file.readText())
        } finally {
            readDir.deleteRecursively()
        }
    }

    @Test fun manifestIsReadableWithoutPasswordAndChecksumMatches() {
        val workDir = tempDir("manifest")
        val bytes = ByteArrayOutputStream()
        try {
            val written = BackupArchive.write(
                out = bytes,
                snapshot = snapshot,
                settings = emptyMap(),
                password = null,
                attachments = emptyList(),
                appVersion = "1.0-test",
                createdAt = 42,
                workDir = workDir,
            )
            val manifest = BackupArchive.readManifest(
                ByteArrayInputStream(bytes.toByteArray()),
                File(workDir, "manifest-check").apply { mkdirs() },
            )
            assertEquals(written.checksumSha256, manifest.checksumSha256)
            assertEquals(written.payloadBytes, manifest.payloadBytes)
            assertEquals(42, manifest.createdAt)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test fun corruptedPayloadIsRejectedBeforeRestore() {
        val workDir = tempDir("corrupt")
        val bytes = ByteArrayOutputStream()
        try {
            BackupArchive.write(
                out = bytes,
                snapshot = snapshot,
                settings = emptyMap(),
                password = null,
                attachments = emptyList(),
                appVersion = "1.0-test",
                createdAt = 42,
                workDir = workDir,
            )
        } finally {
            workDir.deleteRecursively()
        }
        val corrupt = bytes.toByteArray().also { data ->
            // Flip a byte in the middle of the file, which belongs to the payload entry.
            val index = data.size / 2
            data[index] = (data[index].toInt() xor 0xFF).toByte()
        }
        val readDir = tempDir("corrupt-read")
        try {
            BackupArchive.read(ByteArrayInputStream(corrupt), null, readDir)
            fail("A corrupted backup must not be accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.isNotBlank())
        } finally {
            readDir.deleteRecursively()
        }
    }

    @Test fun plainZipIsNotAcceptedAsBackup() {
        val dir = tempDir("not-backup")
        try {
            val bytes = ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("hola.txt"))
                zip.write("no soy una copia".toByteArray())
                zip.closeEntry()
            }
            try {
                BackupArchive.read(ByteArrayInputStream(bytes.toByteArray()), null, dir)
                fail("Unexpected backup accepted")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("manifiesto", ignoreCase = true) ||
                    error.message!!.contains("copia", ignoreCase = true))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tempDir(prefix: String): File =
        File.createTempFile(prefix, "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
}
