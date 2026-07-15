package com.photonne.app.ui.admin

import com.russhwolf.settings.Settings

/**
 * Which task groups the admin left open. Persisted rather than remembered:
 * this screen is one you leave and come back to — you kick off a backfill,
 * go check the timeline, come back to see how it's going — and a hub that
 * re-collapsed itself every time would make the grouping a chore instead of
 * a shortcut.
 */
internal const val ADMIN_TASKS_EXPANDED_KEY = "photonne.admin.tasks.expanded"

/**
 * Open on a fresh install: the group you want the day you set the server up,
 * and the only one whose tasks you run routinely. Everything else is a
 * reaction to a problem, so it stays folded until you go looking.
 */
internal val DefaultExpandedSections = setOf(AdminRunTaskSection.NewPhotos)

/**
 * Parse the stored value. Unknown names are dropped rather than failing the
 * read: sections get renamed, and a stale preference should cost you a fold,
 * not the screen.
 *
 * Kept free of [Settings] — like [parseSortDirection] over in FolderSorting —
 * so the rules that are easy to get wrong can be tested without a fake store.
 */
internal fun parseExpandedSections(raw: String?): Set<AdminRunTaskSection> {
    if (raw == null) return DefaultExpandedSections
    // Deliberately distinct from "nothing stored": an admin who folds every
    // group means it, and must not get NewPhotos back on the next visit.
    if (raw.isBlank()) return emptySet()
    return raw.split(',')
        .mapNotNull { name -> runCatching { AdminRunTaskSection.valueOf(name.trim()) }.getOrNull() }
        .toSet()
}

internal fun serializeExpandedSections(sections: Set<AdminRunTaskSection>): String =
    sections.joinToString(",") { it.name }

internal fun readExpandedSections(settings: Settings): Set<AdminRunTaskSection> =
    parseExpandedSections(settings.getStringOrNull(ADMIN_TASKS_EXPANDED_KEY))

internal fun writeExpandedSections(settings: Settings, sections: Set<AdminRunTaskSection>) {
    settings.putString(ADMIN_TASKS_EXPANDED_KEY, serializeExpandedSections(sections))
}

/** The tasks in a group, in enum order — which is the order they read in. */
internal fun tasksOf(section: AdminRunTaskSection): List<AdminRunTask> =
    AdminRunTask.entries.filter { it.section == section }
