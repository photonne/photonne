package com.photonne.app.ui.explore

import com.photonne.app.data.models.SceneLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelSortTest {
    private val labels = listOf(
        SceneLabel(label = "playa", assetCount = 3),
        SceneLabel(label = "Montaña", assetCount = 10),
        SceneLabel(label = "ciudad", assetCount = 7),
    )

    @Test
    fun countPutsTheMostPhotosFirst() {
        assertEquals(
            listOf("Montaña", "ciudad", "playa"),
            labels.sortedLabels(LabelSort.Count, { it.label }, { it.assetCount }).map { it.label }
        )
    }

    @Test
    fun nameIgnoresCase() {
        assertEquals(
            listOf("ciudad", "Montaña", "playa"),
            labels.sortedLabels(LabelSort.Name, { it.label }, { it.assetCount }).map { it.label }
        )
    }
}
