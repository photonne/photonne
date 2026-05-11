package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.Person
import com.photonne.app.data.people.PeopleRepository
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
    val errorMessage: String? = null,
    val pendingFaceIds: Set<String> = emptySet(),
    /** Face id for which the user is currently picking a person (assign or set-cover). */
    val assigningFaceId: String? = null
) {
    fun personById(personId: String?): Person? =
        personId?.let { id -> people.firstOrNull { it.id == id } }
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
    private val repository: PeopleRepository
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
                            errorMessage = error.message ?: "Failed to load faces"
                        )
                    }
                }
        }
    }

    fun close() {
        _state.value = AssetFacesUiState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // --- Pick-a-person flow (assign or set cover) --------------------

    fun startAssigning(faceId: String) {
        _state.update { it.copy(assigningFaceId = faceId) }
    }

    fun cancelAssigning() {
        _state.update { it.copy(assigningFaceId = null) }
    }

    fun assignToPerson(faceId: String, personId: String) {
        markPending(faceId)
        _state.update { it.copy(assigningFaceId = null) }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to assign") }
        }
    }

    fun assignToNewPerson(faceId: String, name: String) {
        if (name.isBlank()) return
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
                }
                .onFailure { error -> setError(faceId, error.message ?: "Failed to create person") }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to accept") }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to dismiss") }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to unassign") }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to reject") }
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
                .onFailure { error -> setError(faceId, error.message ?: "Failed to set cover") }
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

    private fun setError(faceId: String, message: String) {
        _state.update {
            it.copy(
                pendingFaceIds = it.pendingFaceIds - faceId,
                errorMessage = message
            )
        }
    }
}
