package com.photonne.app.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.actions.AssetActionsRepository
import com.photonne.app.data.asset.AssetDetailRepository
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
    private val assets: AssetDetailRepository,
    private val sharing: AssetSharing,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    /**
     * Deshace la última acción en bloque REVERSIBLE.
     *
     * Papelera y archivar dejaron de pedir confirmación previa: se ejecutan al
     * instante y el snackbar ofrece deshacer. Eso quita fricción de cada
     * acción a cambio de un botón durante unos segundos, que es el reparto
     * correcto cuando el error se puede revertir entero. Lo irreversible
     * (vaciar la papelera, borrar para siempre) conserva su diálogo.
     */
    fun undoBulk(kind: BulkUndoKind, assetIds: List<String>, onDone: () -> Unit = {}) {
        if (assetIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    BulkUndoKind.Trash -> assets.restore(assetIds)
                    BulkUndoKind.Archive -> assets.unarchive(assetIds)
                    BulkUndoKind.Unarchive -> assets.archive(assetIds)
                }
            }
                .onSuccess { onDone() }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "No se pudo deshacer"))
                    }
                }
        }
    }

    private val _state = MutableStateFlow(AssetActionsUiState())
    val state: StateFlow<AssetActionsUiState> = _state.asStateFlow()

    /** Trabajo masivo en vuelo (descarga/ZIP/compartir/enlace), cancelable. */
    private var workingJob: kotlinx.coroutines.Job? = null

    /**
     * Cancela la operación masiva en curso. La descarga de un ZIP grande podía
     * durar minutos sin más salida que esperar; la píldora de progreso ofrece
     * este Cancelar.
     */
    fun cancelWorking() {
        workingJob?.cancel()
        workingJob = null
        _state.update { it.copy(working = AssetActionWorking.Idle) }
    }

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
        workingJob = viewModelScope.launch {
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
                            statusMessage = "Descargado: ${saved.displayName}"
                        )
                    }
                }
                .onFailure { error ->
                    // runCatching atrapa también la cancelación: un Cancelar
                    // del usuario no es un error que enseñar.
                    if (error is kotlinx.coroutines.CancellationException) {
                        _state.update { it.copy(working = AssetActionWorking.Idle) }
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            error = errorFactory.from(error, "No se pudo descargar")
                        )
                    }
                }
        }
    }

    /**
     * Hands the selection to the OS share sheet: every original, one file
     * each, staged in the app's private share cache.
     *
     * It used to go through [download]'s path: the file landed in the
     * gallery's `Download/Photonne` (a duplicate on the phone that the backup
     * then saw as new) and several photos travelled as one ZIP that chat apps
     * show as a document. Now nothing touches the gallery and N photos are N
     * files (`ACTION_SEND_MULTIPLE` / several URLs on iOS).
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
        workingJob = viewModelScope.launch {
            runCatching {
                sharing.clearShareCache()
                val usedNames = HashSet<String>()
                // One at a time: only one original is held in memory at once.
                val files = assetIds.map { id ->
                    val content = repository.downloadOriginal(id)
                    sharing.stageForShare(
                        bytes = content.bytes,
                        fileName = uniqueShareName(content.suggestedFileName, usedNames),
                        mimeType = content.mimeType
                    )
                }
                val mimeType = commonShareMimeType(files.map { it.mimeType })
                sharing.shareFiles(files = files, mimeType = mimeType)
            }
                .onSuccess {
                    _state.update { it.copy(working = AssetActionWorking.Idle) }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        _state.update { it.copy(working = AssetActionWorking.Idle) }
                        return@onFailure
                    }
                    val uiError = when (error) {
                        is AssetSharingUnavailable ->
                            UiError(userMessage = error.message ?: "Compartir no es compatible")
                        else -> errorFactory.from(error, "No se pudo compartir")
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

    /** Two originals called IMG_0001.jpg (different folders or cameras) would
     *  overwrite each other in the share cache: suffix the repeats. */
    private fun uniqueShareName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$base ($n)$ext"
            if (used.add(candidate)) return candidate
            n++
        }
    }

    // The narrowest MIME type covering every file: the exact type when they
    // all match, the family wildcard for mixed photos, and the catch-all
    // wildcard for photos plus videos.
    private fun commonShareMimeType(types: List<String>): String {
        val distinct = types.distinct()
        if (distinct.size == 1) return distinct.first()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else "*/*"
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
        workingJob = viewModelScope.launch {
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
                    if (error is kotlinx.coroutines.CancellationException) {
                        _state.update { it.copy(working = AssetActionWorking.Idle) }
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            working = AssetActionWorking.Idle,
                            error = errorFactory.from(error, "No se pudo crear el enlace")
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
        val now = kotlin.time.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val month = now.monthNumber.toString().padStart(2, '0')
        val day = now.dayOfMonth.toString().padStart(2, '0')
        return "photonne_${now.year}-${month}-${day}"
    }
}
