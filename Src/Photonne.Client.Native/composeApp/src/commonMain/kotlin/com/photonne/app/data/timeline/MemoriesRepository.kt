package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.di.PhotonneAppConfig
import kotlinx.datetime.Instant

class MemoriesRepository(
    private val api: PhotonneApi,
    private val config: PhotonneAppConfig
) {
    /**
     * The timeline strip's source: a live "on this day" query. Deliberately NOT
     * the generated feed — the strip only ever shows today's anniversaries, and
     * this way it keeps working the moment a photo is added, without waiting for
     * the nightly pass.
     */
    suspend fun list(): List<TimelineItem> {
        if (config.useFakeMemories) return fakeMemoriesFromTimeline()
        return api.getMemories()
    }

    /**
     * The Recuerdos section's source: every generated memory, best first.
     *
     * Asks for the server's maximum rather than its default 50: the section folds
     * these into a row per theme, and a feed truncated by score cuts across those
     * rows instead of along them — losing 2019 from the middle of "Días de playa"
     * with nothing on screen to show it happened.
     */
    suspend fun feed(kind: String? = null): List<Memory> = api.getMemoryFeed(kind, limit = 500)

    suspend fun detail(id: String): MemoryDetail = api.getMemory(id)

    private suspend fun fakeMemoriesFromTimeline(): List<TimelineItem> {
        val total = FAKE_GROUPS.sumOf { it.count }
        val page = api.getTimeline(cursor = null, pageSize = total)
        val source = page.items.take(total)
        if (source.isEmpty()) return emptyList()

        val anchor = source.first().fileCreatedAt
        val secondsPerYear = 365L * 24 * 3600
        val result = mutableListOf<TimelineItem>()
        var sourceIdx = 0
        for (group in FAKE_GROUPS) {
            val shifted = Instant.fromEpochSeconds(
                anchor.epochSeconds - group.yearsAgo * secondsPerYear
            )
            repeat(group.count) {
                val item = source.getOrNull(sourceIdx) ?: return@repeat
                result += item.copy(fileCreatedAt = shifted)
                sourceIdx++
            }
        }
        return result
    }

    private data class FakeGroup(val yearsAgo: Int, val count: Int)

    private companion object {
        // Same-day clusters to exercise the grouping in MemoriesStrip.
        val FAKE_GROUPS = listOf(
            FakeGroup(yearsAgo = 1, count = 3),
            FakeGroup(yearsAgo = 2, count = 1),
            FakeGroup(yearsAgo = 4, count = 2),
            FakeGroup(yearsAgo = 6, count = 1),
        )
    }
}
