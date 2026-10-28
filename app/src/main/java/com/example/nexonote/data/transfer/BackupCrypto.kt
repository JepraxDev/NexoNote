package com.example.nexonote.data.transfer

import android.os.Build
import com.example.nexonote.domain.model.CryptoParameters
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption for backups (AES-256-GCM + PBKDF2).
 *
 * Android 8.0 (API 26) and newer use PBKDF2-HMAC-SHA256. API 24–25 do not provide that
 * SecretKeyFactory implementation, so the fallback is PBKDF2-HMAC-SHA1 with the same iteration
 * count. The algorithm is stored in the manifest, so a backup can always be opened on any device
 * running NexoNote, and no password is ever written to disk.
 */
object BackupCrypto {
    const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
    const val KDF_SHA1 = "PBKDF2WithHmacSHA1"
    const val ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val AES = "AES/GCM/NoPadding"

    fun newParameters(): CryptoParameters {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        return CryptoParameters(
            kdf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) KDF_SHA256 else KDF_SHA1,
            iterations = ITERATIONS,
            salt = salt,
            iv = iv,
        )
    }

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray, parameters: CryptoParameters) {
        val key = deriveKey(password, parameters)
        val cipher = Cipher.getInstance(AES).apply {
            init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, parameters.iv),
            )
        }
        CipherOutputStream(output, cipher).use { encrypted ->
            input.copyTo(encrypted, bufferSize = 64 * 1024)
        }
    }

    fun decrypt(input: InputStream, output: OutputStream, password: CharArray, parameters: CryptoParameters) {
        val key = deriveKey(password, parameters)
        val cipher = Cipher.getInstance(AES).apply {
            init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, parameters.iv),
            )
        }
        try {
            CipherInputStream(input, cipher).use { decrypted ->
                decrypted.copyTo(output, bufferSize = 64 * 1024)
            }
        } catch (error: javax.crypto.AEADBadTagException) {
            throw IllegalArgumentException("La contraseña no es correcta o la copia está dañada.", error)
        } catch (error: java.io.IOException) {
            val cause = error.cause
            if (cause is javax.crypto.AEADBadTagException || cause is javax.crypto.BadPaddingException) {
                throw IllegalArgumentException("La contraseña no es correcta o la copia está dañada.", error)
            }
            throw error
        }
    }

    fun deriveKey(password: CharArray, parameters: CryptoParameters): SecretKey {
        require(password.isNotEmpty()) { "La contraseña no puede estar vacía." }
        require(parameters.salt.size >= 8) { "Los parámetros de la copia no son válidos." }
        require(parameters.iv.size >= 12) { "Los parámetros de la copia no son válidos." }
        require(parameters.iterations in 10_000..2_000_000) { "El número de iteraciones no es válido." }
        val spec = PBEKeySpec(password, parameters.salt, parameters.iterations, KEY_BITS)
        val factory = try {
            SecretKeyFactory.getInstance(parameters.kdf)
        } catch (_: java.security.NoSuchAlgorithmException) {
            if (parameters.kdf == KDF_SHA256) SecretKeyFactory.getInstance(KDF_SHA1)
            else throw IllegalArgumentException("Este dispositivo no puede abrir la copia cifrada.")
        }
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
