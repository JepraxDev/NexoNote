package com.example.nexonote.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.nexonote.NexoNoteApplication
import com.example.nexonote.domain.model.BackupFrequency
import com.example.nexonote.domain.repository.BackupScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class WorkManagerBackupScheduler(context: Context) : BackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(frequency: BackupFrequency) {
        val days = frequency.days
        if (days == null) {
            cancel()
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "nexonote_automatic_backup"
    }
}

/**
 * Deferrable automatic backup. WorkManager may run it later than requested; the app never
 * promises an exact time. Encryption is intentionally not used here because the password would
 * have to be stored somewhere, which the backup requirements forbid.
 */
class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val container = (applicationContext as NexoNoteApplication).container
        val settings = container.backupSettingsRepository
        if (!settings.automaticEnabled.first()) {
            Result.success()
        } else {
            val frequency = settings.frequency.first()
            val days = frequency.days
            if (days == null) {
                Result.success()
            } else {
                val now = System.currentTimeMillis()
                val last = settings.lastAutomaticAt.first() ?: 0L
                val interval = TimeUnit.DAYS.toMillis(days)
                if (last > 0 && now - last < interval) {
                    Result.success()
                } else {
                    val destination = settings.destinationUri.first()
                    if (destination.isNullOrBlank()) {
                        container.backupService.recordFailure(
                            fileName = "Copia automática",
                            message = "Elige una carpeta de destino para las copias automáticas.",
                        )
                        Result.success()
                    } else {
                        try {
                            val target = container.fileGateway.createDocument(
                                treeUri = destination.toUri(),
                                mimeType = "application/octet-stream",
                                displayName = container.backupService.automaticFileName(now),
                            )
                            container.backupService.createToUri(target, password = null)
                            settings.setLastAutomaticAt(now)
                            Result.success()
                        } catch (error: SecurityException) {
                            container.backupService.recordFailure(
                                fileName = "Copia automática",
                                message = "El permiso sobre la carpeta de destino ya no es válido. " +
                                    "Vuelve a elegirla en Ajustes.",
                            )
                            Result.success()
                        } catch (error: IllegalArgumentException) {
                            container.backupService.recordFailure(
                                fileName = "Copia automática",
                                message = error.message ?: "No se pudo crear la copia automática.",
                            )
                            Result.success()
                        } catch (error: java.io.IOException) {
                            container.backupService.recordFailure(
                                fileName = "Copia automática",
                                message = "No hay espacio suficiente o la carpeta no está disponible.",
                            )
                            Result.success()
                        }
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        Result.retry()
    } catch (_: java.io.IOException) {
        Result.retry()
    } catch (_: Exception) {
        Result.retry()
    }
}
