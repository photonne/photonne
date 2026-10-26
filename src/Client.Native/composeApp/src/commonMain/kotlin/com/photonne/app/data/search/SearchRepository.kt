package com.photonne.app.data.search

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.models.SearchResponse
import com.photonne.app.data.models.SemanticSearchResponse
import kotlinx.datetime.LocalDate

class SearchRepository(
    private val api: PhotonneApi
) {
    suspend fun textSearch(
        query: String?,
        from: LocalDate?,
        to: LocalDate?,
        personIds: List<String>,
        objectLabels: List<String>,
        sceneLabels: List<String>,
        ocrText: String? = null,
        mediaType: String? = null,
        pageSize: Int? = null,
        offset: Int? = null,
        pinned: PinnedSearchFilter? = null
    ): SearchResponse = api.searchAssets(
        q = query,
        from = from?.toString(),
        to = to?.toString(),
        personIds = personIds,
        objectLabels = objectLabels,
        sceneLabels = sceneLabels,
        textQuery = ocrText,
        mediaType = mediaType,
        pageSize = pageSize,
        offset = offset,
        tag = (pinned as? PinnedSearchFilter.Tag)?.name,
        cameraMake = (pinned as? PinnedSearchFilter.Camera)?.make,
        cameraModel = (pinned as? PinnedSearchFilter.Camera)?.model,
        placeId = (pinned as? PinnedSearchFilter.Place)?.id
    )

    suspend fun semanticSearch(query: String, limit: Int? = null): SemanticSearchResponse =
        api.semanticSearch(q = query, limit = limit)

    suspend fun objectLabels(limit: Int = 80, search: String? = null): List<ObjectLabel> =
        api.listObjectLabels(q = search, limit = limit)

    suspend fun sceneLabels(limit: Int = 80, search: String? = null): List<SceneLabel> =
        api.listSceneLabels(q = search, limit = limit)

    suspend fun places(limit: Int = 12): List<com.photonne.app.data.models.PlaceFacet> =
        api.listPlaces(limit = limit)

    suspend fun people(limit: Int = 80, search: String? = null): List<Person> =
        api.listPeople(search = search, limit = limit).items
}

/**
 * A filter the viewer pins on a search when one of its details is tapped: the
 * photos with a tag, taken with a camera, or taken at a place. There is no
 * control for it in the filters sheet; its chip takes it off.
 */
sealed interface PinnedSearchFilter {
    /** What its chip says. */
    val label: String

    data class Tag(val name: String) : PinnedSearchFilter {
        override val label: String get() = "#$name"
    }

    data class Camera(val make: String?, val model: String) : PinnedSearchFilter {
        override val label: String get() = listOfNotNull(make, model).joinToString(" ")
    }

    data class Place(val id: String, val name: String) : PinnedSearchFilter {
        override val label: String get() = name
    }
}
