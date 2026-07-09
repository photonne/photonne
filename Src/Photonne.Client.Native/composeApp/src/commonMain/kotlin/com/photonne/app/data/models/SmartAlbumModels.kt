package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * Wire representation of a smart-album rule node, mirroring the server's
 * SmartRuleNode (docs/smart-albums/rule-schema.md). One node is either LOGICAL
 * ([op] + [conditions]) or a CONDITION leaf ([type] + payload). Every field is
 * nullable with a null default so that — combined with the client's
 * `explicitNulls = false` Json config — only the fields relevant to a given
 * node are emitted.
 */
@Serializable
data class SmartRule(
    val op: String? = null,
    val conditions: List<SmartRule>? = null,
    val type: String? = null,
    val condition: SmartRule? = null,
    val match: String? = null,
    val personIds: List<String>? = null,
    val folderIds: List<String>? = null,
    val includeSubfolders: Boolean? = null,
    // ISO-8601 dates ("2024-01-01"); the server binds them to DateTime?.
    val from: String? = null,
    val to: String? = null,
    val labels: List<String>? = null,
    val userTagIds: List<String>? = null,
    val tagType: String? = null,
    val value: Boolean? = null,
    val mediaType: String? = null,
    val query: String? = null,
)

/** Result of a dry-run rule resolution (`POST /api/albums/preview`). */
@Serializable
data class SmartAlbumPreview(
    val count: Int = 0,
    val sampleAssetIds: List<String> = emptyList(),
)
