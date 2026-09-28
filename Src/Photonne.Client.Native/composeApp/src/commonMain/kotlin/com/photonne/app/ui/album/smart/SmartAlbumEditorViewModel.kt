package com.photonne.app.ui.album.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.SmartRule
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.folder.FolderNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editor state for a smart album: a brand-new one ("Nuevo álbum inteligente")
 *  or an existing one being edited ([editingAlbumId] set). */
data class SmartAlbumEditorUiState(
    val name: String = "",
    val description: String = "",
    /** Top-level "Coincidir con Todas (AND) / Cualquiera (OR)" toggle. */
    val matchAll: Boolean = true,
    val conditions: List<SmartCondition> = emptyList(),
    /** Rule nodes this editor can't show, carried through a save (see [parseSmartRule]). */
    val preserved: List<SmartRule> = emptyList(),
    val previewCount: Int? = null,
    val previewSampleIds: List<String> = emptyList(),
    val isPreviewing: Boolean = false,
    val isCreating: Boolean = false,
    val editingAlbumId: String? = null,
    val isLoadingRule: Boolean = false,
    /** The rule couldn't be loaded: saving would overwrite it blind, so it's blocked. */
    val ruleLoadFailed: Boolean = false,
    val error: UiError? = null,
) {
    val isEditing: Boolean get() = editingAlbumId != null
    val activeConditions: List<SmartCondition> get() = conditions.filterNot { it.isEmpty }
    val canSave: Boolean
        get() = name.isNotBlank() &&
            (activeConditions.isNotEmpty() || preserved.isNotEmpty()) &&
            !isCreating && !isLoadingRule && !ruleLoadFailed

    /** What the discard guard compares against the loaded album. */
    internal fun draft() = listOf(name.trim(), description.trim(), matchAll, activeConditions, preserved)
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
    private var loadJob: Job? = null
    private var baseline: List<Any?>? = null

    /** Clears the editor back to a blank slate. Called on screen entry because
     * the VM instance is reused across navigations (single ViewModelStoreOwner),
     * so a freshly-opened "Nuevo álbum" would otherwise keep the last edit. */
    fun reset() {
        previewJob?.cancel()
        loadJob?.cancel()
        pickersController.reset()
        baseline = null
        _state.value = SmartAlbumEditorUiState()
    }

    /** Opens the editor on an existing smart album: name/description right away,
     * then the stored rule rebuilt into editable rows. [unknownName] labels an
     * id the server couldn't name (person or folder deleted since). */
    fun loadForEdit(album: AlbumSummary, unknownName: String) {
        reset()
        _state.value = SmartAlbumEditorUiState(
            name = album.name,
            description = album.description.orEmpty(),
            editingAlbumId = album.id,
            isLoadingRule = true,
        )
        loadJob = viewModelScope.launch {
            runCatching { albums.smartRule(album.id) }
                .onSuccess { details ->
                    val parsed = parseSmartRule(details, unknownName)
                    _state.update {
                        it.copy(
                            matchAll = parsed.matchAll,
                            conditions = parsed.conditions,
                            preserved = parsed.preserved,
                            isLoadingRule = false,
                        )
                    }
                    baseline = _state.value.draft()
                    schedulePreview()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingRule = false,
                            ruleLoadFailed = true,
                            error = errorFactory.from(error, "No se pudieron cargar las condiciones"),
                        )
                    }
                }
        }
    }

    /** True when there is something to lose on back: any input for a new album,
     * or a difference from what was loaded when editing. */
    fun isDirty(s: SmartAlbumEditorUiState): Boolean =
        if (s.isEditing) baseline != null && s.draft() != baseline
        else s.name.isNotBlank() || s.description.isNotBlank() || s.activeConditions.isNotEmpty()

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setDescription(value: String) = _state.update { it.copy(description = value) }

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
        val rule = buildSmartRule(_state.value.conditions, _state.value.matchAll, _state.value.preserved)
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

    /** Creates the album, or saves name/description/rule when editing one. */
    fun save(onSaved: (AlbumSummary) -> Unit) {
        val current = _state.value
        val rule = buildSmartRule(current.conditions, current.matchAll, current.preserved)
        if (!current.canSave || rule == null) return
        val name = current.name.trim()
        val description = current.description.trim().ifEmpty { null }
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val albumId = current.editingAlbumId
                if (albumId != null) albums.updateSmart(albumId, name, description, rule)
                else albums.createSmart(name, description, rule)
            }
                .onSuccess { album ->
                    _state.update { it.copy(isCreating = false) }
                    onSaved(album)
                }
                .onFailure { error ->
                    val fallback = if (current.isEditing) "No se pudo guardar el álbum" else "No se pudo crear el álbum"
                    _state.update { it.copy(isCreating = false, error = errorFactory.from(error, fallback)) }
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
