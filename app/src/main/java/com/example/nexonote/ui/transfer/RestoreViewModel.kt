package com.example.nexonote.ui.transfer

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nexonote.data.transfer.RestoreService
import com.example.nexonote.di.AppContainer
import com.example.nexonote.domain.model.RestoreMode
import com.example.nexonote.domain.model.RestorePreview
import com.example.nexonote.domain.model.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RestoreUiState(
    val inspecting: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val preview: RestorePreview? = null,
    val passwordRequired: Boolean = false,
    val passwordVerified: Boolean = false,
    val mode: RestoreMode = RestoreMode.REPLACE,
    val result: RestoreResult? = null,
    val sourceUri: String? = null,
    val sourceName: String? = null,
) {
    val encrypted: Boolean get() = preview?.manifest?.encrypted == true
    val canRestore: Boolean
        get() = preview?.compatible == true && passwordVerified && !busy && result == null
}

class RestoreViewModel(
    private val service: RestoreService,
    initialUri: String?,
) : ViewModel() {
    private val state = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

    init {
        initialUri?.takeIf { it.isNotBlank() }?.let { inspect(it.toUri(), null) }
    }

    fun inspect(uri: Uri, password: CharArray? = null) {
        val sameSource = state.value.sourceUri == uri.toString()
        viewModelScope.launch {
            state.update {
                it.copy(
                    inspecting = true,
                    error = null,
                    message = null,
                    result = null,
                    preview = if (sameSource) it.preview else null,
                    passwordVerified = if (sameSource) it.passwordVerified else false,
                    passwordRequired = if (sameSource) it.passwordRequired else false,
                    sourceUri = uri.toString(),
                )
            }
            try {
                val preview = service.inspect(uri, password)
                val provided = password != null && password.isNotEmpty()
                val verified = !preview.manifest.encrypted || provided
                state.update {
                    it.copy(
                        inspecting = false,
                        preview = preview,
                        sourceName = preview.fileName,
                        sourceUri = uri.toString(),
                        passwordRequired = preview.manifest.encrypted && !provided,
                        passwordVerified = verified,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        inspecting = false,
                        passwordVerified = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo validar la copia de seguridad.",
                    )
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun setMode(mode: RestoreMode) {
        state.update { it.copy(mode = mode) }
    }

    fun restore(password: CharArray?) {
        val current = state.value
        val uri = current.sourceUri?.let { runCatching { it.toUri() }.getOrNull() } ?: return
        if (!current.canRestore) return
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = service.restore(uri, password, current.mode)
                state.update {
                    it.copy(
                        busy = false,
                        result = result,
                        message = "Restauración completada: ${result.restoredRows.values.sum()} filas.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        busy = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo restaurar la copia. Tus datos actuales no se modificaron.",
                    )
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun reset() {
        state.update {
            RestoreUiState(mode = it.mode)
        }
    }

    fun consumeMessage() {
        state.update { it.copy(message = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer, initialUri: String?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RestoreViewModel(container.restoreService, initialUri) }
            }
    }
}
