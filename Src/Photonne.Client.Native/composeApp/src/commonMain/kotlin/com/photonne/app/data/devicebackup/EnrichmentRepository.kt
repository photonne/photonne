package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.AssetEnrichmentResponse
import com.photonne.app.data.api.PendingEnrichmentPage
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.RetryAllTasksResponse
import com.photonne.app.data.api.RetryTaskResponse
import kotlinx.datetime.Instant

/**
 * Thin wrapper around the four enrichment endpoints. Lives next to
 * [DeviceBackupRepository] because the UI flow is the same (a phone
 * backup that lands on the server and waits for its post-processing
 * pipeline to finish).
 */
class EnrichmentRepository(
    private val api: PhotonneApi
) {
    suspend fun listPending(
        cursor: Instant? = null,
        pageSize: Int = 50
    ): PendingEnrichmentPage = api.listPendingEnrichment(cursor, pageSize)

    suspend fun getAsset(assetId: String): AssetEnrichmentResponse =
        api.getAssetEnrichment(assetId)

    suspend fun retryTask(assetId: String, taskType: String): RetryTaskResponse =
        api.retryEnrichmentTask(assetId, taskType)

    suspend fun retryAll(assetId: String): RetryAllTasksResponse =
        api.retryAllEnrichmentTasks(assetId)
}
