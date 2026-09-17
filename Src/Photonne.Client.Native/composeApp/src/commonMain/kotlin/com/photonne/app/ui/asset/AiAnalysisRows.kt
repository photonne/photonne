package com.photonne.app.ui.asset

import com.photonne.app.data.api.EnrichmentTaskDto
import kotlin.time.Instant

/** The five per-photo AI passes, by the task type name the server uses. */
enum class AiAnalysis(val taskType: String) {
    Faces("FaceRecognition"),
    Objects("ObjectDetection"),
    Scenes("SceneClassification"),
    Text("TextRecognition"),
    Embeddings("ImageEmbedding"),
}

/** What the latest attempt of one analysis is doing, as the sheet reports it. */
sealed interface AiAnalysisStatus {
    data object Never : AiAnalysisStatus
    data object Queued : AiAnalysisStatus
    data object Running : AiAnalysisStatus
    data class Done(val agoSeconds: Long) : AiAnalysisStatus
    /** [retryInSeconds] is set while the worker still plans another attempt;
     *  null once it has given up (or when the server didn't say). */
    data class Failed(val message: String?, val retryInSeconds: Long?) : AiAnalysisStatus
    data object Suppressed : AiAnalysisStatus
}

data class AiAnalysisRow(val analysis: AiAnalysis, val status: AiAnalysisStatus) {
    /** Queued or running: the sheet keeps polling and hides the button. */
    val isBusy: Boolean
        get() = status is AiAnalysisStatus.Queued || status is AiAnalysisStatus.Running
}

/**
 * One row per analysis from the asset's enrichment tasks. An asset keeps one
 * task row per attempt ever made, so each analysis is read off its latest
 * row; types the server never queued show as never analysed. Pure, so the
 * status wording and the "latest wins" rule can be tested without a sheet.
 */
fun aiAnalysisRows(tasks: List<EnrichmentTaskDto>, nowMs: Long): List<AiAnalysisRow> =
    AiAnalysis.entries.map { analysis ->
        val latest = tasks
            .filter { it.taskType.equals(analysis.taskType, ignoreCase = true) }
            .maxByOrNull { parseMs(it.createdAt) ?: Long.MIN_VALUE }
        AiAnalysisRow(analysis, statusOf(latest, nowMs))
    }

private fun statusOf(task: EnrichmentTaskDto?, nowMs: Long): AiAnalysisStatus {
    if (task == null) return AiAnalysisStatus.Never
    return when (task.status.lowercase()) {
        "pending" -> AiAnalysisStatus.Queued
        "processing" -> AiAnalysisStatus.Running
        "completed" -> AiAnalysisStatus.Done(
            agoSeconds = parseMs(task.completedAt)?.let { ((nowMs - it) / 1000L).coerceAtLeast(0) } ?: 0L
        )
        "failed" -> AiAnalysisStatus.Failed(
            message = task.errorMessage?.takeIf { it.isNotBlank() },
            retryInSeconds = parseMs(task.nextRetryAt)?.let { ((it - nowMs) / 1000L).coerceAtLeast(0) },
        )
        "suppressed" -> AiAnalysisStatus.Suppressed
        else -> AiAnalysisStatus.Never
    }
}

private fun parseMs(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
