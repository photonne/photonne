package com.photonne.app.ui.album.smart

import com.photonne.app.data.models.SmartAlbumRuleDetails
import com.photonne.app.data.models.SmartRule
import com.photonne.app.data.models.SmartRuleFolderRef
import com.photonne.app.data.models.SmartRulePersonRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Editing a smart album rebuilds the editor from the stored rule and saves it
 * back. What must hold: a rule made here survives the round trip unchanged, and
 * whatever this editor can't show (web-only conditions, a single-leaf root)
 * is carried through instead of being dropped on save.
 */
class ParseSmartRuleTest {

    private fun details(rule: SmartRule) = SmartAlbumRuleDetails(
        rule = rule,
        people = listOf(SmartRulePersonRef("p1", "Abuela", "f1")),
        folders = listOf(SmartRuleFolderRef("d1", "Viajes", "/assets/users/marc/Viajes")),
    )

    @Test
    fun ruleFromThisEditor_roundTripsUnchanged() {
        val conditions = listOf(
            SmartCondition.People(listOf(PersonRef("p1", "Abuela", "f1")), matchAll = true),
            SmartCondition.Folders(listOf(FolderRef("d1", "Viajes", false, "/assets/users/marc/Viajes"))),
            SmartCondition.DateRange("2024-01-01", null),
            SmartCondition.Scenes(listOf("beach")),
            SmartCondition.MediaType(SmartCondition.MediaType.VIDEO),
        )
        val rule = buildSmartRule(conditions, matchAll = false)!!

        val parsed = parseSmartRule(details(rule), "?")

        assertFalse(parsed.matchAll)
        assertEquals(conditions, parsed.conditions)
        assertTrue(parsed.preserved.isEmpty())
        assertEquals(rule, buildSmartRule(parsed.conditions, parsed.matchAll, parsed.preserved))
    }

    @Test
    fun webOnlyConditions_arePreservedOnSave() {
        val favorite = SmartRule(type = "favorite", value = true)
        val notScene = SmartRule(type = "not", condition = SmartRule(type = "scene", labels = listOf("food")))
        val rule = SmartRule(
            op = "AND",
            conditions = listOf(
                SmartRule(type = "scene", match = "all", labels = listOf("beach", "sunset")),
                favorite,
                notScene,
            ),
        )

        val parsed = parseSmartRule(details(rule), "?")

        val scenes = assertIs<SmartCondition.Scenes>(parsed.conditions.single())
        assertTrue(scenes.matchAll)
        assertEquals(listOf(favorite, notScene), parsed.preserved)
        assertEquals(rule, buildSmartRule(parsed.conditions, parsed.matchAll, parsed.preserved))
    }

    @Test
    fun singleLeafRoot_andMissingNames_stillEditable() {
        // The web sends a bare leaf when there is only one condition.
        val rule = SmartRule(type = "folder", folderIds = listOf("d1", "gone"), includeSubfolders = false)

        val parsed = parseSmartRule(details(rule), "(ya no existe)")

        assertTrue(parsed.matchAll)
        val folders = assertIs<SmartCondition.Folders>(parsed.conditions.single())
        assertFalse(folders.includeSubfolders)
        assertEquals(listOf("Viajes", "(ya no existe)"), folders.folders.map { it.name })
    }

    @Test
    fun mediaType_readsAnyCase_andKeepsAnUnknownValue() {
        val unknown = SmartRule(type = "mediaType", mediaType = "Gif")
        val rule = SmartRule(
            op = "AND",
            conditions = listOf(SmartRule(type = "MEDIATYPE", mediaType = "video"), unknown),
        )

        val parsed = parseSmartRule(details(rule), "?")

        val media = assertIs<SmartCondition.MediaType>(parsed.conditions.single())
        assertTrue(media.isVideo)
        assertEquals(SmartRule(type = "mediaType", mediaType = "Video"),
            buildSmartRule(parsed.conditions, matchAll = true)!!.conditions!!.single())
        assertEquals(listOf(unknown), parsed.preserved)
    }

    @Test
    fun rollingWindow_roundTripsAsLast() {
        val window = SmartCondition.DateRange(from = null, to = null, window = RollingWindow(6, RollingWindow.Period.Months))
        val rule = buildSmartRule(listOf(window), matchAll = true)!!

        assertEquals(SmartRule(type = "dateRange", last = "6m"), rule.conditions!!.single())
        val parsed = parseSmartRule(details(rule), "?")
        assertEquals(listOf<SmartCondition>(window), parsed.conditions)
    }

    @Test
    fun unreadableWindow_isPreserved() {
        val odd = SmartRule(type = "dateRange", last = "2w")
        val parsed = parseSmartRule(details(SmartRule(op = "AND", conditions = listOf(odd))), "?")

        assertTrue(parsed.conditions.isEmpty())
        assertEquals(listOf(odd), parsed.preserved)
    }

    @Test
    fun rollingWindow_parsesOnlyWhatTheServerAccepts() {
        assertEquals(RollingWindow(90, RollingWindow.Period.Days), RollingWindow.parse("90d"))
        assertEquals(RollingWindow(1, RollingWindow.Period.Years), RollingWindow.parse(" 1y "))
        listOf("0d", "90", "-3m", "2w", "10000d", "05d").forEach { assertEquals(null, RollingWindow.parse(it), it) }
    }
}
