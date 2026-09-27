package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.Person
import com.photonne.app.data.people.PeopleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetFacesUiState(
    val assetId: String? = null,
    val faces: List<Face> = emptyList(),
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val pendingFaceIds: Set<String> = emptySet(),
    /** Face id for which the user is currently picking a person (assign or set-cover). */
    val assigningFaceId: String? = null,
    /** Texto del campo de "Asignar cara": filtra la lista en el servidor. */
    val pickerQuery: String = "",
    /** Resultado de [pickerQuery]; null sin texto (se enseña [people]). */
    val pickerResults: List<Person>? = null,
    val pickerSearching: Boolean = false,
) {
    fun personById(personId: String?): Person? =
        personId?.let { id -> people.firstOrNull { it.id == id } }

    /** Lista del selector de "Asignar cara". */
    val pickerPeople: List<Person> get() = pickerResults ?: people

    /**
     * La persona cuyo nombre coincide EXACTAMENTE con lo tecleado (sin
     * mayúsculas ni acentos). Con ella, la acción principal asigna en vez de
     * crear una segunda "Ana" que luego habría que fusionar.
     */
    val pickerExactMatch: Person?
        get() {
            val typed = foldForMatch(pickerQuery)
            if (typed.isEmpty()) return null
            return pickerPeople.firstOrNull { p ->
                p.name?.let { foldForMatch(it) } == typed
            }
        }
}

/** Minúsculas, sin acentos y sin espacios sobrantes: "  José " == "jose". */
internal fun foldForMatch(text: String): String {
    val trimmed = text.trim().lowercase()
    if (trimmed.isEmpty()) return trimmed
    return buildString(trimmed.length) {
        for (c in trimmed) {
            append(
                when (c) {
                    'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
                    'é', 'è', 'ë', 'ê' -> 'e'
                    'í', 'ì', 'ï', 'î' -> 'i'
                    'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                    'ú', 'ù', 'ü', 'û' -> 'u'
                    'ñ' -> 'n'
                    'ç' -> 'c'
                    else -> c
                }
            )
        }
    }.replace(Regex("\\s+"), " ")
}

/**
 * Backs the per-asset faces bottom sheet. Loads:
 * - the asset's [Face] rows (so we can render bounding-box thumbnails
 *   and per-face actions);
 * - the full person list (so the assign / set-cover dialog has a
 *   ready-made picker).
 *
 * Per-face actions optimistically update the local list before the
 * request settles so the UI feels responsive on flaky networks.
 */
class AssetFacesViewModel(
    private val repository: PeopleRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetFacesUiState())
    val state: StateFlow<AssetFacesUiState> = _state.asStateFlow()

    fun open(assetId: String) {
        if (_state.value.assetId == assetId && _state.value.faces.isNotEmpty()) return
        _state.value = AssetFacesUiState(assetId = assetId, isLoading = true)
        viewModelScope.launch {
            runCatching {
                val faces = repository.assetFaces(assetId)
                val people = repository.list(includeHidden = true, limit = 200, offset = 0)
                faces to people
            }
                .onSuccess { (faces, people) ->
                    _state.update {
                        it.copy(
                            faces = faces,
                            people = people.items,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudieron cargar las caras")
                        )
                    }
                }
        }
    }

    fun close() {
        _state.value = AssetFacesUiState()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // --- Pick-a-person flow (assign or set cover) --------------------

    fun startAssigning(faceId: String) {
        _state.update {
            it.copy(
                assigningFaceId = faceId,
                pickerQuery = "",
                pickerResults = null,
                pickerSearching = false
            )
        }
    }

    fun cancelAssigning() {
        pickerJob?.cancel()
        _state.update { it.copy(assigningFaceId = null) }
    }

    private var pickerJob: Job? = null

    /**
     * Filtra el selector buscando en el servidor: solo se cargan 200 personas
     * y, sin esto, a alguien de fuera de ellas no se le podía asignar la cara.
     */
    fun setPickerQuery(text: String) {
        _state.update { it.copy(pickerQuery = text) }
        pickerJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(pickerResults = null, pickerSearching = false) }
            return
        }
        pickerJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(pickerSearching = true) }
            runCatching {
                repository.list(includeHidden = true, limit = 50, offset = 0, search = trimmed)
            }
                .onSuccess { page ->
                    _state.update { it.copy(pickerResults = page.items, pickerSearching = false) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    // Sin resultados del servidor, filtra lo ya cargado.
                    _state.update { s ->
                        s.copy(
                            pickerResults = s.people.filter { p ->
                                p.name?.let { foldForMatch(it).contains(foldForMatch(trimmed)) } == true
                            },
                            pickerSearching = false
                        )
                    }
                }
        }
    }

    fun assignToPerson(faceId: String, personId: String) {
        markPending(faceId)
        pickerJob?.cancel()
        _state.update { s ->
            // La elegida puede venir de la búsqueda y no estar en [people]: sin
            // añadirla, la fila de la cara no sabría su nombre.
            val picked = s.pickerResults?.firstOrNull { it.id == personId }
            s.copy(
                assigningFaceId = null,
                people = if (picked != null && s.people.none { it.id == personId }) {
                    s.people + picked
                } else s.people
            )
        }
        viewModelScope.launch {
            runCatching { repository.assignFaceToPerson(faceId = faceId, personId = personId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.map { face ->
                                if (face.id == faceId) face.copy(
                                    personId = personId,
                                    isManuallyAssigned = true,
                                    isRejected = false,
                                    suggestedPersonId = null,
                                    suggestedDistance = null
                                ) else face
                            },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                }
                .onFailure { error -> setError(faceId, error, "No se pudo asignar") }
        }
    }

    fun assignToNewPerson(faceId: String, name: String) {
        if (name.isBlank()) return
        pickerJob?.cancel()
        markPending(faceId)
        _state.update { it.copy(assigningFaceId = null) }
        viewModelScope.launch {
            runCatching {
                repository.assignFaceToNewPerson(faceId = faceId, name = name.trim())
            }
                .onSuccess { response ->
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.map { face ->
                                if (face.id == faceId) face.copy(
                                    personId = response.personId,
                                    isManuallyAssigned = true,
                                    isRejected = false,
                                    suggestedPersonId = null,
                                    suggestedDistance = null
                                ) else face
                            },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                    // La persona nueva no está en la lista: sin recargarla, la
                    // fila de la cara salía "Sin nombre".
                    refreshPeople()
                }
                .onFailure { error -> setError(faceId, error, "No se pudo crear la persona") }
        }
    }

    // --- Per-face actions --------------------------------------------

    fun acceptSuggestion(faceId: String) {
        markPending(faceId)
        viewModelScope.launch {
            runCatching { repository.acceptFaceSuggestion(faceId) }
                .onSuccess { response ->
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.map { face ->
                                if (face.id == faceId) face.copy(
                                    personId = response.personId,
                                    isManuallyAssigned = true,
                                    suggestedPersonId = null,
                                    suggestedDistance = null
                                ) else face
                            },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                }
                .onFailure { error -> setError(faceId, error, "No se pudo aceptar") }
        }
    }

    fun dismissSuggestion(faceId: String) {
        markPending(faceId)
        viewModelScope.launch {
            runCatching { repository.dismissFaceSuggestion(faceId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.map { face ->
                                if (face.id == faceId) face.copy(
                                    suggestedPersonId = null,
                                    suggestedDistance = null
                                ) else face
                            },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                }
                .onFailure { error -> setError(faceId, error, "No se pudo descartar") }
        }
    }

    fun unassign(faceId: String) {
        markPending(faceId)
        viewModelScope.launch {
            runCatching { repository.unassignFace(faceId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.map { face ->
                                if (face.id == faceId) face.copy(
                                    personId = null,
                                    isManuallyAssigned = true
                                ) else face
                            },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                }
                .onFailure { error -> setError(faceId, error, "No se pudo quitar la asignación") }
        }
    }

    fun reject(faceId: String) {
        markPending(faceId)
        viewModelScope.launch {
            runCatching { repository.rejectFace(faceId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            faces = previous.faces.filterNot { it.id == faceId },
                            pendingFaceIds = previous.pendingFaceIds - faceId
                        )
                    }
                }
                .onFailure { error -> setError(faceId, error, "No se pudo rechazar") }
        }
    }

    fun setAsCover(personId: String, faceId: String, onSuccess: () -> Unit = {}) {
        markPending(faceId)
        viewModelScope.launch {
            runCatching { repository.setCoverFace(personId = personId, faceId = faceId) }
                .onSuccess {
                    _state.update { it.copy(pendingFaceIds = it.pendingFaceIds - faceId) }
                    onSuccess()
                }
                .onFailure { error -> setError(faceId, error, "No se pudo establecer la portada") }
        }
    }

    /** Re-fetch the people list. Useful after `assignToNewPerson` succeeds
     *  so the new person shows up in the dialog without reopening the sheet. */
    fun refreshPeople() {
        viewModelScope.launch {
            runCatching { repository.list(includeHidden = true, limit = 200, offset = 0) }
                .onSuccess { page: PeoplePage ->
                    _state.update { it.copy(people = page.items) }
                }
        }
    }

    private fun markPending(faceId: String) {
        _state.update { it.copy(pendingFaceIds = it.pendingFaceIds + faceId) }
    }

    private fun setError(faceId: String, throwable: Throwable, fallback: String) {
        _state.update {
            it.copy(
                pendingFaceIds = it.pendingFaceIds - faceId,
                error = errorFactory.from(throwable, fallback)
            )
        }
    }
}
