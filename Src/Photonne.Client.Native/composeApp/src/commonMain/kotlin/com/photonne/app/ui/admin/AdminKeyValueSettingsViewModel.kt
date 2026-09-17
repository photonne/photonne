package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_load_failed
import com.photonne.app.resources.admin_settings_save_failed
import org.jetbrains.compose.resources.getString
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
    /** The load itself failed: there is nothing trustworthy to edit, so the
     *  form shows the error with a retry instead of a page of defaults that
     *  would pass for the server's values. */
    val loadFailed: Boolean = false,
    /** Keys whose current value the server would refuse or silently rewrite
     *  (empty, not a number, outside its range, or at odds with another
     *  field). Saving waits until this is empty. */
    val invalid: Set<String> = emptySet(),
    val error: UiError? = null,
    /** The last Save went through: the form says so on the snackbar, once. */
    val saved: Boolean = false
) {
    val isDirty: Boolean
        get() = current != original

    val canSave: Boolean
        get() = !isSubmitting && !isLoading && isDirty && invalid.isEmpty()

    fun get(key: String): String = current[key].orEmpty()

    fun bool(key: String): Boolean = get(key).equals("true", ignoreCase = true)

    fun int(key: String, default: Int): Int = get(key).toIntOrNull() ?: default
}

/**
 * Base ViewModel that loads/saves a fixed set of setting keys. Concrete
 * subpages subclass it with their key list and (optional) defaults map.
 * `transform` lets a subclass coerce/sanitize a single field before it
 * goes into the editable state (e.g. clamping integers to a range).
 */
abstract class AdminKeyValueSettingsViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
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

    /** Whole-number settings and the range the server accepts for each. The
     *  server never rejects a value: it clamps or falls back to its default
     *  when it reads one, without telling anybody, so the form is the only
     *  place an admin can find out that 500 is not a JPEG quality. */
    open val intRanges: Map<String, IntRange> = emptyMap()

    /** Rules that span more than one field. Returns the keys at fault. */
    protected open fun crossCheck(current: Map<String, String>): Set<String> = emptySet()

    private fun invalidKeys(current: Map<String, String>): Set<String> =
        invalidIntKeys(current, intRanges) + crossCheck(current)

    fun load() {
        if (_state.value.isLoading) return
        _state.update {
            it.copy(isLoading = true, loadFailed = false, error = null, saved = false)
        }
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
                            invalid = invalidKeys(withDefaults),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = true,
                            error = errorFactory.from(error, getString(Res.string.admin_settings_load_failed))
                        )
                    }
                }
        }
    }

    fun setBool(key: String, value: Boolean) = set(key, if (value) "true" else "false")

    fun set(key: String, value: String) {
        _state.update { current ->
            val edited = current.current + (key to normalize(key, value))
            current.copy(
                current = edited,
                invalid = invalidKeys(edited),
                saved = false
            )
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val changed = s.current.filter { (k, v) -> s.original[k] != v }
        if (changed.isEmpty()) return
        _state.update { it.copy(isSubmitting = true, error = null, saved = false) }
        viewModelScope.launch {
            runCatching { repository.saveSettings(changed) }
                .onSuccess {
                    // Only what was actually sent becomes the new baseline. The
                    // fields stay live during the request, so taking the
                    // latest `current` here would mark an edit made meanwhile
                    // as saved without it ever reaching the server.
                    _state.update {
                        it.copy(
                            original = it.original + changed,
                            isSubmitting = false,
                            saved = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = errorFactory.from(error, getString(Res.string.admin_settings_save_failed))
                        )
                    }
                }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}

/** Keys of [ranges] whose value in [current] is not a whole number inside its
 *  range. An empty field counts: the server would swap in its own default and
 *  the form would go on showing a blank. */
internal fun invalidIntKeys(
    current: Map<String, String>,
    ranges: Map<String, IntRange>,
): Set<String> = ranges.filter { (key, range) ->
    val raw = current[key] ?: return@filter false
    val n = raw.toIntOrNull()
    n == null || n !in range
}.keys
