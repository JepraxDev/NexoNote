package com.example.nexonote.data.transfer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the storage access framework and the file provider. Private paths are never exposed:
 * exported/shared files live in a cache subdirectory served by [FileProvider] with temporary
 * read grants, while backups are written to the URI chosen by the user.
 */
class FileGateway(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun <T> write(uri: Uri, action: suspend (OutputStream) -> T): T = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("No se pudo abrir el archivo de destino para escribir.")
        output.buffered().use { action(it) }
    }

    fun openInput(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw IllegalArgumentException("No se pudo leer el archivo seleccionado.")

    fun displayName(uri: Uri): String? {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun size(uri: Uri): Long? {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        }
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { return it.length }
        }
        return null
    }

    fun exists(uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    fun takePersistablePermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /**
     * Creates a new file inside a tree URI chosen with the system folder picker. Used by automatic
     * backups because WorkManager runs with no UI and cannot ask the user for a location.
     */
    fun createDocument(treeUri: Uri, mimeType: String, displayName: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
            ?: throw IllegalStateException("No se pudo crear el archivo en la carpeta elegida.")
    }

    /** Friendly description of a SAF document or tree URI, safe to show in the UI. */
    fun describe(uri: Uri): String = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfter(':')
                .ifBlank { "Almacenamiento elegido" }
        } else {
            displayName(uri) ?: "Documento elegido"
        }
    }.getOrDefault("Documento elegido")

    fun createExportFile(fileName: String): File {
        val directory = File(context.cacheDir, "exports")
            .apply { mkdirs() }
        cleanupOldExports(directory)
        return File(directory, fileName)
    }

    fun shareIntent(file: File, mimeType: String, title: String = "Compartir con"): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(title, uri)
        }
    }

    fun cacheFile(prefix: String, extension: String): File {
        val directory = File(context.cacheDir, "work").apply { mkdirs() }
        return File(directory, "$prefix-${UUID.randomUUID()}.$extension")
    }

    private fun cleanupOldExports(directory: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }
}
