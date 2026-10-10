package com.photonne.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ObjectLabel(
    @SerialName("label") val label: String,
    @SerialName("assetCount") val assetCount: Int,
    @SerialName("coverAssetId") val coverAssetId: String? = null
)

@Serializable
data class SceneLabel(
    @SerialName("label") val label: String,
    @SerialName("assetCount") val assetCount: Int,
    @SerialName("coverAssetId") val coverAssetId: String? = null
)

@Serializable
data class Person(
    val id: String,
    val name: String? = null,
    val coverFaceId: String? = null,
    val faceCount: Int = 0,
    val isHidden: Boolean = false,
    val pendingSuggestionsCount: Int = 0
)

@Serializable
data class PeoplePage(
    val total: Int = 0,
    val items: List<Person> = emptyList()
)

@Serializable
data class SearchResponse(
    @SerialName("items") val items: List<TimelineItem> = emptyList(),
    @SerialName("hasMore") val hasMore: Boolean = false
)

@Serializable
data class SemanticSearchItem(
    val score: Float = 0f,
    val asset: TimelineItem
)

@Serializable
data class SemanticSearchResponse(
    val items: List<SemanticSearchItem> = emptyList()
)
