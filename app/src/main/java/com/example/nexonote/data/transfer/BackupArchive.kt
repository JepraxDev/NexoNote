package com.example.nexonote.data.transfer

import com.example.nexonote.domain.model.BACKUP_FORMAT
import com.example.nexonote.domain.model.BACKUP_FORMAT_VERSION
import com.example.nexonote.domain.model.BackupManifest
import com.example.nexonote.domain.model.CryptoParameters
import com.example.nexonote.domain.model.DatabaseSnapshot
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupAttachment(val name: String, val file: File)

data class BackupContents(
    val manifest: BackupManifest,
    val snapshot: DatabaseSnapshot,
    val settings: Map<String, String?>,
    val attachments: List<BackupAttachment>,
    val workDir: File,
)

/**
 * Versioned ZIP container:
 *
 * ```
 * manifest.json          plain text: format/version/schema/date/checksum/counts/crypto params
 * payload.zip            deflated ZIP with data.json, settings.json and attachments/
 * payload.enc            same ZIP encrypted with AES-256-GCM (only when a password is used)
 * ```
 *
 * The manifest is intentionally readable without the password so a restore screen can show the
 * date, schema and size before asking for the password. Integrity is always verified by SHA-256
 * and, when encrypted, by the GCM authentication tag.
 */
object BackupArchive {
    private const val MAX_MANIFEST_BYTES = 512L * 1024L
    private const val MAX_PAYLOAD_BYTES = 512L * 1024 * 1024
    private const val MAX_DATA_BYTES = 320L * 1024 * 1024
    private const val MAX_SETTINGS_BYTES = 256L * 1024
    private const val MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024
    private const val MAX_ATTACHMENT_TOTAL = 256L * 1024 * 1024
    private const val MAX_ATTACHMENTS = 500
    private const val MAX_ENTRIES = 600

    fun write(
        out: OutputStream,
        snapshot: DatabaseSnapshot,
        settings: Map<String, String?>,
        password: CharArray?,
        attachments: List<BackupAttachment>,
        appVersion: String,
        createdAt: Long,
        workDir: File,
    ): BackupManifest {
        require(attachments.size <= MAX_ATTACHMENTS) { "Hay demasiados archivos adjuntos para una copia." }
        val attachmentTotal = attachments.sumOf { it.file.length() }
        require(attachmentTotal <= MAX_ATTACHMENT_TOTAL) { "Los archivos adjuntos superan el tamaño máximo." }
        workDir.mkdirs()
        val dataFile = File(workDir, "data.json")
        val settingsFile = File(workDir, "settings.json")
        dataFile.writeText(BackupJson.encodeSnapshot(snapshot), Charsets.UTF_8)
        settingsFile.writeText(BackupJson.encodeSettings(settings), Charsets.UTF_8)

        val plainPayload = File(workDir, "payload.zip")
        ZipOutputStream(BufferedOutputStream(plainPayload.outputStream())).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            dataFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("settings.json"))
            settingsFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            attachments.forEach { attachment ->
                val name = safeAttachmentName(attachment.name)
                require(name != null) { "El nombre de un archivo adjunto no es válido." }
                zip.putNextEntry(ZipEntry("attachments/$name"))
                attachment.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        var payloadFile = plainPayload
        var parameters: CryptoParameters? = null
        if (password != null) {
            parameters = BackupCrypto.newParameters()
            val encrypted = File(workDir, "payload.enc")
            plainPayload.inputStream().use { input ->
                encrypted.outputStream().use { output ->
                    BackupCrypto.encrypt(input, output, password, parameters)
                }
            }
            payloadFile = encrypted
        }

        val checksum = sha256(payloadFile)
        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = snapshot.schemaVersion,
            createdAt = createdAt,
            appVersion = appVersion,
            encrypted = password != null,
            checksumSha256 = checksum,
            payloadBytes = payloadFile.length(),
            counts = snapshot.counts,
            attachments = attachments.size,
            kdf = parameters?.kdf,
            iterations = parameters?.iterations,
            salt = parameters?.let { toHex(it.salt) },
            iv = parameters?.let { toHex(it.iv) },
        )

        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(BackupJson.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(if (parameters != null) "payload.enc" else "payload.zip"))
            payloadFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return manifest
    }

    /** Reads only the plain manifest and verifies payload size/checksum, without needing a password. */
    fun readManifest(input: InputStream, workDir: File): BackupManifest {
        val outer = try {
            readOuter(input, workDir)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "No se pudo leer la copia de seguridad. Comprueba que el archivo esté completo y no esté dañado.",
                error,
            )
        }
        return outer.manifest
    }

    /**
     * Validates the container and extracts it into [workDir]. Throws [IllegalArgumentException]
     * with a user-facing message for corrupt, incompatible or malicious files.
     */
    fun read(
        input: InputStream,
        password: CharArray?,
        workDir: File,
    ): BackupContents {
        try {
            val outer = readOuter(input, workDir)
            val manifest = outer.manifest
            val payload = outer.payload
            val innerPayload = if (manifest.encrypted) {
                val actualPassword = password?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Esta copia está protegida con contraseña.")
                val kdf = manifest.kdf
                    ?: throw IllegalArgumentException("La copia no indica el método de cifrado.")
                val iterations = manifest.iterations
                    ?: throw IllegalArgumentException("La copia no indica las iteraciones de cifrado.")
                val salt = manifest.salt?.let(::fromHex)
                    ?: throw IllegalArgumentException("La copia no indica la sal de cifrado.")
                val iv = manifest.iv?.let(::fromHex)
                    ?: throw IllegalArgumentException("La copia no indica el vector de cifrado.")
                val parameters = CryptoParameters(kdf, iterations, salt, iv)
                val decrypted = File(workDir, "payload.decrypted.zip")
                payload.inputStream().use { encrypted ->
                    decrypted.outputStream().use { output ->
                        BackupCrypto.decrypt(encrypted, output, actualPassword, parameters)
                    }
                }
                decrypted
            } else {
                payload
            }

            val extracted = File(workDir, "extracted").apply { mkdirs() }
            extractInner(innerPayload, extracted)
            val dataFile = File(extracted, "data.json")
            require(dataFile.isFile) { "La copia no contiene los datos de la aplicación." }
            require(dataFile.length() <= MAX_DATA_BYTES) { "Los datos de la copia son demasiado grandes." }
            val snapshot = BackupJson.decodeSnapshot(dataFile.readText(Charsets.UTF_8))
            require(snapshot.schemaVersion == manifest.schemaVersion) {
                "La versión del esquema no coincide con el manifiesto."
            }
            val settingsFile = File(extracted, "settings.json")
            val settings = if (settingsFile.isFile && settingsFile.length() <= MAX_SETTINGS_BYTES) {
                BackupJson.decodeSettings(settingsFile.readText(Charsets.UTF_8))
            } else {
                emptyMap()
            }
            val attachmentsDir = File(extracted, "attachments")
            val attachments = if (attachmentsDir.isDirectory) {
                attachmentsDir.walkTopDown()
                    .filter { it.isFile }
                    .map { file ->
                        BackupAttachment(
                            name = file.relativeTo(attachmentsDir).invariantSeparatorsPath,
                            file = file,
                        )
                    }
                    .sortedBy { it.name }
                    .toList()
            } else {
                emptyList()
            }
            return BackupContents(manifest, snapshot, settings, attachments, workDir)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "No se pudo leer la copia de seguridad. Comprueba que el archivo esté completo y no esté dañado.",
                error,
            )
        }
    }

    private data class OuterContainer(
        val manifest: BackupManifest,
        val payload: File,
    )

    private fun readOuter(input: InputStream, workDir: File): OuterContainer {
        workDir.mkdirs()
        val manifestFile = File(workDir, "manifest.json")
        var payloadFile: File? = null
        var payloadEncrypted = false
        var entries = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                entries++
                if (entries > MAX_ENTRIES) error("La copia contiene demasiadas entradas.")
                when (entry.name) {
                    "manifest.json" -> copyLimited(zip, manifestFile, MAX_MANIFEST_BYTES, "manifiesto")
                    "payload.zip", "payload.enc" -> {
                        require(payloadFile == null) { "La copia contiene más de un contenido principal." }
                        payloadFile = File(workDir, if (entry.name.endsWith(".enc")) "payload.enc" else "payload.zip")
                        payloadEncrypted = entry.name.endsWith(".enc")
                        copyLimited(zip, payloadFile!!, MAX_PAYLOAD_BYTES, "contenido")
                    }
                    else -> error("La copia contiene una entrada no permitida: ${entry.name.take(80)}")
                }
                zip.closeEntry()
            }
        }
        require(manifestFile.isFile) { "El archivo no contiene un manifiesto de NexoNote." }
        val payload = payloadFile ?: error("La copia no contiene datos.")
        require(manifestFile.length() <= MAX_MANIFEST_BYTES) { "El manifiesto de la copia es demasiado grande." }
        val manifest = BackupJson.decodeManifest(manifestFile.readText(Charsets.UTF_8))
        require(manifest.format == BACKUP_FORMAT) { "El archivo no es una copia de NexoNote." }
        require(manifest.formatVersion in 1..BACKUP_FORMAT_VERSION) {
            "La copia usa una versión de formato que esta aplicación no puede abrir."
        }
        require(manifest.payloadBytes == payload.length()) {
            "La copia está incompleta: el tamaño del contenido no coincide con el manifiesto."
        }
        require(manifest.checksumSha256.isNotBlank()) { "La copia no incluye una comprobación de integridad." }
        require(manifest.checksumSha256.equals(sha256(payload), ignoreCase = true)) {
            "La copia está dañada: la comprobación de integridad no coincide."
        }
        require(manifest.encrypted == payloadEncrypted) {
            "El cifrado de la copia no coincide con su manifiesto."
        }
        return OuterContainer(manifest, payload)
    }

    fun deleteWorkDir(workDir: File) {
        runCatching { workDir.deleteRecursively() }
    }

    private fun extractInner(payload: File, target: File) {
        var attachmentBytes = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(payload.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                entries++
                if (entries > MAX_ENTRIES) error("La copia contiene demasiadas entradas internas.")
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith("/") && name.split('/').none { it == ".." }) {
                    "La copia contiene una ruta no permitida."
                }
                when {
                    name == "data.json" ->
                        copyLimited(zip, File(target, "data.json"), MAX_DATA_BYTES, "datos")
                    name == "settings.json" ->
                        copyLimited(zip, File(target, "settings.json"), MAX_SETTINGS_BYTES, "ajustes")
                    name.startsWith("attachments/") -> {
                        val relative = safeAttachmentName(name.removePrefix("attachments/"))
                            ?: error("La copia contiene un nombre de adjunto no válido.")
                        val destination = File(File(target, "attachments").apply { mkdirs() }, relative)
                        require(destination.canonicalPath.startsWith(target.canonicalPath)) {
                            "La copia contiene una ruta no permitida."
                        }
                        attachmentBytes += copyLimited(zip, destination, MAX_ATTACHMENT_BYTES, "adjunto")
                        require(attachmentBytes <= MAX_ATTACHMENT_TOTAL) {
                            "Los adjuntos de la copia superan el tamaño máximo."
                        }
                    }
                    else -> Unit
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyLimited(zip: ZipInputStream, target: File, limit: Long, label: String): Long {
        target.parentFile?.mkdirs()
        var total = 0L
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = zip.read(buffer)
                if (read <= 0) break
                total += read
                if (total > limit) error("El $label de la copia supera el tamaño permitido.")
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun safeAttachmentName(name: String): String? {
        val normalized = name.replace('\\', '/').trim()
        if (normalized.isEmpty() || normalized.length > 200) return null
        if (normalized.startsWith("/") || normalized.contains("..")) return null
        if (normalized.any { it.isISOControl() }) return null
        return normalized
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
    }

    fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }

    fun fromHex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]+"))) {
            "Los parámetros de la copia no son válidos."
        }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
