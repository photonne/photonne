package com.photonne.app.ui.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Run Tasks catalogue. Pure data, no UI — but the parts worth guarding are
 * the ones a compiler can't see: a task silently landing in no group, a kind
 * misspelled so its button does nothing, or a destructive task losing its
 * confirmation because someone added a row above it.
 */
class AdminRunTaskCatalogTest {

    /** The kinds MaintenanceService.Run() actually answers to (server-side
     *  switch, MaintenanceService.cs). A typo here is invisible at runtime: the
     *  server registers a task and kills it with "unknown kind", which from the
     *  hub looks exactly like a button that does nothing. */
    private val serverMaintenanceKinds = setOf(
        "orphan-thumbnails",
        "missing-files",
        "recalculate-sizes",
        "empty-trash",
        "purge-missing",
        "interpolate-locations",
        "reverse-geocode",
        "detect-trips",
        "generate-memories",
    )

    @Test
    fun everyTaskBelongsToExactlyOneGroup() {
        val grouped = AdminRunTaskSection.entries.flatMap { tasksOf(it) }

        assertEquals(AdminRunTask.entries.size, grouped.size, "una tarea está en dos grupos o en ninguno")
        assertEquals(AdminRunTask.entries.toSet(), grouped.toSet())
    }

    @Test
    fun noGroupIsEmpty() {
        // An empty group renders as a header that folds open onto nothing.
        for (section in AdminRunTaskSection.entries) {
            assertTrue(tasksOf(section).isNotEmpty(), "$section no tiene tareas")
        }
    }

    @Test
    fun theTwentyTasksAreThere() {
        assertEquals(20, AdminRunTask.entries.size)
    }

    @Test
    fun everyMaintenanceKindMatchesTheServer() {
        val client = AdminRunTask.entries.mapNotNull { it.maintenanceKind }

        assertEquals(client.size, client.toSet().size, "dos tareas comparten el mismo kind")
        assertEquals(serverMaintenanceKinds, client.toSet())
    }

    @Test
    fun onlyTheTwoIrreversibleTasksAskFirst() {
        val destructive = AdminRunTask.entries.filter { it.isDestructive }.toSet()

        assertEquals(setOf(AdminRunTask.EmptyTrash, AdminRunTask.PurgeMissing), destructive)
    }

    @Test
    fun destructiveTasksLiveInCleanup() {
        // The point of the group: you can see what deletes without reading rows.
        for (task in AdminRunTask.entries.filter { it.isDestructive }) {
            assertEquals(AdminRunTaskSection.Cleanup, task.section)
        }
    }

    @Test
    fun progressKeyIsUniqueAmongTasksThatReportProgress() {
        // Two tasks sharing a key would paint each other's progress bar. This is
        // exactly what keying on backgroundType did: all nine maintenance kinds
        // report as type "Maintenance".
        val keys = AdminRunTask.entries.mapNotNull { it.progressKey }

        assertEquals(keys.size, keys.toSet().size, "dos tareas comparten clave de progreso")
    }

    @Test
    fun maintenanceTasksAreKeyedByKindNotByType() {
        val maintenance = AdminRunTask.entries.filter { it.maintenanceKind != null }

        assertEquals(9, maintenance.size)
        for (task in maintenance) {
            assertEquals(task.maintenanceKind, task.progressKey)
        }
    }

    @Test
    fun mlTasksHaveNoProgressKey() {
        // They have no single "run" to follow — their work is a queue of
        // per-asset jobs — so they must not claim a task entry's progress.
        for (task in AdminRunTask.entries.filter { it.section == AdminRunTaskSection.Ai }) {
            assertNull(task.progressKey, "${task.name} no debería tener clave de progreso")
        }
    }

    @Test
    fun aFreshInstallOpensOnlyNewPhotos() {
        assertEquals(DefaultExpandedSections, parseExpandedSections(null))
        assertEquals(setOf(AdminRunTaskSection.NewPhotos), DefaultExpandedSections)
    }

    @Test
    fun theOpenGroupsSurviveARoundTrip() {
        val chosen = setOf(AdminRunTaskSection.Cleanup, AdminRunTaskSection.Ai)

        assertEquals(chosen, parseExpandedSections(serializeExpandedSections(chosen)))
    }

    @Test
    fun foldingEverythingIsRemembered() {
        // Distinct from "nothing stored yet": an admin who folded every group
        // means it, and must not get NewPhotos back on the next visit.
        assertEquals(emptySet(), parseExpandedSections(serializeExpandedSections(emptySet())))
    }

    @Test
    fun aRenamedGroupCostsAFoldNotTheScreen() {
        assertEquals(
            setOf(AdminRunTaskSection.Cleanup),
            parseExpandedSections("Cleanup,SomeGroupWeDeleted")
        )
    }
}
