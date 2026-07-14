package com.photonne.app.data.admin

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AdminResetPasswordRequest
import com.photonne.app.data.models.AdminStatsResponse
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.data.models.BackfillRequest
import com.photonne.app.data.models.BackfillResponse
import com.photonne.app.data.models.BackgroundTaskDto
import com.photonne.app.data.models.BackupRestoreResponse
import com.photonne.app.data.models.CancelQueueResponse
import com.photonne.app.data.models.CreateLibraryRequest
import com.photonne.app.data.models.CreateUserRequest
import com.photonne.app.data.models.DuplicatesStreamEvent
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.GlobalReclusterResponse
import com.photonne.app.data.models.IndexStreamEvent
import com.photonne.app.data.models.LibraryPermissionDto
import com.photonne.app.data.models.LibraryScanProgress
import com.photonne.app.data.models.MaintenanceStreamEvent
import com.photonne.app.data.models.MaintenanceTaskResult
import com.photonne.app.data.models.MetadataStreamEvent
import com.photonne.app.data.models.MlPendingTotalResponse
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.data.models.ThumbnailStreamEvent
import com.photonne.app.data.models.TrashCleanupResult
import com.photonne.app.data.models.TrashStatsResponse
import com.photonne.app.data.models.UpdateLibraryRequest
import com.photonne.app.data.models.UpdateUserRequest
import com.photonne.app.data.models.UserDto
import com.photonne.app.data.models.VersionInfoResponse
import kotlinx.coroutines.flow.Flow

/**
 * Admin-only operations exposed by the `/api/users`, `/api/admin`,
 * `/api/libraries` and `/api/settings` endpoint families. The server
 * gates everything with the `Admin` role policy, so the UI shows the
 * entry point only when the current user has that role.
 */
class AdminRepository(private val api: PhotonneApi) {

    // --- Users ---

    suspend fun listUsers(): List<UserDto> = api.adminListUsers()

    suspend fun createUser(
        username: String,
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        role: String?,
        isActive: Boolean,
        storageQuotaBytes: Long?
    ): UserDto = api.adminCreateUser(
        CreateUserRequest(
            username = username,
            email = email,
            password = password,
            firstName = firstName?.takeIf { it.isNotBlank() },
            lastName = lastName?.takeIf { it.isNotBlank() },
            role = role?.takeIf { it.isNotBlank() },
            isActive = isActive,
            storageQuotaBytes = storageQuotaBytes
        )
    )

    suspend fun updateUser(
        id: String,
        username: String?,
        email: String?,
        firstName: String?,
        lastName: String?,
        role: String?,
        isActive: Boolean?,
        storageQuotaBytes: Long?
    ): UserDto = api.adminUpdateUser(
        id,
        UpdateUserRequest(
            username = username?.takeIf { it.isNotBlank() },
            email = email?.takeIf { it.isNotBlank() },
            firstName = firstName,
            lastName = lastName,
            role = role?.takeIf { it.isNotBlank() },
            isActive = isActive,
            storageQuotaBytes = storageQuotaBytes
        )
    )

    suspend fun deleteUser(id: String) = api.adminDeleteUser(id)

    suspend fun resetUserPassword(id: String, newPassword: String): String =
        api.adminResetUserPassword(id, AdminResetPasswordRequest(newPassword)).message

    suspend fun promoteToPrimary(id: String) = api.adminPromoteToPrimary(id)

    // --- Stats / version ---

    suspend fun getStats(): AdminStatsResponse = api.adminGetStats()

    suspend fun getVersion(refresh: Boolean = false): VersionInfoResponse =
        api.adminGetVersion(refresh)

    // --- Trash ---

    suspend fun getTrashStats(): TrashStatsResponse = api.adminGetTrashStats()

    suspend fun cleanupExpiredTrash(): TrashCleanupResult = api.adminCleanupExpiredTrash()

    // --- Shared-folder trash ---

    suspend fun getSharedTrash(
        cursor: kotlinx.datetime.Instant? = null,
        pageSize: Int = 150
    ): com.photonne.app.data.models.SharedTrashPage = api.getSharedTrash(cursor, pageSize)

    suspend fun restoreSharedTrash(assetIds: List<String>) = api.restoreSharedTrash(assetIds)

    suspend fun purgeSharedTrash(assetIds: List<String>) = api.purgeSharedTrash(assetIds)

    // --- Settings ---

    /** Fetch a single setting as a string. Returns `null` when the key
     *  has no stored value (the server replies with an empty string). */
    suspend fun getSettingString(key: String): String? {
        val dto = api.adminGetSetting(key)
        return dto.value.takeIf { it.isNotBlank() }
    }

    suspend fun getSettings(keys: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (key in keys) {
            val value = runCatching { api.adminGetSetting(key).value }.getOrDefault("")
            out[key] = value
        }
        return out
    }

    suspend fun saveSetting(key: String, value: String) {
        api.adminSaveSetting(key, value)
    }

    suspend fun saveSettings(values: Map<String, String>) {
        for ((key, value) in values) {
            api.adminSaveSetting(key, value)
        }
    }

    // --- Libraries ---

    suspend fun listLibraries(): List<ExternalLibraryDto> = api.adminListLibraries()

    suspend fun createLibrary(
        name: String,
        path: String,
        importSubfolders: Boolean,
        cronSchedule: String?
    ): ExternalLibraryDto = api.adminCreateLibrary(
        CreateLibraryRequest(
            name = name.trim(),
            path = path.trim(),
            importSubfolders = importSubfolders,
            cronSchedule = cronSchedule?.takeIf { it.isNotBlank() }
        )
    )

    suspend fun updateLibrary(
        id: String,
        name: String,
        path: String,
        importSubfolders: Boolean,
        cronSchedule: String?
    ): ExternalLibraryDto = api.adminUpdateLibrary(
        id,
        UpdateLibraryRequest(
            name = name.trim(),
            path = path.trim(),
            importSubfolders = importSubfolders,
            cronSchedule = cronSchedule?.takeIf { it.isNotBlank() }
        )
    )

    suspend fun deleteLibrary(id: String) = api.adminDeleteLibrary(id)

    suspend fun scanLibrary(id: String): Flow<LibraryScanProgress> = api.adminScanLibrary(id)

    suspend fun listLibraryPermissions(id: String): List<LibraryPermissionDto> =
        api.adminListLibraryPermissions(id)

    suspend fun setLibraryPermission(
        id: String,
        userId: String,
        canRead: Boolean
    ): LibraryPermissionDto = api.adminSetLibraryPermission(id, userId, canRead)

    suspend fun removeLibraryPermission(id: String, userId: String) =
        api.adminRemoveLibraryPermission(id, userId)

    // --- ML backfill / maintenance ---

    suspend fun backfill(kind: String, batchSize: Int?, onlyMissing: Boolean): BackfillResponse =
        api.adminBackfill(kind, BackfillRequest(batchSize = batchSize, onlyMissing = onlyMissing))

    suspend fun pendingCount(kind: String): PendingCountResponse = api.adminPendingCount(kind)

    suspend fun mlPendingTotal(): MlPendingTotalResponse = api.adminMlPendingTotal()

    suspend fun cancelMlQueue(kind: String): CancelQueueResponse =
        api.adminCancelMlQueue(kind)

    suspend fun runFaceClustering(): GlobalReclusterResponse = api.adminRunFaceClustering()

    suspend fun runMaintenance(kind: String): MaintenanceTaskResult =
        api.adminMaintenanceTask(kind)

    // --- Streaming tasks ---

    suspend fun indexStream(): Flow<IndexStreamEvent> = api.adminIndexStream()

    suspend fun thumbnailsStream(regenerate: Boolean): Flow<ThumbnailStreamEvent> =
        api.adminThumbnailsStream(regenerate)

    suspend fun metadataStream(overwrite: Boolean): Flow<MetadataStreamEvent> =
        api.adminMetadataStream(overwrite)

    suspend fun dateRestoreStream(
        fromFile: Boolean,
        inferFromPath: Boolean = false,
        useFileDate: Boolean = false,
        writeToFile: Boolean = false,
        dryRun: Boolean = false
    ): Flow<MetadataStreamEvent> =
        api.adminDateRestoreStream(fromFile, inferFromPath, useFileDate, writeToFile, dryRun)

    suspend fun duplicatesStream(
        cleanup: Boolean,
        physical: Boolean
    ): Flow<DuplicatesStreamEvent> = api.adminDuplicatesStream(cleanup = cleanup, physical = physical)

    suspend fun maintenanceStream(
        kind: String,
        dryRun: Boolean = false
    ): Flow<MaintenanceStreamEvent> = api.adminMaintenanceStream(kind, dryRun)

    suspend fun faceClusteringStream(): Flow<MaintenanceStreamEvent> =
        api.adminFaceClusteringStream()

    // --- Background task registry ---

    suspend fun listBackgroundTasks(): List<BackgroundTaskDto> = api.listBackgroundTasks()

    /**
     * Find a running background task by type name (matches the server's
     * [BackgroundTaskType] enum: "IndexAssets", "Thumbnails", "Metadata",
     * "LibraryScan"). Returns null on network errors so UI init code can
     * fall through to a normal idle state instead of blocking.
     */
    suspend fun findRunningTaskOfType(type: String): BackgroundTaskDto? =
        runCatching { api.listBackgroundTasks() }
            .getOrNull()
            ?.firstOrNull { it.type == type && it.isRunning }

    suspend fun cancelBackgroundTask(id: String) = api.cancelBackgroundTask(id)

    suspend fun resumeIndexTaskStream(id: String): Flow<IndexStreamEvent> =
        api.resumeIndexTaskStream(id)

    suspend fun resumeThumbnailsTaskStream(id: String): Flow<ThumbnailStreamEvent> =
        api.resumeThumbnailsTaskStream(id)

    suspend fun resumeMetadataTaskStream(id: String): Flow<MetadataStreamEvent> =
        api.resumeMetadataTaskStream(id)

    suspend fun resumeDateRestoreTaskStream(id: String): Flow<MetadataStreamEvent> =
        api.resumeDateRestoreTaskStream(id)

    suspend fun resumeMaintenanceTaskStream(id: String): Flow<MaintenanceStreamEvent> =
        api.resumeMaintenanceTaskStream(id)

    // --- Backup / restore ---

    suspend fun downloadBackup(level: String): AssetContentBytes =
        api.adminDownloadBackup(level)

    suspend fun restoreBackup(fileName: String, bytes: ByteArray): BackupRestoreResponse =
        api.adminRestoreBackup(fileName, bytes)
}
