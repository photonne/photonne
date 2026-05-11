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
        pageSize: Int? = null
    ): SearchResponse = api.searchAssets(
        q = query,
        from = from?.toString(),
        to = to?.toString(),
        personIds = personIds,
        objectLabels = objectLabels,
        sceneLabels = sceneLabels,
        pageSize = pageSize
    )

    suspend fun semanticSearch(query: String, limit: Int? = null): SemanticSearchResponse =
        api.semanticSearch(q = query, limit = limit)

    suspend fun objectLabels(limit: Int = 80): List<ObjectLabel> =
        api.listObjectLabels(limit = limit)

    suspend fun sceneLabels(limit: Int = 80): List<SceneLabel> =
        api.listSceneLabels(limit = limit)

    suspend fun people(limit: Int = 80, search: String? = null): List<Person> =
        api.listPeople(search = search, limit = limit).items
}
