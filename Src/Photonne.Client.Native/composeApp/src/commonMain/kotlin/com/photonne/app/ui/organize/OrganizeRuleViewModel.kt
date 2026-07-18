package com.photonne.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.data.models.YearCount
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.album.smart.ConditionPickersController
import com.photonne.app.ui.album.smart.SmartCondition
import com.photonne.app.ui.album.smart.SmartPickerData
import com.photonne.app.ui.album.smart.buildSmartRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for "Mover por condiciones": a condition rule (reusing the smart-album
 * builder), a chosen destination folder, a live preview of how many pending
 * assets match, and the one-shot move.
 */
data class OrganizeRuleUiState(
    val matchAll: Boolean = true,
    val conditions: List<SmartCondition> = emptyList(),
    val targetFolderId: String? = null,
    val targetFolderPath: String? = null,
    val organizeByYear: Boolean = false,
    val previewCount: Int? = null,
    val previewSampleIds: List<String> = emptyList(),
    val yearBreakdown: List<YearCount> = emptyList(),
    val isPreviewing: Boolean = false,
    val isMoving: Boolean = false,
    val error: UiError? = null,
) {
    val activeConditions: List<SmartCondition> get() = conditions.filterNot { it.isEmpty }
    val hasConditions: Boolean get() = activeConditions.isNotEmpty()
    val canMove: Boolean
        get() = hasConditions && targetFolderId != null && (previewCount ?: 0) > 0 && !isMoving
}

/**
 * Drives the "Para organizar → Mover por condiciones" screen: build a rule out
 * of people/folders/dates/scenes conditions, preview how many MobileBackup
 * assets match (scoped server-side to the inbox), and file them all into a
 * chosen folder in one shot. The condition pickers are shared with the
 * smart-album editor via [ConditionPickersController].
 */
class OrganizeRuleViewModel(
    private val organize: OrganizeRepository,
    search: SearchRepository,
    folders: FoldersRepository,
    authState: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizeRuleUiState())
    val state: StateFlow<OrganizeRuleUiState> = _state.asStateFlow()

    private val pickersController = ConditionPickersController(search, folders, authState, viewModelScope)
    val pickers: StateFlow<SmartPickerData> = pickersController.pickers

    private var previewJob: Job? = null

    /** Blank slate on screen entry — the VM instance is reused across navigations. */
    fun reset() {
        previewJob?.cancel()
        pickersController.reset()
        _state.value = OrganizeRuleUiState()
    }

    fun setMatchAll(value: Boolean) {
        _state.update { it.copy(matchAll = value) }
        schedulePreview()
    }

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

    fun setTarget(folderId: String, path: String) {
        _state.update { it.copy(targetFolderId = folderId, targetFolderPath = path) }
    }

    fun setOrganizeByYear(enabled: Boolean) {
        _state.update { it.copy(organizeByYear = enabled) }
    }

    /** Debounced dry-run so the "N fotos coinciden" count and thumbnails track
     * edits without a request per keystroke/toggle. */
    private fun schedulePreview() {
        previewJob?.cancel()
        val rule = buildSmartRule(_state.value.conditions, _state.value.matchAll)
        if (rule == null) {
            _state.update { it.copy(previewCount = null, previewSampleIds = emptyList(), yearBreakdown = emptyList(), isPreviewing = false) }
            return
        }
        _state.update { it.copy(isPreviewing = true) }
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            runCatching { organize.previewRule(rule) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            previewCount = result.count,
                            previewSampleIds = result.sampleAssetIds,
                            yearBreakdown = result.yearBreakdown,
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

    /** Files every matching pending asset into the chosen folder. On success
     * [onDone] receives the moved count so the caller can refresh the inbox
     * badge and navigate back. */
    fun move(onDone: (MoveOutcome) -> Unit) {
        val current = _state.value
        val rule = buildSmartRule(current.conditions, current.matchAll)
        val target = current.targetFolderId
        if (rule == null || target == null || !current.canMove) return
        _state.update { it.copy(isMoving = true, error = null) }
        viewModelScope.launch {
            runCatching { organize.moveByRule(rule, target, current.organizeByYear) }
                .onSuccess { outcome ->
                    _state.update { it.copy(isMoving = false) }
                    onDone(outcome)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isMoving = false, error = errorFactory.from(error, "No se pudieron mover"))
                    }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── Picker data (delegated to the shared controller) ─────────────────────
    fun setPeopleQuery(query: String) = pickersController.setPeopleQuery(query)
    fun setSceneQuery(query: String) = pickersController.setSceneQuery(query)
    fun setObjectQuery(query: String) = pickersController.setObjectQuery(query)
    fun ensureFolders() = pickersController.ensureFolders()

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 400L
    }
}
