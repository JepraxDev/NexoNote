package com.example.nexonote.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.nexonote.data.transfer.BackupJson
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.BackupLocation
import com.example.nexonote.domain.repository.BackupHistoryStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * History persisted as a small JSON file. Managed backups live in `filesDir/backups`; external
 * files stay wherever the user chose them. External entries are verified on read and never
 * reported as available unless the URI can actually be opened.
 */
class FileBackupHistoryStore(
    context: Context,
    private val files: FileGateway,
) : BackupHistoryStore {
    private val directory = File(context.filesDir, "backups").apply { mkdirs() }
    private val historyFile = File(directory, "history.json")
    private val mutex = Mutex()

    override suspend fun entries(): List<BackupHistoryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { read().sortedByDescending { it.createdAt } }
    }

    override suspend fun add(entry: BackupHistoryEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read().filterNot { it.id == entry.id }
            write((listOf(entry) + current).take(MAX_ENTRIES))
        }
    }

    override suspend fun remove(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read()
            val entry = current.firstOrNull { it.id == id } ?: return@withLock false
            if (entry.location == BackupLocation.MANAGED) {
                runCatching { managedFile(entry).delete() }
            }
            write(current.filterNot { it.id == id })
            true
        }
    }

    override suspend fun update(entry: BackupHistoryEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read()
            write(current.map { if (it.id == entry.id) entry else it })
        }
    }

    override suspend fun exists(entry: BackupHistoryEntry): Boolean = withContext(Dispatchers.IO) {
        if (entry.location == BackupLocation.MANAGED) {
            managedFile(entry).isFile
        } else {
            val uri = entry.uri?.let { runCatching { it.toUri() }.getOrNull() }
            uri != null && files.exists(uri)
        }
    }

    fun managedFile(entry: BackupHistoryEntry): File {
        val name = entry.managedPath?.let { File(it).name } ?: "${entry.id}.nexobackup"
        return File(directory, name)
    }

    override fun locationFor(entry: BackupHistoryEntry): String? =
        if (entry.location == BackupLocation.MANAGED) managedFile(entry).absolutePath else entry.uri

    fun newManagedFile(id: String): File = File(directory, "$id.nexobackup")

    private fun read(): List<BackupHistoryEntry> {
        if (!historyFile.isFile) return emptyList()
        return runCatching { BackupJson.decodeHistory(historyFile.readText(Charsets.UTF_8)) }
            .getOrElse { emptyList() }
    }

    private fun write(entries: List<BackupHistoryEntry>) {
        directory.mkdirs()
        val temp = File(directory, "history.json.tmp")
        temp.writeText(BackupJson.encodeHistory(entries), Charsets.UTF_8)
        if (!temp.renameTo(historyFile)) {
            historyFile.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
