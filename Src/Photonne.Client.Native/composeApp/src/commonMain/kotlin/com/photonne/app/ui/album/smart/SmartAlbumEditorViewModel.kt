package com.photonne.app.ui.album.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.search.SearchRepository
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
data class SmartPickerData(
    val people: List<PersonRef> = emptyList(),
    val folders: List<FolderRef> = emptyList(),
    val sceneLabels: List<String> = emptyList(),
    val objectLabels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
)

class SmartAlbumEditorViewModel(
    private val albums: AlbumsRepository,
    private val search: SearchRepository,
    private val folders: FoldersRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(SmartAlbumEditorUiState())
    val state: StateFlow<SmartAlbumEditorUiState> = _state.asStateFlow()

    private val _pickers = MutableStateFlow(SmartPickerData())
    val pickers: StateFlow<SmartPickerData> = _pickers.asStateFlow()

    private var previewJob: Job? = null

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

    /** Loads the picker sources once (people, folders, scene/object labels). */
    fun ensurePickerData() {
        if (_pickers.value.loaded || _pickers.value.isLoading) return
        _pickers.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val people = runCatching {
                search.people(limit = 200)
                    .filter { !it.name.isNullOrBlank() }
                    .map { PersonRef(it.id, it.name!!, it.coverFaceId) }
            }.getOrDefault(emptyList())

            val folderRefs = runCatching {
                folders.list()
                    .filterNot { it.path.contains("/_trash") || it.path.contains("/_archive") }
                    .map { FolderRef(it.id, it.name, it.isShared) }
            }.getOrDefault(emptyList())

            val scenes = runCatching { search.sceneLabels(limit = 120).map { it.label } }.getOrDefault(emptyList())
            val objects = runCatching { search.objectLabels(limit = 120).map { it.label } }.getOrDefault(emptyList())

            _pickers.update {
                it.copy(
                    people = people,
                    folders = folderRefs,
                    sceneLabels = scenes,
                    objectLabels = objects,
                    isLoading = false,
                    loaded = true,
                )
            }
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 400L
    }
}
