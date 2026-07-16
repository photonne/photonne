package com.photonne.app.ui.album.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.folder.FolderNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editor state for a brand-new smart album ("Nuevo álbum inteligente"). */
data class SmartAlbumEditorUiState(
    val name: String = "",
    /** Top-level "Coincidir con Todas (AND) / Cualquiera (OR)" toggle. */
    val matchAll: Boolean = true,
    val conditions: List<SmartCondition> = emptyList(),
    val previewCount: Int? = null,
    val previewSampleIds: List<String> = emptyList(),
    val isPreviewing: Boolean = false,
    val isCreating: Boolean = false,
    val error: UiError? = null,
) {
    val activeConditions: List<SmartCondition> get() = conditions.filterNot { it.isEmpty }
    val canSave: Boolean get() = name.isNotBlank() && activeConditions.isNotEmpty() && !isCreating
}

/** Sources feeding the condition pickers, loaded lazily the first time the
 * user opens the "add condition" sheet. */
data class PickerResults<T>(
    val query: String = "",
    val results: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val loadedOnce: Boolean = false,
)

data class SmartPickerData(
    val people: PickerResults<PersonRef> = PickerResults(),
    val scenes: PickerResults<LabelRef> = PickerResults(),
    val objects: PickerResults<LabelRef> = PickerResults(),
    // Folders have no server search: loaded once, filtered/browsed in the composable.
    // `folders` is the flat list (search mode); `folderRoots` is the tree (browse mode).
    val folders: List<FolderRef> = emptyList(),
    val folderRoots: List<FolderNode> = emptyList(),
    val foldersLoading: Boolean = false,
    val foldersLoaded: Boolean = false,
)

class SmartAlbumEditorViewModel(
    private val albums: AlbumsRepository,
    search: SearchRepository,
    folders: FoldersRepository,
    authState: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(SmartAlbumEditorUiState())
    val state: StateFlow<SmartAlbumEditorUiState> = _state.asStateFlow()

    private val pickersController = ConditionPickersController(search, folders, authState, viewModelScope)
    val pickers: StateFlow<SmartPickerData> = pickersController.pickers

    private var previewJob: Job? = null

    /** Clears the editor back to a blank slate. Called on screen entry because
     * the VM instance is reused across navigations (single ViewModelStoreOwner),
     * so a freshly-opened "Nuevo álbum" would otherwise keep the last edit. */
    fun reset() {
        previewJob?.cancel()
        pickersController.reset()
        _state.value = SmartAlbumEditorUiState()
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setMatchAll(value: Boolean) {
        _state.update { it.copy(matchAll = value) }
        schedulePreview()
    }

    /** Adds a condition, or replaces the existing one of the same kind (there is
     * at most one People / Folders / DateRange / … condition in the MVP editor). */
    fun upsertCondition(condition: SmartCondition) {
        _state.update { s ->
            val without = s.conditions.filterNot { it.key == condition.key }
            s.copy(conditions = if (condition.isEmpty) without else without + condition)
        }
        schedulePreview()
    }

    fun removeCondition(key: String) {
        _state.update { s -> s.copy(conditions = s.conditions.filterNot { it.key == key }) }
        schedulePreview()
    }

    /** Debounced dry-run so the "N fotos coinciden" count and thumbnail strip
     * track edits without a request per keystroke/toggle. */
    private fun schedulePreview() {
        previewJob?.cancel()
        val rule = buildSmartRule(_state.value.conditions, _state.value.matchAll)
        if (rule == null) {
            _state.update { it.copy(previewCount = null, previewSampleIds = emptyList(), isPreviewing = false) }
            return
        }
        _state.update { it.copy(isPreviewing = true) }
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            runCatching { albums.preview(rule) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            previewCount = result.count,
                            previewSampleIds = result.sampleAssetIds,
                            isPreviewing = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isPreviewing = false, error = errorFactory.from(error, "No se pudo previsualizar"))
                    }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun create(onCreated: (AlbumSummary) -> Unit) {
        val current = _state.value
        val rule = buildSmartRule(current.conditions, current.matchAll)
        if (!current.canSave || rule == null) return
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            runCatching { albums.createSmart(current.name.trim(), null, rule) }
                .onSuccess { album ->
                    _state.update { it.copy(isCreating = false) }
                    onCreated(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isCreating = false, error = errorFactory.from(error, "No se pudo crear el álbum"))
                    }
                }
        }
    }

    // ── Picker data (delegated to the shared controller) ─────────────────────

    fun setPeopleQuery(query: String) = pickersController.setPeopleQuery(query)
    fun setSceneQuery(query: String) = pickersController.setSceneQuery(query)
    fun setObjectQuery(query: String) = pickersController.setObjectQuery(query)
    fun ensureFolders() = pickersController.ensureFolders()

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 400L
    }
}
