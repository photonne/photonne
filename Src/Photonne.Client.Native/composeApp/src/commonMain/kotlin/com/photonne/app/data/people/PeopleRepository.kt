package com.photonne.app.data.people

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssignFaceResponse
import com.photonne.app.data.models.BulkSuggestionResult
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.PersonAssetsPage
import com.photonne.app.data.models.PersonFacesPage
import com.photonne.app.data.models.PersonSuggestionsPage
import com.photonne.app.data.models.ReclusterResponse
import com.photonne.app.data.models.UnlinkAssetResponse

class PeopleRepository(
    private val api: PhotonneApi
) {
    suspend fun list(
        includeHidden: Boolean = false,
        limit: Int = 80,
        offset: Int = 0,
        search: String? = null
    ): PeoplePage = api.listPeople(
        search = search,
        includeHidden = includeHidden,
        limit = limit,
        offset = offset
    )

    suspend fun rename(personId: String, name: String?) {
        api.renamePerson(personId, name)
    }

    suspend fun hide(personId: String) {
        api.hidePerson(personId)
    }

    suspend fun unhide(personId: String) {
        api.unhidePerson(personId)
    }

    suspend fun assets(
        personId: String,
        limit: Int = 80,
        offset: Int = 0
    ): PersonAssetsPage = api.getPersonAssets(
        personId = personId,
        limit = limit,
        offset = offset
    )

    // --- Suggestions -------------------------------------------------

    suspend fun suggestions(
        personId: String,
        limit: Int = 30,
        offset: Int = 0
    ): PersonSuggestionsPage = api.listPersonSuggestions(personId, limit, offset)

    suspend fun acceptAllSuggestions(personId: String): BulkSuggestionResult =
        api.acceptAllPersonSuggestions(personId)

    suspend fun dismissAllSuggestions(personId: String): BulkSuggestionResult =
        api.dismissAllPersonSuggestions(personId)

    suspend fun acceptFaceSuggestion(faceId: String): AssignFaceResponse =
        api.acceptFaceSuggestion(faceId)

    suspend fun dismissFaceSuggestion(faceId: String) {
        api.dismissFaceSuggestion(faceId)
    }

    // --- Faces -------------------------------------------------------

    suspend fun assetFaces(assetId: String): List<Face> = api.getAssetFaces(assetId)

    suspend fun personFaces(
        personId: String,
        limit: Int = 60,
        offset: Int = 0
    ): PersonFacesPage = api.listPersonFaces(personId, limit, offset)

    suspend fun assignFaceToPerson(faceId: String, personId: String): AssignFaceResponse =
        api.assignFace(faceId = faceId, personId = personId)

    suspend fun assignFaceToNewPerson(faceId: String, name: String): AssignFaceResponse =
        api.assignFace(faceId = faceId, newPersonName = name)

    suspend fun rejectFace(faceId: String) {
        api.rejectFace(faceId)
    }

    suspend fun unassignFace(faceId: String) {
        api.unassignFace(faceId)
    }

    suspend fun setCoverFace(personId: String, faceId: String) {
        api.setPersonCoverFace(personId, faceId)
    }

    // --- Merge / recluster / unlink ----------------------------------

    suspend fun merge(targetPersonId: String, sourcePersonId: String) {
        api.mergePeople(targetPersonId, sourcePersonId)
    }

    suspend fun recluster(): ReclusterResponse = api.reclusterPeople()

    suspend fun unlinkAsset(personId: String, assetId: String): UnlinkAssetResponse =
        api.unlinkAssetFromPerson(personId, assetId)
}
