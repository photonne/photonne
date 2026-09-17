package com.photonne.app.ui.asset

import com.photonne.app.data.api.EnrichmentTaskDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The "Analizar con IA" sheet reads five statuses off an asset's enrichment
 * rows. The rules worth pinning: every analysis gets a row even when the
 * server never queued it, and an asset that was retried shows its latest
 * attempt, not its first.
 */
class AiAnalysisRowsTest {

    private val now = 1_800_000_000_000L

    private fun iso(offsetSeconds: Long): String =
        Instant.fromEpochMilliseconds(now + offsetSeconds * 1000L).toString()

    private fun task(
        type: String,
        status: String,
        createdAt: Long = -600,
        completedAt: Long? = null,
        nextRetryAt: Long? = null,
        error: String? = null,
    ) = EnrichmentTaskDto(
        taskType = type,
        status = status,
        errorMessage = error,
        createdAt = iso(createdAt),
        completedAt = completedAt?.let(::iso),
        nextRetryAt = nextRetryAt?.let(::iso),
    )

    @Test
    fun everyAnalysisGetsARow_evenWithNoTasksAtAll() {
        val rows = aiAnalysisRows(emptyList(), now)

        assertEquals(AiAnalysis.entries, rows.map { it.analysis })
        assertTrue(rows.all { it.status == AiAnalysisStatus.Never })
        assertFalse(rows.any { it.isBusy })
    }

    @Test
    fun theLatestAttemptWins() {
        // A failed first attempt and a later successful one: the row says done.
        val rows = aiAnalysisRows(
            listOf(
                task("FaceRecognition", "Failed", createdAt = -7200, error = "boom"),
                task("FaceRecognition", "Completed", createdAt = -120, completedAt = -60),
            ),
            now,
        )

        val faces = rows.single { it.analysis == AiAnalysis.Faces }
        val done = assertIs<AiAnalysisStatus.Done>(faces.status)
        assertEquals(60, done.agoSeconds)
    }

    @Test
    fun queuedAndRunningAreBusy_andNothingElseIs() {
        val rows = aiAnalysisRows(
            listOf(
                task("ObjectDetection", "Pending"),
                task("SceneClassification", "Processing"),
                task("TextRecognition", "Suppressed"),
            ),
            now,
        )

        assertTrue(rows.single { it.analysis == AiAnalysis.Objects }.isBusy)
        assertTrue(rows.single { it.analysis == AiAnalysis.Scenes }.isBusy)
        assertEquals(AiAnalysisStatus.Suppressed, rows.single { it.analysis == AiAnalysis.Text }.status)
        assertFalse(rows.single { it.analysis == AiAnalysis.Embeddings }.isBusy)
    }

    @Test
    fun aFailureKeepsItsReasonAndWhetherARetryIsComing() {
        val rows = aiAnalysisRows(
            listOf(
                task("TextRecognition", "Failed", error = "imagen ilegible", nextRetryAt = 300),
                task("ImageEmbedding", "Failed", error = "  "),
            ),
            now,
        )

        val text = assertIs<AiAnalysisStatus.Failed>(rows.single { it.analysis == AiAnalysis.Text }.status)
        assertEquals("imagen ilegible", text.message)
        assertEquals(300, text.retryInSeconds)

        val embeddings = assertIs<AiAnalysisStatus.Failed>(rows.single { it.analysis == AiAnalysis.Embeddings }.status)
        assertEquals(null, embeddings.message)
        assertEquals(null, embeddings.retryInSeconds)
    }

    @Test
    fun taskTypeMatchingIgnoresCase() {
        val rows = aiAnalysisRows(listOf(task("facerecognition", "Completed", completedAt = -5)), now)

        assertIs<AiAnalysisStatus.Done>(rows.single { it.analysis == AiAnalysis.Faces }.status)
    }
}
