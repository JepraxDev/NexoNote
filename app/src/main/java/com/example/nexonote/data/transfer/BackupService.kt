package com.example.nexonote.data.transfer

import android.content.Context
import android.net.Uri
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.BackupLocation
import com.example.nexonote.domain.model.BackupManifest
import com.example.nexonote.domain.model.BackupStatus
import com.example.nexonote.domain.repository.BackupHistoryStore
import com.example.nexonote.domain.repository.BackupRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates backups as complete temporary files before touching the destination. That ordering
 * means a failed export never leaves a half-written backup, and managed backups can still be
 * listed when an external SAF write fails.
 */
class BackupService(
    private val context: Context,
    private val repository: BackupRepository,
    private val history: BackupHistoryStore,
    private val files: FileGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun createToUri(uri: Uri, password: CharArray?): BackupHistoryEntry = withContext(Dispatchers.IO) {
        val now = clock()
        val id = UUID.randomUUID().toString()
        val generated = generatedName(now)
        val display = files.displayName(uri) ?: generated
        val temp = File(context.cacheDir, "backup-$id.nexobackup")
        try {
            val manifest = build(temp, password, now)
            files.write(uri) { output ->
                temp.inputStream().use { input -> input.copyTo(output) }
            }
            val entry = BackupHistoryEntry(
                id = id,
                fileName = display,
                createdAt = now,
                sizeBytes = temp.length(),
                encrypted = manifest.encrypted,
                status = BackupStatus.OK,
                location = BackupLocation.EXTERNAL,
                uri = uri.toString(),
            )
            history.add(entry)
            entry
        } catch (error: Exception) {
            recordFailure(display, message(error), BackupLocation.EXTERNAL, uri.toString(), now)
            throw error
        } finally {
            temp.delete()
        }
    }

    suspend fun createManaged(password: CharArray?): BackupHistoryEntry = withContext(Dispatchers.IO) {
        val now = clock()
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "backups").apply { mkdirs() }
        val target = File(directory, "$id.nexobackup")
        val temp = File(context.cacheDir, "backup-$id.nexobackup")
        try {
            val manifest = build(temp, password, now)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val entry = BackupHistoryEntry(
                id = id,
                fileName = target.name,
                createdAt = now,
                sizeBytes = target.length(),
                encrypted = manifest.encrypted,
                status = BackupStatus.OK,
                location = BackupLocation.MANAGED,
                managedPath = target.name,
            )
            history.add(entry)
            entry
        } catch (error: Exception) {
            target.delete()
            recordFailure(generatedName(now), message(error), BackupLocation.MANAGED, null, now)
            throw error
        } finally {
            temp.delete()
        }
    }

    suspend fun recordFailure(
        fileName: String,
        message: String,
        location: BackupLocation = BackupLocation.EXTERNAL,
        uri: String? = null,
        at: Long = clock(),
    ) {
        history.add(
            BackupHistoryEntry(
                id = UUID.randomUUID().toString(),
                fileName = fileName.take(120),
                createdAt = at,
                sizeBytes = 0,
                encrypted = false,
                status = BackupStatus.FAILED,
                location = location,
                uri = uri,
                message = message.take(300),
            )
        )
    }

    private suspend fun build(target: File, password: CharArray?, now: Long): BackupManifest {
        val workDir = File(context.cacheDir, "backup-work-${UUID.randomUUID()}")
        return try {
            val snapshot = repository.snapshot()
            val settings = repository.readSettings()
            val attachments = attachments()
            target.outputStream().buffered().use { output ->
                BackupArchive.write(
                    out = output,
                    snapshot = snapshot,
                    settings = settings,
                    password = password,
                    attachments = attachments,
                    appVersion = appVersion(),
                    createdAt = now,
                    workDir = workDir,
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun attachments(): List<BackupAttachment> {
        val root = File(context.filesDir, "attachments")
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                BackupAttachment(
                    name = file.relativeTo(root).invariantSeparatorsPath,
                    file = file,
                )
            }
            .toList()
    }

    private fun generatedName(now: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now))
        return "NexoNote-$stamp.nexobackup"
    }

    /** Unique child name for a destination folder used by automatic backups. */
    fun automaticFileName(now: Long = clock()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))
        return "NexoNote-auto-$stamp.nexobackup"
    }

    /** Suggested name for a manual backup saved with the system file picker. */
    fun suggestedFileName(): String = generatedName(clock())

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    private fun message(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() } ?: "No se pudo crear la copia de seguridad."
}
