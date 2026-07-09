package com.photonne.app.ui.album.smart

import com.photonne.app.data.models.SmartRule

/** A person the user picked, kept light for chip rendering (face-thumbnail based). */
data class PersonRef(val id: String, val name: String, val coverFaceId: String?)

/** A folder the user picked. [path] is the full virtual path, shown under the
 * name to disambiguate same-named folders in search results. */
data class FolderRef(val id: String, val name: String, val isShared: Boolean, val path: String)

/** A node of the folder tree shown in the picker's browse mode. Built
 * client-side from the flat folder list via parentFolderId. */
data class FolderNode(
    val id: String,
    val name: String,
    val path: String,
    val isShared: Boolean,
    val children: List<FolderNode>,
)

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
        override val key: String = "scenes",
    ) : SmartCondition

    data class Objects(
        val labels: List<String>,
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
        includeSubfolders = true,
    )
    is SmartCondition.DateRange -> SmartRule(type = "dateRange", from = from, to = to)
    is SmartCondition.Scenes -> SmartRule(type = "scene", match = "any", labels = labels)
    is SmartCondition.Objects -> SmartRule(type = "object", match = "any", labels = labels)
}

/**
 * Builds the full rule tree from the editor state: a single logical node whose
 * operator is the top-level "Todas / Cualquiera" toggle, over every non-empty
 * condition. Returns null when there is nothing to resolve.
 */
fun buildSmartRule(conditions: List<SmartCondition>, matchAll: Boolean): SmartRule? {
    val leaves = conditions.filterNot { it.isEmpty }.map { it.toRule() }
    if (leaves.isEmpty()) return null
    return SmartRule(op = if (matchAll) "AND" else "OR", conditions = leaves)
}
