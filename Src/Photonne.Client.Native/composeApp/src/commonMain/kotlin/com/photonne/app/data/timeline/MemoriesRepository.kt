package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.di.PhotonneAppConfig
import kotlinx.datetime.Instant

class MemoriesRepository(
    private val api: PhotonneApi,
    private val config: PhotonneAppConfig
) {
    suspend fun list(): List<TimelineItem> {
        if (config.useFakeMemories) return fakeMemoriesFromTimeline()
        return api.getMemories()
    }

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
