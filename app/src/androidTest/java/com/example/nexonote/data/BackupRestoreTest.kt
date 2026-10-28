package com.example.nexonote.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.LocalBackupRepository
import com.example.nexonote.data.repository.LocalNoteRepository
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.data.transfer.BackupArchive
import com.example.nexonote.data.transfer.BackupService
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.data.transfer.RestoreService
import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.ColumnInput
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.model.RestoreMode
import com.example.nexonote.domain.model.SubtaskDraft
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.TableQuery
import com.example.nexonote.domain.model.TaskInput
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.BackupRepository
import com.example.nexonote.testing.FakeBackupHistoryStore
import com.example.nexonote.testing.FakeReminderScheduler
import com.example.nexonote.testing.FakeSettingsRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: NexoDatabase
    private lateinit var settings: FakeSettingsRepository
    private lateinit var repository: LocalBackupRepository
    private lateinit var history: FakeBackupHistoryStore
    private lateinit var files: FileGateway
    private lateinit var backupService: BackupService
    private lateinit var restoreService: RestoreService
    private lateinit var notes: LocalNoteRepository
    private lateinit var tasks: LocalTaskRepository
    private lateinit var tables: LocalTableRepository

    private data class Seed(
        val noteId: Long,
        val taskId: Long,
        val tableId: Long,
        val quantityColumnId: Long,
        val rowId: Long,
    )

    @Before fun setup() {
        File(context.filesDir, "backups").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).build()
        settings = FakeSettingsRepository()
        repository = LocalBackupRepository(database, settings)
        history = FakeBackupHistoryStore().apply {
            managedDirectory = File(context.filesDir, "backups")
        }
        files = FileGateway(context)
        backupService = BackupService(context, repository, history, files)
        restoreService = RestoreService(context, repository, files)
        notes = LocalNoteRepository(database)
        tasks = LocalTaskRepository(database, FakeReminderScheduler())
        tables = LocalTableRepository(database)
    }

    @After fun teardown() {
        database.close()
        File(context.filesDir, "backups").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        context.cacheDir.listFiles()?.filter {
            it.name.startsWith("restore-") || it.name.startsWith("backup-") ||
                it.name.startsWith("attachments-")
        }?.forEach { it.deleteRecursively() }
    }

    @Test fun backupAndReplaceRestorePreservesIdsRelationsAndSettings() = runBlocking {
        val seed = seedData()
        settings.setThemeMode(ThemeMode.DARK)
        settings.setAccentColor(AccentColor.TEAL)
        val entry = backupService.createManaged(null)
        assertTrue(entry.sizeBytes > 0)
        assertTrue(entry.fileName.endsWith(".nexobackup"))

        // Mutate, delete and add unrelated data after the snapshot was taken.
        val extraNote = notes.save(null, noteInput("Nota posterior"))
        notes.save(seed.noteId, noteInput("Título cambiado"))
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setAccentColor(AccentColor.RED)
        val backupFile = managedFile(entry)

        val preview = restoreService.inspect(Uri.fromFile(backupFile))
        assertTrue(preview.compatible)
        assertTrue(preview.problems.isEmpty())
        assertFalse(preview.manifest.encrypted)
        assertEquals(1, preview.counts["notes"])

        val result = restoreService.restore(Uri.fromFile(backupFile), null, RestoreMode.REPLACE)
        assertEquals(RestoreMode.REPLACE, result.mode)
        assertTrue(result.restoredRows.values.sum() > 0)

        val note = notes.observeNote(seed.noteId).first()
        assertNotNull(note)
        assertEquals("Reunión café ☕", note!!.title)
        assertEquals("Contenido con ñ y 🎉", note.content)
        assertTrue(note.labels.contains("ideas"))
        assertEquals(2, note.checklist.size)
        assertNull(notes.observeNote(extraNote).first())

        val task = tasks.findTask(seed.taskId)
        assertNotNull(task)
        assertEquals(2, task!!.subtasks.size)
        assertTrue(task.labels.contains("trabajo"))

        val row = tables.findRow(seed.rowId)
        assertNotNull(row)
        assertEquals("7", row!!.cells[seed.quantityColumnId])

        assertEquals("Soporte", settings.displayName.first())
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(AccentColor.TEAL, settings.accentColor.first())
    }

    @Test fun encryptedBackupRejectsWrongPasswordAndRestoresWithCorrectOne() = runBlocking {
        val seed = seedData()
        val password = "secreto-123".toCharArray()
        val entry = backupService.createManaged(password)
        assertTrue(entry.encrypted)
        val uri = Uri.fromFile(managedFile(entry))

        try {
            restoreService.inspect(uri, "incorrecta".toCharArray())
            fail("A wrong password must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("contraseña", ignoreCase = true))
        }
        // A wrong password must not touch the data.
        assertEquals("Reunión café ☕", notes.observeNote(seed.noteId).first()!!.title)

        val preview = restoreService.inspect(uri, password.copyOf())
        assertTrue(preview.compatible)
        assertTrue(preview.manifest.encrypted)
        assertNotNull(preview.manifest.salt)
        assertNotNull(preview.manifest.iv)

        notes.save(seed.noteId, noteInput("Cambiada tras la copia"))
        restoreService.restore(uri, password.copyOf(), RestoreMode.REPLACE)
        assertEquals("Reunión café ☕", notes.observeNote(seed.noteId).first()!!.title)
        password.fill('\u0000')
    }

    @Test fun corruptedBackupIsRejectedWithoutDataLoss() = runBlocking {
        val seed = seedData()
        val entry = backupService.createManaged(null)
        val original = managedFile(entry)
        val corrupt = File(context.cacheDir, "corrupt-${System.nanoTime()}.nexobackup")
        val bytes = original.readBytes()
        val index = bytes.size / 2
        bytes[index] = (bytes[index].toInt() xor 0xFF).toByte()
        corrupt.writeBytes(bytes)

        try {
            restoreService.restore(Uri.fromFile(corrupt), null, RestoreMode.REPLACE)
            fail("A corrupted backup must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.isNotBlank())
        }
        assertEquals("Reunión café ☕", notes.observeNote(seed.noteId).first()!!.title)
        assertEquals(1, notes.notes.first().size)
        corrupt.delete()
        Unit
    }

    @Test fun incompatibleSchemaIsRejectedAndReported() = runBlocking {
        val seed = seedData()
        val snapshot = repository.snapshot()
        val old = snapshot.copy(schemaVersion = snapshot.schemaVersion - 1)
        val file = File(context.cacheDir, "old-schema-${System.nanoTime()}.nexobackup")
        val workDir = File(context.cacheDir, "old-work-${System.nanoTime()}")
        file.outputStream().use { output ->
            BackupArchive.write(
                out = output,
                snapshot = old,
                settings = repository.readSettings(),
                password = null,
                attachments = emptyList(),
                appVersion = "0.9",
                createdAt = 1_700_000_000_000,
                workDir = workDir,
            )
        }
        workDir.deleteRecursively()

        val preview = restoreService.inspect(Uri.fromFile(file))
        assertFalse(preview.compatible)
        assertTrue(preview.problems.any { it.contains("esquema") })

        try {
            restoreService.restore(Uri.fromFile(file), null, RestoreMode.REPLACE)
            fail("An incompatible schema must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("versión", ignoreCase = true) ||
                error.message!!.contains("esquema", ignoreCase = true))
        }
        assertEquals("Reunión café ☕", notes.observeNote(seed.noteId).first()!!.title)
        file.delete()
        Unit
    }

    @Test fun mergeWithIdConflictsIsRejectedWithoutChanges() = runBlocking {
        val seed = seedData()
        val entry = backupService.createManaged(null)
        val extra = notes.save(null, noteInput("Solo en el dispositivo"))
        val before = notes.notes.first().map { it.id }.toSet()

        try {
            restoreService.restore(Uri.fromFile(managedFile(entry)), null, RestoreMode.MERGE)
            fail("A merge with existing identifiers must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("identificadores"))
        }
        assertEquals(before, notes.notes.first().map { it.id }.toSet())
        assertNotNull(notes.observeNote(extra).first())
        assertNotNull(notes.observeNote(seed.noteId).first())
    }

    @Test fun failedDatabaseRestoreRollsBackAttachments() = runBlocking {
        val attachments = File(context.filesDir, "attachments").apply { mkdirs() }
        File(attachments, "viejo.txt").writeText("contenido anterior")
        val entry = backupService.createManaged(null)
        assertTrue(entry.sizeBytes > 0)

        attachments.deleteRecursively()
        attachments.mkdirs()
        File(attachments, "nuevo.txt").writeText("contenido actual")

        val failing = object : BackupRepository by repository {
            override suspend fun restore(
                snapshot: DatabaseSnapshot,
                mode: RestoreMode,
            ): Map<String, Int> = throw IllegalStateException("fallo simulado")
        }
        val service = RestoreService(context, failing, files)
        try {
            service.restore(Uri.fromFile(managedFile(entry)), null, RestoreMode.REPLACE)
            fail("The simulated failure must propagate")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("simulado"))
        }

        val current = File(attachments, "nuevo.txt")
        assertTrue(current.isFile)
        assertEquals("contenido actual", current.readText())
        assertFalse(File(attachments, "viejo.txt").exists())
    }

    private suspend fun seedData(): Seed {
        val noteId = notes.save(
            id = null,
            input = NoteInput(
                title = "Reunión café ☕",
                content = "Contenido con ñ y 🎉",
                type = NoteType.CHECKLIST,
                color = NoteColor.BLUE,
                pinned = true,
                favorite = true,
                checklist = listOf(
                    com.example.nexonote.domain.model.ChecklistDraft("Primero", true),
                    com.example.nexonote.domain.model.ChecklistDraft("Segundo", false),
                ),
                labels = listOf("ideas", "trabajo"),
            ),
        )
        val taskId = tasks.save(
            id = null,
            input = TaskInput(
                title = "Preparar demo",
                description = "Con subtareas",
                labels = listOf("trabajo"),
                subtasks = listOf(SubtaskDraft("Guion", true), SubtaskDraft("Ensayar", false)),
            ),
        )
        val tableId = tables.createTable(TableInput(name = "Inventario"))
        val quantityColumn = tables.addColumn(tableId, ColumnInput("Cantidad", ColumnType.INTEGER))
        val rowId = tables.createRow(tableId)
        tables.updateCell(rowId, quantityColumn, "7")
        return Seed(noteId, taskId, tableId, quantityColumn, rowId)
    }

    private fun noteInput(title: String) = NoteInput(
        title = title,
        content = "Texto",
        type = NoteType.TEXT,
        color = NoteColor.DEFAULT,
        pinned = false,
        favorite = false,
    )

    private fun managedFile(entry: BackupHistoryEntry): File =
        File(File(context.filesDir, "backups"), entry.fileName)
}
