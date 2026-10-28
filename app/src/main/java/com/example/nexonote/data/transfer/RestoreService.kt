package com.example.nexonote.data.transfer

import android.content.Context
import android.net.Uri
import com.example.nexonote.domain.model.BACKUP_FORMAT_VERSION
import com.example.nexonote.domain.model.DatabaseSnapshot
import com.example.nexonote.domain.model.RestoreMode
import com.example.nexonote.domain.model.RestorePreview
import com.example.nexonote.domain.model.RestoreResult
import com.example.nexonote.domain.repository.BackupRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validates a backup before any data is touched, then restores it atomically:
 *
 * 1. container, checksum, schema and row shape are validated;
 * 2. attachments are staged next to the live directory;
 * 3. the database is replaced/merged inside one transaction;
 * 4. settings are applied and staged attachments swapped in.
 *
 * Any failure rolls back the database (Room transaction) and the attachment directory. Nothing is
 * deleted before validation succeeds.
 */
class RestoreService(
    private val context: Context,
    private val repository: BackupRepository,
    private val files: FileGateway,
) {
    suspend fun inspect(uri: Uri, password: CharArray? = null): RestorePreview = withContext(Dispatchers.IO) {
        validateSize(uri)
        val workDir = File(context.cacheDir, "restore-inspect-${UUID.randomUUID()}")
        try {
            val manifest = files.openInput(uri).use { input ->
                BackupArchive.readManifest(input, workDir)
            }
            if (manifest.encrypted && password != null && password.isNotEmpty()) {
                // Once a password is supplied, fully decrypt and validate the payload so a wrong
                // password is reported here instead of after the destructive confirmation step.
                files.openInput(uri).use { input ->
                    BackupArchive.read(input, password, File(workDir, "verified"))
                }
            }
            val problems = buildList {
                if (manifest.schemaVersion != repository.schemaVersion) {
                    add(
                        "La copia usa el esquema ${manifest.schemaVersion} y esta aplicación usa " +
                            "${repository.schemaVersion}."
                    )
                }
                if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                    add("La copia usa una versión de formato más reciente.")
                }
                if (manifest.encrypted && (password == null || password.isEmpty())) {
                    add("La copia está protegida con contraseña.")
                }
            }
            RestorePreview(
                fileName = files.displayName(uri) ?: "copia.nexobackup",
                manifest = manifest,
                counts = manifest.counts,
                attachmentCount = manifest.attachments,
                compatible = manifest.schemaVersion == repository.schemaVersion &&
                    manifest.formatVersion <= BACKUP_FORMAT_VERSION,
                problems = problems,
            )
        } finally {
            BackupArchive.deleteWorkDir(workDir)
        }
    }

    suspend fun restore(
        uri: Uri,
        password: CharArray?,
        mode: RestoreMode,
    ): RestoreResult = withContext(Dispatchers.IO) {
        validateSize(uri)
        val workDir = File(context.cacheDir, "restore-${UUID.randomUUID()}")
        val staging = File(context.cacheDir, "attachments-staging-${UUID.randomUUID()}")
        val previousAttachments = File(context.cacheDir, "attachments-previous-${UUID.randomUUID()}")
        val liveAttachments = File(context.filesDir, "attachments")
        var previousSnapshot: DatabaseSnapshot? = null
        var replacedAttachments = false
        try {
            val contents = files.openInput(uri).use { input ->
                BackupArchive.read(input, password, workDir)
            }
            require(contents.manifest.schemaVersion == repository.schemaVersion) {
                "La copia pertenece a otra versión del esquema y no se puede restaurar."
            }
            require(contents.snapshot.schemaVersion == repository.schemaVersion) {
                "Los datos de la copia no coinciden con la versión actual."
            }
            val hasAttachments = contents.attachments.isNotEmpty()
            if (hasAttachments) {
                staging.mkdirs()
                contents.attachments.forEach { attachment ->
                    val target = File(staging, attachment.name)
                    target.parentFile?.mkdirs()
                    attachment.file.copyTo(target, overwrite = true)
                }
                if (mode == RestoreMode.REPLACE) {
                    previousSnapshot = repository.snapshot()
                    moveLiveTo(liveAttachments, previousAttachments)
                    moveLiveTo(staging, liveAttachments)
                    replacedAttachments = true
                } else {
                    // Safe merge never deletes existing files; it only adds/overwrites the backup ones.
                    staging.copyRecursively(liveAttachments, overwrite = true)
                }
            }

            val rows = try {
                repository.restore(contents.snapshot, mode)
            } catch (error: Exception) {
                if (replacedAttachments) rollbackAttachments(liveAttachments, previousAttachments)
                throw error
            }
            try {
                repository.applySettings(contents.settings)
            } catch (error: Exception) {
                if (replacedAttachments) {
                    rollbackAttachments(liveAttachments, previousAttachments)
                    previousSnapshot?.let { snapshot ->
                        runCatching { repository.restore(snapshot, RestoreMode.REPLACE) }
                    }
                }
                throw error
            }
            previousAttachments.deleteRecursively()
            RestoreResult(
                restoredRows = rows,
                attachmentsRestored = contents.attachments.size,
                mode = mode,
            )
        } finally {
            staging.deleteRecursively()
            previousAttachments.deleteRecursively()
            BackupArchive.deleteWorkDir(workDir)
        }
    }

    private fun validateSize(uri: Uri) {
        val size = files.size(uri)
        require(size == null || size > 0) { "El archivo de copia está vacío." }
        require(size == null || size <= MAX_ARCHIVE_BYTES) {
            "La copia supera el tamaño máximo admitido (${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB)."
        }
        require(files.exists(uri)) { "No se pudo acceder al archivo de copia." }
    }

    private fun moveLiveTo(source: File, target: File) {
        target.deleteRecursively()
        if (!source.exists()) return
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }

    private fun rollbackAttachments(live: File, previous: File) {
        live.deleteRecursively()
        if (previous.exists()) {
            if (!previous.renameTo(live)) {
                previous.copyRecursively(live, overwrite = true)
                previous.deleteRecursively()
            }
        }
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    }
}
