package com.photonne.app.ui.album.smart

import com.photonne.app.data.models.SmartAlbumRuleDetails
import com.photonne.app.data.models.SmartRule
import com.photonne.app.data.models.SmartRuleFolderRef
import com.photonne.app.data.models.SmartRulePersonRef

/** A person the user picked, kept light for chip rendering (face-thumbnail based). */
data class PersonRef(val id: String, val name: String, val coverFaceId: String?)

/** A folder the user picked. [path] is the full virtual path, shown under the
 * name to disambiguate same-named folders in search results. */
data class FolderRef(val id: String, val name: String, val isShared: Boolean, val path: String)

/** A scene/object label in the picker; carries a cover for the thumbnail row.
 * The condition itself only keeps the label string (the wire model needs no more). */
data class LabelRef(val label: String, val coverAssetId: String?)

/**
 * One row in the smart-album editor. Each variant maps to a leaf
 * [SmartRule] condition (docs/smart-albums/rule-schema.md). [key] is a stable
 * identity for list/animation and for "replace the condition of this kind".
 */
sealed interface SmartCondition {
    val key: String

    data class People(
        val people: List<PersonRef>,
        /** false = "appears any of them" (default), true = "all together". */
        val matchAll: Boolean = false,
        override val key: String = "people",
    ) : SmartCondition

    data class Folders(
        val folders: List<FolderRef>,
        /** Always true from this editor; kept so a web-made "sin subcarpetas" survives an edit. */
        val includeSubfolders: Boolean = true,
        override val key: String = "folders",
    ) : SmartCondition

    data class DateRange(
        /** ISO date "yyyy-MM-dd"; either bound may be null (open-ended). */
        val from: String?,
        val to: String?,
        override val key: String = "dateRange",
    ) : SmartCondition

    data class Scenes(
        val labels: List<String>,
        /** false = any label (this editor's default); true = all, only from a web-made rule. */
        val matchAll: Boolean = false,
        override val key: String = "scenes",
    ) : SmartCondition

    data class Objects(
        val labels: List<String>,
        /** false = any label (this editor's default); true = all, only from a web-made rule. */
        val matchAll: Boolean = false,
        override val key: String = "objects",
    ) : SmartCondition

    /** True when this condition carries no selection yet (nothing to send). */
    val isEmpty: Boolean
        get() = when (this) {
            is People -> people.isEmpty()
            is Folders -> folders.isEmpty()
            is DateRange -> from == null && to == null
            is Scenes -> labels.isEmpty()
            is Objects -> labels.isEmpty()
        }
}

/** Converts one editor condition to its wire [SmartRule] leaf. */
private fun SmartCondition.toRule(): SmartRule = when (this) {
    is SmartCondition.People -> SmartRule(
        type = "person",
        match = if (matchAll) "all" else "any",
        personIds = people.map { it.id },
    )
    is SmartCondition.Folders -> SmartRule(
        type = "folder",
        folderIds = folders.map { it.id },
        includeSubfolders = includeSubfolders,
    )
    is SmartCondition.DateRange -> SmartRule(type = "dateRange", from = from, to = to)
    is SmartCondition.Scenes -> SmartRule(type = "scene", match = if (matchAll) "all" else "any", labels = labels)
    is SmartCondition.Objects -> SmartRule(type = "object", match = if (matchAll) "all" else "any", labels = labels)
}

/**
 * Builds the full rule tree from the editor state (plus any [preserved] nodes
 * this editor can't show — see [parseSmartRule]): a single logical node whose
 * operator is the top-level "Todas / Cualquiera" toggle, over every non-empty
 * condition. Returns null when there is nothing to resolve.
 */
fun buildSmartRule(
    conditions: List<SmartCondition>,
    matchAll: Boolean,
    preserved: List<SmartRule> = emptyList(),
): SmartRule? {
    val leaves = conditions.filterNot { it.isEmpty }.map { it.toRule() } + preserved
    if (leaves.isEmpty()) return null
    return SmartRule(op = if (matchAll) "AND" else "OR", conditions = leaves)
}

/** A stored rule split into what this editor can show and what it must carry through untouched. */
data class ParsedSmartRule(
    val matchAll: Boolean,
    val conditions: List<SmartCondition>,
    /** Nodes this editor has no row for (favorite, mediaType, text, "not", nested
     *  groups, a second condition of a kind…): re-emitted verbatim on save so
     *  editing on the phone never drops what was set up on the web. */
    val preserved: List<SmartRule>,
)

/**
 * Inverse of [buildSmartRule] for editing a saved album. The root is either a
 * logical node over leaves (this editor, the web with 2+ conditions) or a bare
 * leaf (the web with a single condition). Ids are hydrated to chips with the
 * names from `GET /api/albums/{id}/rule`; an id with no name (person deleted,
 * folder gone) falls back to a placeholder so it can still be removed.
 */
fun parseSmartRule(details: SmartAlbumRuleDetails, unknownName: String): ParsedSmartRule {
    val root = details.rule
    val isLogical = root.op != null
    val children = if (isLogical) root.conditions.orEmpty() else listOf(root)
    val people = details.people.associateBy { it.id }
    val folders = details.folders.associateBy { it.id }

    val conditions = mutableListOf<SmartCondition>()
    val preserved = mutableListOf<SmartRule>()
    for (node in children) {
        val condition = node.toCondition(people, folders, unknownName)
        if (condition == null || conditions.any { it.key == condition.key }) preserved += node
        else conditions += condition
    }
    return ParsedSmartRule(
        matchAll = !isLogical || !root.op.equals("OR", ignoreCase = true),
        conditions = conditions,
        preserved = preserved,
    )
}

private fun SmartRule.toCondition(
    people: Map<String, SmartRulePersonRef>,
    folders: Map<String, SmartRuleFolderRef>,
    unknownName: String,
): SmartCondition? {
    if (op != null) return null
    val all = match.equals("all", ignoreCase = true)
    return when (type?.lowercase()) {
        "person" -> SmartCondition.People(
            people = personIds.orEmpty().map { id ->
                val p = people[id]
                PersonRef(id, p?.name?.takeIf { it.isNotBlank() } ?: unknownName, p?.coverFaceId)
            },
            matchAll = all,
        )
        "folder" -> SmartCondition.Folders(
            folders = folderIds.orEmpty().map { id ->
                val f = folders[id]
                FolderRef(id, f?.name ?: unknownName, f?.isShared ?: false, f?.path ?: "")
            },
            includeSubfolders = includeSubfolders ?: true,
        )
        // The server binds these to DateTime; keep only the date part the picker edits.
        "daterange" -> SmartCondition.DateRange(from = from?.take(10), to = to?.take(10))
        "scene" -> SmartCondition.Scenes(labels.orEmpty(), matchAll = all)
        "object" -> SmartCondition.Objects(labels.orEmpty(), matchAll = all)
        else -> null
    }?.takeUnless { it.isEmpty }
}
