package com.photonne.app.data.devicebackup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pass's one real decision: what to upload. A scheduled wake must not keep
 * retrying files the server already refused for good, while an explicit request
 * from the user must retry everything.
 */
class SelectUploadCandidatesTest {

    private fun media(uri: String) = DeviceMedia(
        uri = uri,
        displayName = uri,
        relativePath = "",
        mimeType = "image/jpeg",
        sizeBytes = 1_000L,
        dateModifiedMillis = 1L,
        type = DeviceMediaType.Image
    )

    private val items = listOf(
        media("synced"),
        media("ignored"),
        media("unknown"),
        media("not-synced"),
        media("failed-network"),
        media("failed-quota")
    )

    private val states = mapOf(
        "synced" to DeviceMediaSyncState.Synced("asset-1"),
        "ignored" to DeviceMediaSyncState.Ignored,
        "unknown" to DeviceMediaSyncState.Unknown,
        "not-synced" to DeviceMediaSyncState.NotSynced,
        "failed-network" to DeviceMediaSyncState.Failed(UploadFailureReason.NetworkError, null),
        "failed-quota" to DeviceMediaSyncState.Failed(UploadFailureReason.QuotaExceeded, null)
    )

    @Test
    fun scheduledPassSkipsSyncedIgnoredAndPermanentFailures() {
        val selected = selectUploadCandidates(items, states, retryPermanentFailures = false)

        assertEquals(
            listOf("unknown", "not-synced", "failed-network"),
            selected.map { it.uri }
        )
    }

    @Test
    fun explicitPassRetriesPermanentFailuresToo() {
        val selected = selectUploadCandidates(items, states, retryPermanentFailures = true)

        assertEquals(
            listOf("unknown", "not-synced", "failed-network", "failed-quota"),
            selected.map { it.uri }
        )
    }

    @Test
    fun filesWithNoVerdictYetAreCandidates() {
        val selected = selectUploadCandidates(
            items = listOf(media("brand-new")),
            states = emptyMap(),
            retryPermanentFailures = false
        )

        assertEquals(listOf("brand-new"), selected.map { it.uri })
    }
}
