package com.example.nexonote.ui.transfer

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.data.transfer.BackupService
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.BackupFrequency
import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.BackupLocation
import com.example.nexonote.domain.repository.BackupHistoryStore
import com.example.nexonote.domain.repository.BackupRepository
import com.example.nexonote.domain.repository.BackupScheduler
import com.example.nexonote.domain.repository.BackupSettingsRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val settingsLoading: Boolean = true,
    val historyLoading: Boolean = true,
    val automaticEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.MANUAL,
    val destination: String? = null,
    val destinationLabel: String? = null,
    val lastAutomaticAt: Long? = null,
    val history: List<BackupHistoryEntry> = emptyList(),
    val missing: Set<String> = emptySet(),
    val counts: Map<String, Int> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val totalRows: Int get() = counts.values.sum()
    val automaticReady: Boolean get() = automaticEnabled && frequency != BackupFrequency.MANUAL && !destination.isNullOrBlank()
    val warning: String? get() = when {
        automaticEnabled && frequency == BackupFrequency.MANUAL -> "Elige una frecuencia para las copias automáticas."
        automaticEnabled && destination.isNullOrBlank() -> "Elige una carpeta para las copias automáticas."
        else -> null
    }
}

private data class BackupSettingsSnapshot(
    val enabled: Boolean,
    val frequency: BackupFrequency,
    val destination: String?,
    val lastAt: Long?,
    val error: String? = null,
)

class BackupViewModel(
    private val service: BackupService,
    private val repository: BackupRepository,
    private val settings: BackupSettingsRepository,
    private val history: BackupHistoryStore,
    private val files: FileGateway,
    private val scheduler: BackupScheduler,
) : ViewModel() {
    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.automaticEnabled,
                settings.frequency,
                settings.destinationUri,
                settings.lastAutomaticAt,
            ) { enabled, frequency, destination, lastAt ->
                BackupSettingsSnapshot(enabled, frequency, destination, lastAt)
            }.catch {
                emit(
                    BackupSettingsSnapshot(
                        enabled = false,
                        frequency = BackupFrequency.MANUAL,
                        destination = null,
                        lastAt = null,
                        error = "No pudimos leer los ajustes de copia de seguridad.",
                    )
                )
            }.collect { snapshot ->
                state.update {
                    it.copy(
                        settingsLoading = false,
                        automaticEnabled = snapshot.enabled,
                        frequency = snapshot.frequency,
                        destination = snapshot.destination,
                        destinationLabel = snapshot.destination?.let { stored ->
                            runCatching { files.describe(stored.toUri()) }.getOrDefault("Carpeta elegida")
                        },
                        lastAutomaticAt = snapshot.lastAt,
                        error = snapshot.error ?: it.error,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadHistory() }
    }

    fun setAutomaticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setAutomaticEnabled(enabled)
                if (enabled) scheduler.schedule(state.value.frequency) else scheduler.cancel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "No se pudo guardar el ajuste.") }
            }
        }
    }

    fun setFrequency(frequency: BackupFrequency) {
        viewModelScope.launch {
            try {
                settings.setFrequency(frequency)
                if (state.value.automaticEnabled) {
                    if (frequency == BackupFrequency.MANUAL) scheduler.cancel()
                    else scheduler.schedule(frequency)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "No se pudo guardar la frecuencia.") }
            }
        }
    }

    fun setDestination(uri: Uri) {
        viewModelScope.launch {
            try {
                files.takePersistablePermission(uri)
                settings.setDestinationUri(uri.toString())
                if (state.value.automaticEnabled && state.value.frequency != BackupFrequency.MANUAL) {
                    scheduler.schedule(state.value.frequency)
                }
                state.update { it.copy(message = "Carpeta de copias actualizada.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update { it.copy(error = "No se pudo guardar el permiso de la carpeta.") }
            }
        }
    }

    fun createManaged(password: CharArray?) {
        perform(password) { service.createManaged(it) }
    }

    fun createExternal(uri: Uri, password: CharArray?) {
        perform(password) { service.createToUri(uri, it) }
    }

    fun suggestedFileName(): String = service.suggestedFileName()

    fun delete(entry: BackupHistoryEntry) {
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null) }
            try {
                val removed = history.remove(entry.id)
                loadHistory()
                state.update {
                    it.copy(
                        busy = false,
                        message = if (removed) "Copia eliminada." else "La copia ya no existía.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        error = error.message ?: "No se pudo eliminar la copia.",
                    )
                }
            }
        }
    }

    /** Location for a history entry, converted to a URI the restore screen can open. */
    fun restoreUri(entry: BackupHistoryEntry): Uri? {
        val location = history.locationFor(entry) ?: return null
        return if (entry.location == BackupLocation.MANAGED) {
            runCatching { Uri.fromFile(File(location)) }.getOrNull()
        } else {
            runCatching { location.toUri() }.getOrNull()
        }
    }

    fun consumeMessage() {
        state.update { it.copy(message = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    private fun perform(password: CharArray?, create: suspend (CharArray?) -> BackupHistoryEntry) {
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val entry = create(password)
                loadHistory()
                state.update {
                    it.copy(
                        busy = false,
                        message = "Copia creada: ${entry.fileName} (${TransferFormats.formatSize(entry.sizeBytes)})",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo crear la copia de seguridad.",
                    )
                }
                loadHistory()
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    private suspend fun loadHistory() {
        state.update { it.copy(historyLoading = true) }
        try {
            val entries = history.entries()
            val missing = entries.filterNot { entry -> history.exists(entry) }.map { it.id }.toSet()
            val counts = runCatching { repository.counts() }.getOrDefault(emptyMap())
            state.update {
                it.copy(
                    historyLoading = false,
                    history = entries,
                    missing = missing,
                    counts = counts,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.update {
                it.copy(
                    historyLoading = false,
                    error = "No pudimos cargar el historial de copias.",
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    service = container.backupService,
                    repository = container.backupRepository,
                    settings = container.backupSettingsRepository,
                    history = container.backupHistoryStore,
                    files = container.fileGateway,
                    scheduler = container.backupScheduler,
                )
            }
        }
    }
}
