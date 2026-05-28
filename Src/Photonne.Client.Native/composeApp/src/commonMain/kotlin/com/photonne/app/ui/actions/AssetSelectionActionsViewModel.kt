package com.photonne.app.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.actions.AssetActionsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

enum class AssetActionWorking { Idle, Downloading, Sharing, CreatingLink }

data class AssetActionsUiState(
    /** Asset ids parked for the share chooser; non-null when the dialog should show. */
    val shareChooserIds: List<String>? = null,
    val working: AssetActionWorking = AssetActionWorking.Idle,
    /** Resulting public-share URL when "Create a Photonne link" succeeds. */
    val createdLink: String? = null,
    /** Localized message surfaced as a toast/banner once an action settles. */
    val statusMessage: String? = null,
    val error: UiError? = null,
) {
    val shareChooserCount: Int get() = shareChooserIds?.size ?: 0
}

/**
 * Single owner for the standard selection actions ("Compartir",
 * "Descargar", "Crear enlace"). Every selection top bar plugs into
 * this same view-model so the chooser dialog, the in-flight spinner
 * and the resulting toast/banner are consistent app-wide.
 *
 * The selection itself stays with each screen's view-model — this
 * one just receives the asset id list and (when the user picks
 * "share directly") drives the platform [AssetSharing] glue.
 */
class AssetSelectionActionsViewModel(
    private val repository: AssetActionsRepository,
    private val sharing: AssetSharing,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetActionsUiState())
    val state: StateFlow<AssetActionsUiState> = _state.asStateFlow()

    fun beginShare(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        _state.update { it.copy(shareChooserIds = assetIds, error = null) }
    }

    fun cancelShare() {
        _state.update { it.copy(shareChooserIds = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(statusMessage = null, error = null) }
    }

    fun dismissLink() {
        _state.update { it.copy(createdLink = null) }
    }

    /**
     * Download every selected asset:
     * - 1 asset → single original via `/api/assets/{id}/content`, saved
     *   to the OS Downloads location.
     * - N assets → bulk ZIP via `/api/assets/download-zip`.
     */
    fun download(assetIds: List<String>) {
        if (assetIds.isEmpty() || _state.value.working != AssetActionWorking.Idle) return
        _state.update {
            it.copy(working = AssetActionWorking.Downloading, error = null)
        }
        viewModelScope.launch {
            runCatching {
                if (assetIds.size == 1) {
                    val content = repository.downloadOriginal(assetIds.first())
                    sharing.saveAsset(
                        bytes = content.bytes,
                        fileName = content.suggestedFileName,
                        mimeType = content.mimeType
                    )
                } else {
                    val zip = repository.downloadZip(
                        assetIds = assetIds,
                        fileName = defaultZipName()
                    )
                    sharing.saveZip(bytes = zip, fileName = "${defaultZipName()}.zip")
                }
            }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            statusMessage = saved.displayName
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            error = errorFactory.from(error, "Download failed")
                        )
                    }
                }
        }
    }

    /**
     * Hands the selection to the OS share sheet. Same single/multi
     * split as [download]: 1 asset → original file, N assets → ZIP.
     */
    fun shareDirectly(assetIds: List<String>) {
        if (assetIds.isEmpty() || _state.value.working != AssetActionWorking.Idle) return
        _state.update {
            it.copy(
                working = AssetActionWorking.Sharing,
                shareChooserIds = null,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val files = if (assetIds.size == 1) {
                    val content = repository.downloadOriginal(assetIds.first())
                    listOf(
                        sharing.saveAsset(
                            bytes = content.bytes,
                            fileName = content.suggestedFileName,
                            mimeType = content.mimeType
                        )
                    )
                } else {
                    val zip = repository.downloadZip(
                        assetIds = assetIds,
                        fileName = defaultZipName()
                    )
                    listOf(sharing.saveZip(bytes = zip, fileName = "${defaultZipName()}.zip"))
                }
                val mimeType = files.firstOrNull()?.mimeType ?: "application/octet-stream"
                sharing.shareFiles(files = files, mimeType = mimeType)
            }
                .onSuccess {
                    _state.update { it.copy(working = AssetActionWorking.Idle) }
                }
                .onFailure { error ->
                    val uiError = when (error) {
                        is AssetSharingUnavailable ->
                            UiError(userMessage = error.message ?: "Share not supported")
                        else -> errorFactory.from(error, "Share failed")
                    }
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            error = uiError
                        )
                    }
                }
        }
    }

    /**
     * Wraps the selection in a brand-new Photonne album, creates a
     * public link for it, and surfaces the URL so the screen can drop
     * it into a "copy link" result dialog.
     */
    fun createPhotonneLink(assetIds: List<String>, albumName: String) {
        if (assetIds.isEmpty() || _state.value.working != AssetActionWorking.Idle) return
        _state.update {
            it.copy(
                working = AssetActionWorking.CreatingLink,
                shareChooserIds = null,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.createShareLinkForAssets(
                    assetIds = assetIds,
                    albumName = albumName
                )
            }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            createdLink = result.url
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            error = errorFactory.from(error, "Could not create link")
                        )
                    }
                }
        }
    }

    /**
     * Filename used for the bulk-zip + the auto-created album name on
     * the Photonne link flow. Kept date-only so consecutive selections
     * within the same minute land on the same string and the user can
     * tell them apart by the inner contents rather than the title.
     */
    private fun defaultZipName(): String {
        val now = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val month = now.monthNumber.toString().padStart(2, '0')
        val day = now.dayOfMonth.toString().padStart(2, '0')
        return "photonne_${now.year}-${month}-${day}"
    }
}
