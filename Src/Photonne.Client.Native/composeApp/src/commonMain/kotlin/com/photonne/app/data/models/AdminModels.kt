package com.photonne.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null,
    val storageQuotaBytes: Long? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null,
    val storageQuotaBytes: Long? = null
)

@Serializable
data class AdminResetPasswordRequest(val newPassword: String)

@Serializable
data class AdminResetPasswordResponse(val message: String = "")

@Serializable
data class AdminUserUsage(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val photos: Int = 0,
    val videos: Int = 0,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L
)

@Serializable
data class AdminStatsResponse(
    val totalPhotos: Int = 0,
    val totalVideos: Int = 0,
    val totalBytes: Long = 0L,
    val users: List<AdminUserUsage> = emptyList()
)

@Serializable
data class VersionInfoResponse(
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val latestReleaseUrl: String? = null,
    val releaseNotes: String? = null,
    val publishedAt: String? = null,
    val hasUpdate: Boolean = false,
    val checkError: String? = null,
    val checkedAt: String? = null
)

@Serializable
data class TrashStatsResponse(
    val totalItems: Int = 0,
    val totalBytes: Long = 0L,
    val expiredItems: Int = 0,
    val retentionDays: Int = 0,
    val maxQuotaMb: Int = 0,
    val overQuotaUsers: Int = 0,
    val overQuotaBytes: Long = 0L
)

@Serializable
data class TrashCleanupResult(
    val success: Boolean = false,
    val message: String = "",
    val deleted: Int = 0
)

/** Generic settings get/set wire format. Server stores every value as a string. */
@Serializable
data class SettingDto(val key: String, val value: String = "")

@Serializable
data class SaveSettingRequest(val key: String, val value: String)

@Serializable
data class SaveSettingResponse(val message: String = "")

// External libraries -------------------------------------------------------

@Serializable
data class ExternalLibraryDto(
    val id: String,
    val name: String,
    val path: String,
    val importSubfolders: Boolean = true,
    val cronSchedule: String? = null,
    val lastScannedAt: String? = null,
    val lastScanStatus: String? = null,
    val lastScanAssetsFound: Int? = null,
    val lastScanAssetsAdded: Int? = null,
    val lastScanAssetsRemoved: Int? = null,
    val assetCount: Int = 0,
    val createdAt: String? = null
)

@Serializable
data class CreateLibraryRequest(
    val name: String,
    val path: String,
    val importSubfolders: Boolean = true,
    val cronSchedule: String? = null
)

@Serializable
data class UpdateLibraryRequest(
    val name: String,
    val path: String,
    val importSubfolders: Boolean = true,
    val cronSchedule: String? = null
)

@Serializable
data class LibraryPermissionDto(
    val id: String,
    val userId: String,
    val username: String,
    val email: String,
    val canRead: Boolean,
    val grantedAt: String? = null,
    val grantedByUserId: String? = null
)

@Serializable
data class SetLibraryPermissionRequest(
    val userId: String,
    val canRead: Boolean
)

@Serializable
data class LibraryScanProgress(
    val message: String = "",
    val percentage: Int = 0,
    val assetsFound: Int = 0,
    val assetsIndexed: Int = 0,
    val assetsMarkedMissing: Int = 0,
    val isCompleted: Boolean = false,
    val error: String? = null,
    val taskId: String? = null
)

// ML backfill / maintenance -----------------------------------------------

@Serializable
data class BackfillRequest(
    val batchSize: Int? = null,
    val onlyMissing: Boolean? = true
)

@Serializable
data class BackfillResponse(
    val enqueued: Int = 0,
    val total: Int = 0
)

@Serializable
data class PendingCountResponse(
    val unprocessed: Int = 0,
    val inQueue: Int = 0
)

@Serializable
data class GlobalReclusterResponse(
    val ownersProcessed: Int = 0,
    val personsCreated: Int = 0
)

@Serializable
data class MaintenanceTaskResult(
    val success: Boolean = false,
    val message: String = "",
    val processed: Int = 0,
    val affected: Int = 0
)

// Streaming task events ---------------------------------------------------

/**
 * Per-line JSON body emitted by `/api/assets/index/stream`. Every numeric
 * counter is optional so older server builds (or different task kinds)
 * can omit fields without breaking deserialization.
 */
@Serializable
data class IndexStreamStatistics(
    val totalFilesFound: Int? = null,
    val newFiles: Int? = null,
    val updatedFiles: Int? = null,
    val movedFiles: Int? = null,
    val skippedUnchanged: Int? = null,
    val orphanedFilesRemoved: Int? = null,
    val orphanedFoldersRemoved: Int? = null,
    val hashesCalculated: Int? = null,
    val exifExtracted: Int? = null,
    val mediaTagsDetected: Int? = null,
    val mlJobsQueued: Int? = null,
    val thumbnailsGenerated: Int? = null,
    val thumbnailsRegenerated: Int? = null,
    val duplicateAssetsRemoved: Int? = null,
    val indexCompletedAt: String? = null,
    val indexDuration: String? = null
)

@Serializable
data class IndexStreamEvent(
    val message: String = "",
    val percentage: Double = 0.0,
    val statistics: IndexStreamStatistics? = null,
    val isCompleted: Boolean = false,
    val taskId: String? = null
)

@Serializable
data class ThumbnailStreamStatistics(
    val totalAssets: Int? = null,
    val processed: Int? = null,
    val generated: Int? = null,
    val skipped: Int? = null,
    val failed: Int? = null
)

@Serializable
data class ThumbnailStreamEvent(
    val message: String = "",
    val percentage: Double = 0.0,
    val statistics: ThumbnailStreamStatistics? = null,
    val isCompleted: Boolean = false,
    val taskId: String? = null
)

@Serializable
data class MetadataStreamStatistics(
    val totalAssets: Int? = null,
    val processed: Int? = null,
    val extracted: Int? = null,
    val skipped: Int? = null,
    val failed: Int? = null
)

@Serializable
data class MetadataStreamEvent(
    val message: String = "",
    val percentage: Double = 0.0,
    val statistics: MetadataStreamStatistics? = null,
    val isCompleted: Boolean = false,
    val taskId: String? = null
)

@Serializable
data class DuplicatesStreamStatistics(
    val totalAssets: Int? = null,
    val duplicateGroups: Int? = null,
    val duplicateAssets: Int? = null,
    val removed: Int? = null,
    val bytesReclaimed: Long? = null,
    val unindexedFiles: Int? = null
)

@Serializable
data class DuplicatesStreamEvent(
    val message: String = "",
    val percentage: Double = 0.0,
    val statistics: DuplicatesStreamStatistics? = null,
    val isCompleted: Boolean = false,
    val taskId: String? = null
)

// Mirrors the anonymous projection returned by `GET /api/tasks` on the
// server (BackgroundTasksEndpoint.cs). The server keeps finished tasks
// for ~1h so clients can reconnect to a buffered stream after the user
// navigates away or relaunches the app.
@Serializable
data class BackgroundTaskDto(
    val id: String,
    val type: String,
    val status: String,
    val percentage: Double = 0.0,
    val lastMessage: String = "",
    val startedAt: String,
    val finishedAt: String? = null,
    val parameters: Map<String, String> = emptyMap()
) {
    val isRunning: Boolean get() = status == "Running"
}

// Database backup / restore ----------------------------------------------

@Serializable
data class BackupRestoreStats(
    val users: Int = 0,
    val assets: Int = 0,
    val albums: Int = 0,
    val folders: Int = 0,
    val externalLibraries: Int = 0,
    val people: Int = 0,
    val faces: Int = 0,
    val embeddings: Int = 0,
    val ocrLines: Int = 0,
    val includesMlData: Boolean = false
)

@Serializable
data class BackupRestoreResponse(
    val message: String = "",
    val stats: BackupRestoreStats? = null
)
