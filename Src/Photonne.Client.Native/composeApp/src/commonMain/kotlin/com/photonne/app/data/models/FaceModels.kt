package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * Per-user face row served by `/api/assets/{id}/faces`. Identity fields
 * (`personId`, suggestion, manual/rejected flags) come from the caller's
 * `UserFaceAssignment`, so two users seeing the same shared asset get
 * different states.
 */
@Serializable
data class Face(
    val id: String,
    val assetId: String,
    val personId: String? = null,
    val boundingBoxX: Float = 0f,
    val boundingBoxY: Float = 0f,
    val boundingBoxW: Float = 0f,
    val boundingBoxH: Float = 0f,
    val confidence: Float = 0f,
    val isManuallyAssigned: Boolean = false,
    val isRejected: Boolean = false,
    val suggestedPersonId: String? = null,
    val suggestedDistance: Float? = null
)

/** Pending face suggestion as exposed by `/api/people/{id}/suggestions`. */
@Serializable
data class PersonSuggestion(
    val id: String,
    val assetId: String,
    val confidence: Float = 0f,
    val suggestedDistance: Float? = null
)

@Serializable
data class PersonSuggestionsPage(
    val total: Int = 0,
    val items: List<PersonSuggestion> = emptyList()
)

/** Confirmed face attached to a person, served by `/api/people/{id}/faces`. */
@Serializable
data class PersonFace(
    val id: String,
    val assetId: String,
    val confidence: Float = 0f,
    val isManuallyAssigned: Boolean = false
)

@Serializable
data class PersonFacesPage(
    val total: Int = 0,
    val items: List<PersonFace> = emptyList()
)

@Serializable
data class BulkSuggestionResult(val affected: Int = 0)

@Serializable
data class UnlinkAssetResponse(val facesDetached: Int = 0)

@Serializable
data class ReclusterResponse(val personsCreated: Int = 0)

@Serializable
data class AssignFaceResponse(val id: String, val personId: String? = null)
