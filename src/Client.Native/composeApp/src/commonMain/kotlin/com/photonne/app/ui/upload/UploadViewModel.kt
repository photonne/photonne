package com.photonne.app.ui.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.upload.UploadRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class UploadStatus { Queued, Uploading, Done, Skipped, Failed, Cancelled }

data class UploadItem(
    val id: Long,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /**
     * Held while the item is Queued or Uploading. Cleared once it
     * leaves the active queue so we don't keep the whole batch
     * resident in memory after a successful upload.
     */
    val bytes: ByteArray?,
    val lastModifiedMillis: Long? = null,
    /** A Live Photo's paired video; released together with [bytes]. */
    val motionClip: PickedMotionClip? = null,
    val status: UploadStatus = UploadStatus.Queued,
    val assetId: String? = null,
    val errorMessage: String? = null
)

data class UploadUiState(
    val items: List<UploadItem> = emptyList(),
    val isUploading: Boolean = false,
    val pickerError: String? = null,
    /**
     * Lo que ha entrado en el servidor en la última tanda, cuando la cola se
     * ha vaciado sola: da el resumen "N subidas · Ver · Añadir a álbum". Null
     * mientras sube, al descartarlo o al empezar otra tanda.
     */
    val lastBatch: List<TimelineItem>? = null,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
) {
    val pendingCount: Int get() = items.count {
        it.status == UploadStatus.Queued || it.status == UploadStatus.Uploading
    }
    val doneCount: Int get() = items.count { it.status == UploadStatus.Done }
    val skippedCount: Int get() = items.count { it.status == UploadStatus.Skipped }
    val failedCount: Int get() = items.count { it.status == UploadStatus.Failed }
}

class UploadViewModel(
    private val repository: UploadRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    private var nextId = 0L
    private var worker: Job? = null

    /**
     * [onQueueDrained] corre UNA vez cuando la cola se vacía y algo nuevo ha
     * entrado en el servidor (lote L12): antes el timeline se recargaba
     * entero por cada archivo subido.
     */
    fun enqueue(files: List<PickedFile>, onQueueDrained: () -> Unit = {}) {
        if (files.isEmpty()) return
        val newItems = files
            .filter { it.sizeBytes <= MAX_BYTES_PER_FILE }
            .map { picked ->
                UploadItem(
                    id = ++nextId,
                    name = picked.name,
                    mimeType = picked.mimeType.ifBlank { "application/octet-stream" },
                    sizeBytes = picked.sizeBytes,
                    bytes = picked.bytes,
                    lastModifiedMillis = picked.lastModifiedMillis,
                    motionClip = picked.motionClip
                )
            }
        val tooBig = files.size - newItems.size
        _state.update {
            it.copy(
                items = it.items + newItems,
                lastBatch = null,
                pickerError = if (tooBig > 0)
                    "$tooBig archivo(s) superan el límite de ${MAX_MB_PER_FILE} MB y se han omitido."
                else it.pickerError
            )
        }
        ensureWorker(onQueueDrained)
    }

    fun pickerErrorRaised(message: String) {
        _state.update { it.copy(pickerError = message) }
    }

    fun clearPickerError() {
        _state.update { it.copy(pickerError = null) }
    }

    fun retry(id: Long, onQueueDrained: () -> Unit = {}) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.map { item ->
                    if (item.id == id && (item.status == UploadStatus.Failed ||
                            item.status == UploadStatus.Cancelled)
                    ) {
                        item.copy(status = UploadStatus.Queued, errorMessage = null)
                    } else item
                }
            )
        }
        ensureWorker(onQueueDrained)
    }

    fun remove(id: Long) {
        _state.update { previous ->
            // Don't yank an item out from under the worker; mark it
            // cancelled instead so the in-flight request gets a
            // chance to settle before we drop it from the list.
            val items = previous.items.toMutableList()
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return@update previous
            val item = items[index]
            if (item.status == UploadStatus.Uploading) {
                items[index] = item.copy(status = UploadStatus.Cancelled, bytes = null, motionClip = null)
            } else {
                items.removeAt(index)
            }
            previous.copy(items = items)
        }
    }

    fun clearFinished() {
        _state.update { previous ->
            previous.copy(
                lastBatch = null,
                items = previous.items.filter {
                    it.status == UploadStatus.Queued || it.status == UploadStatus.Uploading
                }
            )
        }
    }

    fun cancelAll() {
        worker?.cancel()
        worker = null
        _state.update { previous ->
            previous.copy(
                isUploading = false,
                items = previous.items.map { item ->
                    if (item.status == UploadStatus.Queued || item.status == UploadStatus.Uploading) {
                        item.copy(status = UploadStatus.Cancelled, bytes = null, motionClip = null)
                    } else item
                }
            )
        }
    }

    fun dismissBatchSummary() {
        _state.update { it.copy(lastBatch = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Añade la última tanda a [albumId]; [onAdded] recibe los elementos para
     *  el recuento local del álbum. */
    fun addBatchToAlbum(albumId: String, onAdded: (List<TimelineItem>) -> Unit = {}) {
        val batch = _state.value.lastBatch.orEmpty()
        if (batch.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, batch.map { it.id }) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onAdded(batch)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    private fun ensureWorker(onQueueDrained: () -> Unit) {
        if (worker?.isActive == true) return
        worker = viewModelScope.launch {
            _state.update { it.copy(isUploading = true) }
            var uploadedAny = false
            // Lo subido en ESTA tanda (los reintentos de otra tanda cuentan si
            // entran ahora), para el resumen del final.
            val landedIds = mutableListOf<Long>()
            try {
                while (isActive) {
                    val pick = _state.value.items.firstOrNull { it.status == UploadStatus.Queued }
                        ?: break
                    if (runOne(pick.id)) {
                        uploadedAny = true
                        landedIds += pick.id
                    }
                }
                // La cola se ha vaciado sola (no con "Cancelar todo", que corta
                // antes de llegar aquí): resumen con lo que ha entrado.
                _state.update { current ->
                    val landed = landedIds.toHashSet()
                    val batch = current.items
                        .filter { it.id in landed && it.status == UploadStatus.Done }
                        .mapNotNull { it.toTimelineItem() }
                    current.copy(
                        isUploading = false,
                        lastBatch = batch.takeIf { it.isNotEmpty() }
                    )
                }
            } finally {
                // También si "Cancelar todo" corta la cola: lo ya subido
                // tiene que aparecer igualmente.
                if (uploadedAny) onQueueDrained()
            }
        }
    }

    /** Sube un elemento; true si ha entrado algo nuevo en el servidor. */
    private suspend fun runOne(itemId: Long): Boolean {
        val current = _state.value.items.firstOrNull { it.id == itemId } ?: return false
        val bytes = current.bytes
        if (bytes == null) {
            _state.update {
                it.copy(
                    items = it.items.map { item ->
                        if (item.id == itemId)
                            item.copy(status = UploadStatus.Failed, errorMessage = "Faltan los datos del archivo")
                        else item
                    }
                )
            }
            return false
        }
        _state.update {
            it.copy(
                items = it.items.map { item ->
                    if (item.id == itemId) item.copy(status = UploadStatus.Uploading) else item
                }
            )
        }
        val outcome = runCatching {
            repository.upload(
                current.name,
                current.mimeType,
                bytes,
                fileModifiedAtMillis = current.lastModifiedMillis
            )
        }
        outcome.onSuccess { response ->
            val alreadyExisted = response.message.contains("already exists", ignoreCase = true)
            val clip = current.motionClip
            val assetId = response.assetId
            if (clip != null && !assetId.isNullOrEmpty()) attachMotionClip(assetId, clip, alreadyExisted)
            _state.update {
                it.copy(
                    items = it.items.map { item ->
                        if (item.id == itemId) item.copy(
                            status = if (alreadyExisted) UploadStatus.Skipped else UploadStatus.Done,
                            assetId = response.assetId,
                            bytes = null,
                            motionClip = null
                        ) else item
                    }
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    items = it.items.map { item ->
                        if (item.id == itemId) item.copy(
                            status = UploadStatus.Failed,
                            errorMessage = error.message ?: "No se pudo subir"
                        ) else item
                    }
                )
            }
        }
        val landed = outcome.getOrNull()?.let { response ->
            response.assetId != null &&
                !response.message.contains("already exists", ignoreCase = true)
        } ?: false
        return landed
    }

    /**
     * Sube el vídeo de una Live Photo tras su foto. Si la foto ya estaba en el
     * servidor solo lo manda cuando le falta, así que volver a subir el carrete
     * a mano recupera los vídeos que la copia antigua no llevó. Un fallo aquí
     * no falla el elemento: la foto ya ha entrado y la copia lo reintenta.
     */
    private suspend fun attachMotionClip(assetId: String, clip: PickedMotionClip, alreadyExisted: Boolean) {
        try {
            if (alreadyExisted && !repository.motionClipMissing(assetId)) return
            repository.attachMotionClip(assetId, clip.name, clip.mimeType, clip.bytes)
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Throwable) {
        }
    }

    /**
     * Elemento de rejilla/visor para lo recién subido. El visor vuelve a pedir
     * el detalle por id, así que basta con lo que ya se sabe del archivo.
     */
    private fun UploadItem.toTimelineItem(): TimelineItem? {
        val id = assetId ?: return null
        val now = Clock.System.now()
        val modified = lastModifiedMillis?.let { Instant.fromEpochMilliseconds(it) } ?: now
        return TimelineItem(
            id = id,
            fileName = name,
            fullPath = "",
            fileSize = sizeBytes,
            fileCreatedAt = modified,
            fileModifiedAt = modified,
            extension = name.substringAfterLast('.', "").lowercase(),
            scannedAt = now,
            type = if (mimeType.startsWith("video/", ignoreCase = true)) "VIDEO" else "IMAGE",
            hasThumbnails = true
        )
    }

    companion object {
        const val MAX_MB_PER_FILE = 200
        const val MAX_BYTES_PER_FILE = MAX_MB_PER_FILE * 1024L * 1024L
    }
}
