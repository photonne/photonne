package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared state shape for any Ajustes subpage that loads a set of
 * key/value strings from `/api/settings`, lets the user edit them,
 * and saves the changed ones back.
 *
 * `defaults` is the fall-back map applied when the server hasn't
 * stored a value yet (the endpoint returns an empty string for
 * unknown keys); `current` holds the editable copy.
 */
data class AdminKeyValueUiState(
    val original: Map<String, String> = emptyMap(),
    val current: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val canSave: Boolean
        get() = !isSubmitting && !isLoading && current != original

    fun get(key: String): String = current[key].orEmpty()
}

/**
 * Base ViewModel that loads/saves a fixed set of setting keys. Concrete
 * subpages subclass it with their key list and (optional) defaults map.
 * `transform` lets a subclass coerce/sanitize a single field before it
 * goes into the editable state (e.g. clamping integers to a range).
 */
abstract class AdminKeyValueSettingsViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminKeyValueUiState())
    val state: StateFlow<AdminKeyValueUiState> = _state.asStateFlow()

    /** Keys this subpage owns. Defines the order of GET requests too. */
    abstract val keys: List<String>

    /** Fallback values used when the server returns an empty string for a
     *  key (i.e. nothing has been stored yet). Subclasses override this
     *  to match the server-side defaults. */
    protected open val defaults: Map<String, String> = emptyMap()

    /** Hook for subclasses to normalize input as the user types. */
    protected open fun normalize(key: String, value: String): String = value

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getSettings(keys) }
                .onSuccess { fetched ->
                    val withDefaults = keys.associateWith { key ->
                        fetched[key]?.takeIf { it.isNotBlank() } ?: defaults[key].orEmpty()
                    }
                    _state.update {
                        it.copy(
                            original = withDefaults,
                            current = withDefaults,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudieron cargar los ajustes"
                        )
                    }
                }
        }
    }

    fun set(key: String, value: String) {
        _state.update { current ->
            val normalized = normalize(key, value)
            current.copy(
                current = current.current + (key to normalized),
                successMessage = null
            )
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val changed = s.current.filter { (k, v) -> s.original[k] != v }
        if (changed.isEmpty()) return
        _state.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            runCatching { repository.saveSettings(changed) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            original = it.current,
                            isSubmitting = false,
                            successMessage = "Guardado"
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "No se pudieron guardar los ajustes"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
