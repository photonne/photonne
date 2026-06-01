package com.photonne.app.data.api

import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.data.models.ShareUpdateResult
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.AssetPage
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.LoginRequest
import com.photonne.app.data.models.LoginResponse
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.AssignFaceResponse
import com.photonne.app.data.models.BulkSuggestionResult
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.PublicVersionResponse
import com.photonne.app.data.models.PersonAssetsPage
import com.photonne.app.data.models.PersonFacesPage
import com.photonne.app.data.models.PersonSuggestionsPage
import com.photonne.app.data.models.ReclusterResponse
import com.photonne.app.data.models.UnlinkAssetResponse
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.models.SearchResponse
import com.photonne.app.data.models.SemanticSearchResponse
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelinePage
import com.photonne.app.data.models.UnsupportedFilesPage
import com.photonne.app.data.models.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class FavoriteResponse(val isFavorite: Boolean)

@Serializable
internal data class AlbumWriteRequest(val name: String, val description: String?)

@Serializable
internal data class AddAssetToAlbumRequest(val assetId: String)

@Serializable
internal data class BatchAssetIdsRequest(val assetIds: List<String>)

@Serializable
internal data class UpdateDescriptionBody(val caption: String?)

@Serializable
internal data class UpdateCaptureDateBody(val dateTaken: String, val writeToFile: Boolean)

@Serializable
internal data class CreateFolderBody(
    val name: String,
    val parentFolderId: String? = null,
    val isSharedSpace: Boolean = false
)

@Serializable
internal data class UpdateFolderBody(
    val name: String,
    val parentFolderId: String? = null
)

@Serializable
internal data class SetFolderPermissionBody(
    val userId: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canManagePermissions: Boolean
)

@Serializable
internal data class MoveFolderAssetsBody(
    val sourceFolderId: String?,
    val targetFolderId: String,
    val assetIds: List<String>
)

@Serializable
internal data class RenamePersonBody(val name: String?)

@Serializable
internal data class DownloadZipBody(
    val assetIds: List<String>,
    val fileName: String? = null
)

@Serializable
internal data class AssignFaceBody(
    val personId: String? = null,
    val newPersonName: String? = null
)

@Serializable
data class UploadAssetResponse(
    val message: String = "",
    val assetId: String? = null
)

// Enrichment task DTOs — mirror /api/assets/{id}/enrichment shape.
@Serializable
data class EnrichmentTaskDto(
    val taskType: String,
    val status: String,
    val errorMessage: String? = null,
    val attemptCount: Int = 0,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val nextRetryAt: String? = null
)

@Serializable
data class AssetEnrichmentResponse(
    val assetId: String,
    val fileName: String = "",
    val tasks: List<EnrichmentTaskDto> = emptyList()
)

@Serializable
data class PendingEnrichmentAssetDto(
    val assetId: String,
    val fileName: String = "",
    val fileCreatedAt: String? = null,
    val pending: Int = 0,
    val processing: Int = 0,
    val failed: Int = 0,
    val failedTaskTypes: List<String> = emptyList()
)

@Serializable
data class PendingEnrichmentPage(
    val items: List<PendingEnrichmentAssetDto> = emptyList(),
    val nextCursor: String? = null,
    val totalAssets: Int = 0
)

@Serializable
data class RetryTaskResponse(
    val assetId: String,
    val taskType: String,
    val status: String
)

@Serializable
data class RetryAllTasksResponse(
    val assetId: String,
    val retried: Int
)

@Serializable
internal data class ExistsByChecksumBody(val assetId: String = "")

@Serializable
internal data class SetCoverRequest(val assetId: String)

@Serializable
internal data class CreateShareRequest(
    val albumId: String,
    val expiresAt: String? = null,
    val password: String? = null,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null
)

@Serializable
internal data class UpdateShareRequest(
    val expiresAt: String? = null,
    val password: String? = null,
    val allowDownload: Boolean = true,
    val maxViews: Int? = null
)

@Serializable
internal data class SetAlbumPermissionBody(
    val userId: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canManagePermissions: Boolean
)

interface PhotonneApi {
    /** Versión del servidor (GET /api/version, sin auth). */
    suspend fun getServerVersion(): String

    suspend fun login(username: String, password: String, deviceId: String): LoginResponse
    suspend fun getTimeline(cursor: Instant? = null, pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE): TimelinePage
    suspend fun listUnsupportedFiles(cursor: Instant? = null, pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE): UnsupportedFilesPage
    suspend fun getUnsupportedFileContent(id: String): AssetContentBytes
    suspend fun getRecentAssets(limit: Int = 10): List<TimelineItem>
    suspend fun getMemories(): List<TimelineItem>
    suspend fun getAssetDetail(assetId: String): AssetDetail
    suspend fun toggleFavorite(assetId: String): Boolean
    suspend fun updateAssetDescription(assetId: String, description: String?)
    suspend fun updateAssetCaptureDate(
        assetId: String,
        dateTaken: Instant,
        writeToFile: Boolean
    ): com.photonne.app.data.models.CaptureDateUpdateResponse
    suspend fun getAlbums(): List<AlbumSummary>
    suspend fun getAlbumAssets(albumId: String): List<TimelineItem>
    suspend fun createAlbum(name: String, description: String?): AlbumSummary
    suspend fun updateAlbum(albumId: String, name: String, description: String?): AlbumSummary
    suspend fun deleteAlbum(albumId: String)
    suspend fun addAssetToAlbum(albumId: String, assetId: String)
    suspend fun addAssetsToAlbumBatch(albumId: String, assetIds: List<String>)
    suspend fun archiveAssets(assetIds: List<String>)
    suspend fun unarchiveAssets(assetIds: List<String>)
    suspend fun unarchiveAll()
    suspend fun trashAssets(assetIds: List<String>)
    suspend fun restoreAssets(assetIds: List<String>)
    suspend fun restoreAllTrash()
    suspend fun purgeAssets(assetIds: List<String>)
    suspend fun emptyTrash()
    suspend fun getArchivedAssets(
        cursor: Instant? = null,
        pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE
    ): AssetPage
    suspend fun getTrashedAssets(
        cursor: Instant? = null,
        pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE
    ): AssetPage
    suspend fun getFavoriteAssets(
        cursor: Instant? = null,
        pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE
    ): AssetPage
    suspend fun downloadAssetsZip(assetIds: List<String>, fileName: String? = null): ByteArray
    suspend fun getAssetContent(assetId: String): AssetContentBytes
    suspend fun uploadAsset(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        destination: String? = null,
        deviceName: String? = null
    ): UploadAssetResponse

    /** Lists the caller's assets with at least one Pending or Failed enrichment task. */
    suspend fun listPendingEnrichment(
        cursor: Instant? = null,
        pageSize: Int = 50
    ): PendingEnrichmentPage

    /** Full enrichment-task breakdown for a single asset. */
    suspend fun getAssetEnrichment(assetId: String): AssetEnrichmentResponse

    /** Resets a single enrichment task to Pending and re-enqueues it. */
    suspend fun retryEnrichmentTask(assetId: String, taskType: String): RetryTaskResponse

    /** Resets all Failed enrichment tasks of an asset and re-enqueues them. */
    suspend fun retryAllEnrichmentTasks(assetId: String): RetryAllTasksResponse
    /**
     * Looks up an existing asset by SHA-256 checksum on the server.
     * Returns the asset id when the user already has a matching file
     * (HTTP 200), null when nothing matches (HTTP 404). Anything else
     * propagates as a [PhotonneApiException].
     */
    suspend fun assetExistsByChecksum(sha256: String): String?
    suspend fun getMapPoints(): List<MapPoint>
    suspend fun removeAssetFromAlbum(albumId: String, assetId: String)
    suspend fun setAlbumCover(albumId: String, assetId: String): AlbumSummary
    suspend fun leaveAlbum(albumId: String)
    suspend fun listAlbumShares(albumId: String): List<AlbumShareLink>
    suspend fun createAlbumShare(
        albumId: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): AlbumShareLink
    suspend fun updateShare(
        token: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): ShareUpdateResult
    suspend fun revokeShare(token: String)
    suspend fun getShareableUsers(): List<ShareableUser>
    suspend fun listAlbumPermissions(albumId: String): List<AlbumPermission>
    suspend fun setAlbumPermission(
        albumId: String,
        userId: String,
        canRead: Boolean,
        canWrite: Boolean,
        canDelete: Boolean,
        canManagePermissions: Boolean
    ): AlbumPermission
    suspend fun removeAlbumPermission(albumId: String, userId: String)
    suspend fun getFolders(): List<FolderSummary>
    suspend fun getFolder(folderId: String): FolderSummary
    suspend fun getFolderAssets(folderId: String): List<TimelineItem>
    suspend fun createFolder(name: String, parentFolderId: String?, isSharedSpace: Boolean): FolderSummary
    suspend fun updateFolder(folderId: String, name: String, parentFolderId: String?): FolderSummary
    suspend fun deleteFolder(folderId: String)
    suspend fun listFolderPermissions(folderId: String): List<AlbumPermission>
    suspend fun setFolderPermission(
        folderId: String,
        userId: String,
        canRead: Boolean,
        canWrite: Boolean,
        canDelete: Boolean,
        canManagePermissions: Boolean
    ): AlbumPermission
    suspend fun removeFolderPermission(folderId: String, userId: String)
    suspend fun moveFolderAssets(
        sourceFolderId: String?,
        targetFolderId: String,
        assetIds: List<String>
    )
    suspend fun searchAssets(
        q: String? = null,
        from: String? = null,
        to: String? = null,
        folder: String? = null,
        personIds: List<String> = emptyList(),
        objectLabels: List<String> = emptyList(),
        sceneLabels: List<String> = emptyList(),
        textQuery: String? = null,
        pageSize: Int? = null,
        offset: Int? = null
    ): SearchResponse
    suspend fun semanticSearch(q: String, limit: Int? = null): SemanticSearchResponse
    suspend fun listObjectLabels(q: String? = null, limit: Int? = null): List<ObjectLabel>
    suspend fun listSceneLabels(q: String? = null, limit: Int? = null): List<SceneLabel>
    suspend fun listPeople(
        search: String? = null,
        includeHidden: Boolean = false,
        limit: Int? = null,
        offset: Int? = null
    ): PeoplePage
    suspend fun renamePerson(personId: String, name: String?)
    suspend fun hidePerson(personId: String)
    suspend fun unhidePerson(personId: String)
    suspend fun getPersonAssets(
        personId: String,
        limit: Int? = null,
        offset: Int? = null
    ): PersonAssetsPage
    suspend fun listPersonSuggestions(
        personId: String,
        limit: Int? = null,
        offset: Int? = null
    ): PersonSuggestionsPage
    suspend fun acceptAllPersonSuggestions(personId: String): BulkSuggestionResult
    suspend fun dismissAllPersonSuggestions(personId: String): BulkSuggestionResult
    suspend fun acceptFaceSuggestion(faceId: String): AssignFaceResponse
    suspend fun dismissFaceSuggestion(faceId: String)
    suspend fun assignFace(
        faceId: String,
        personId: String? = null,
        newPersonName: String? = null
    ): AssignFaceResponse
    suspend fun rejectFace(faceId: String)
    suspend fun unassignFace(faceId: String)
    suspend fun mergePeople(targetPersonId: String, sourcePersonId: String)
    suspend fun reclusterPeople(): ReclusterResponse
    suspend fun getAssetFaces(assetId: String): List<Face>
    suspend fun listPersonFaces(
        personId: String,
        limit: Int? = null,
        offset: Int? = null
    ): PersonFacesPage
    suspend fun setPersonCoverFace(personId: String, faceId: String)
    suspend fun unlinkAssetFromPerson(personId: String, assetId: String): UnlinkAssetResponse

    suspend fun getCurrentUser(): UserDto
    suspend fun updateProfile(request: com.photonne.app.data.models.UpdateProfileRequest): UserDto
    suspend fun changePassword(
        request: com.photonne.app.data.models.ChangePasswordRequest
    ): com.photonne.app.data.models.ChangePasswordResponse
    suspend fun getStorageInfo(): com.photonne.app.data.models.StorageInfoDto

    // Administration ---------------------------------------------------------
    suspend fun adminListUsers(): List<UserDto>
    suspend fun adminGetUser(id: String): UserDto
    suspend fun adminCreateUser(
        request: com.photonne.app.data.models.CreateUserRequest
    ): UserDto
    suspend fun adminUpdateUser(
        id: String,
        request: com.photonne.app.data.models.UpdateUserRequest
    ): UserDto
    suspend fun adminDeleteUser(id: String)
    suspend fun adminResetUserPassword(
        id: String,
        request: com.photonne.app.data.models.AdminResetPasswordRequest
    ): com.photonne.app.data.models.AdminResetPasswordResponse
    suspend fun adminPromoteToPrimary(id: String)
    suspend fun adminGetStats(): com.photonne.app.data.models.AdminStatsResponse
    suspend fun adminGetVersion(refresh: Boolean = false): com.photonne.app.data.models.VersionInfoResponse
    suspend fun adminGetTrashStats(): com.photonne.app.data.models.TrashStatsResponse
    suspend fun adminCleanupExpiredTrash(): com.photonne.app.data.models.TrashCleanupResult

    // Generic settings -------------------------------------------------------
    suspend fun adminGetSetting(key: String): com.photonne.app.data.models.SettingDto
    suspend fun adminSaveSetting(
        key: String,
        value: String
    ): com.photonne.app.data.models.SaveSettingResponse

    // External libraries -----------------------------------------------------
    suspend fun adminListLibraries(): List<com.photonne.app.data.models.ExternalLibraryDto>
    suspend fun adminCreateLibrary(
        request: com.photonne.app.data.models.CreateLibraryRequest
    ): com.photonne.app.data.models.ExternalLibraryDto
    suspend fun adminUpdateLibrary(
        id: String,
        request: com.photonne.app.data.models.UpdateLibraryRequest
    ): com.photonne.app.data.models.ExternalLibraryDto
    suspend fun adminDeleteLibrary(id: String)
    suspend fun adminScanLibrary(
        id: String
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.LibraryScanProgress>
    suspend fun adminListLibraryPermissions(
        id: String
    ): List<com.photonne.app.data.models.LibraryPermissionDto>
    suspend fun adminSetLibraryPermission(
        id: String,
        userId: String,
        canRead: Boolean
    ): com.photonne.app.data.models.LibraryPermissionDto
    suspend fun adminRemoveLibraryPermission(id: String, userId: String)

    // ML backfill / maintenance ---------------------------------------------
    suspend fun adminBackfill(
        kind: String,
        request: com.photonne.app.data.models.BackfillRequest
    ): com.photonne.app.data.models.BackfillResponse
    suspend fun adminPendingCount(kind: String): com.photonne.app.data.models.PendingCountResponse
    suspend fun adminMlPendingTotal(): com.photonne.app.data.models.MlPendingTotalResponse
    suspend fun adminCancelMlQueue(kind: String): com.photonne.app.data.models.CancelQueueResponse
    suspend fun adminRunFaceClustering(): com.photonne.app.data.models.GlobalReclusterResponse
    suspend fun adminMaintenanceTask(
        kind: String
    ): com.photonne.app.data.models.MaintenanceTaskResult

    // Streaming tasks -------------------------------------------------------
    suspend fun adminIndexStream():
        kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.IndexStreamEvent>
    suspend fun adminThumbnailsStream(
        regenerate: Boolean
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.ThumbnailStreamEvent>
    suspend fun adminMetadataStream(
        overwrite: Boolean
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.MetadataStreamEvent>
    suspend fun adminDateRestoreStream(
        fromFile: Boolean
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.MetadataStreamEvent>
    suspend fun adminDuplicatesStream(
        cleanup: Boolean,
        physical: Boolean
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.DuplicatesStreamEvent>

    // Background task registry — lets the UI reconnect to a running task
    // after the user leaves and re-opens an admin screen (or the app).
    suspend fun listBackgroundTasks(): List<com.photonne.app.data.models.BackgroundTaskDto>
    suspend fun cancelBackgroundTask(id: String)
    suspend fun resumeIndexTaskStream(
        id: String
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.IndexStreamEvent>
    suspend fun resumeThumbnailsTaskStream(
        id: String
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.ThumbnailStreamEvent>
    suspend fun resumeMetadataTaskStream(
        id: String
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.MetadataStreamEvent>
    suspend fun resumeDateRestoreTaskStream(
        id: String
    ): kotlinx.coroutines.flow.Flow<com.photonne.app.data.models.MetadataStreamEvent>

    // Database backup / restore --------------------------------------------
    suspend fun adminDownloadBackup(level: String): com.photonne.app.data.models.AssetContentBytes
    suspend fun adminRestoreBackup(
        fileName: String,
        bytes: ByteArray
    ): com.photonne.app.data.models.BackupRestoreResponse

    // User utilities (mirrors /utilities/* pages in the PWA) ----------------
    suspend fun utilitiesDuplicates(): List<com.photonne.app.data.models.UserDuplicateGroup>
    suspend fun utilitiesLargeFiles(count: Int): List<com.photonne.app.data.models.TimelineItem>
    suspend fun utilitiesFolderTree(): List<com.photonne.app.data.models.FolderTreeNode>

    // Notifications --------------------------------------------------------
    suspend fun getNotifications(
        page: Int = 1,
        pageSize: Int = 20,
        unreadOnly: Boolean = false
    ): com.photonne.app.data.models.NotificationsPage
    suspend fun getUnreadNotificationCount(): Int
    suspend fun markNotificationRead(id: String)
    suspend fun markAllNotificationsRead()

    companion object {
        const val DEFAULT_TIMELINE_PAGE_SIZE = 80

        // Per-request timeout for /api/assets/timeline. See the timeout block
        // inside getTimeline() for why this exists as a safety net.
        const val TIMELINE_REQUEST_TIMEOUT_MS: Long = 180_000
    }
}

class PhotonneApiClient(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String
) : PhotonneApi {

    constructor(client: HttpClient, baseUrl: String) : this(client, { baseUrl })

    private val baseUrl: String get() = baseUrlProvider()

    override suspend fun getServerVersion(): String {
        val response: HttpResponse = client.get("$baseUrl/api/version") {
            skipAuthRefresh()
        }
        response.ensureSuccess { "Server version fetch failed ($it)" }
        return response.body<PublicVersionResponse>().version
    }

    override suspend fun login(username: String, password: String, deviceId: String): LoginResponse {
        val response: HttpResponse = client.post("$baseUrl/api/auth/login") {
            skipAuthRefresh()
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password, deviceId = deviceId))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Login failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getTimeline(cursor: Instant?, pageSize: Int): TimelinePage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/timeline") {
            // Safety net for large libraries: the Darwin engine's default
            // ~60 s socket timeout would surface as the red "Socket timeout
            // has expired" banner the moment the server takes longer than a
            // minute on cold cache / first page. The real fix is the server
            // refactor (no filesystem scan, projected query, indexed sort);
            // 3 minutes here just makes sure we never trip the banner first.
            timeout {
                requestTimeoutMillis = PhotonneApi.TIMELINE_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = PhotonneApi.TIMELINE_REQUEST_TIMEOUT_MS
            }
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Timeline fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listUnsupportedFiles(cursor: Instant?, pageSize: Int): UnsupportedFilesPage {
        val response: HttpResponse = client.get("$baseUrl/api/unsupported-files") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Unsupported files fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getUnsupportedFileContent(id: String): AssetContentBytes {
        val response: HttpResponse =
            client.get("$baseUrl/api/unsupported-files/$id/content")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Unsupported file content fetch failed (${response.status.value})"
            )
        }
        val contentType = response.headers[HttpHeaders.ContentType]
            ?: "application/octet-stream"
        val suggested = response.headers[HttpHeaders.ContentDisposition]
            ?.let(::parseContentDispositionFilename)
            ?: "photonne_$id"
        return AssetContentBytes(
            bytes = response.body(),
            mimeType = contentType,
            suggestedFileName = suggested
        )
    }

    override suspend fun getRecentAssets(limit: Int): List<TimelineItem> {
        val response: HttpResponse = client.get("$baseUrl/api/assets/recent") {
            parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Recent assets fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getMemories(): List<TimelineItem> {
        val response: HttpResponse = client.get("$baseUrl/api/assets/memories")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Memories fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAssetDetail(assetId: String): AssetDetail {
        val response: HttpResponse = client.get("$baseUrl/api/assets/$assetId")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Asset detail fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun downloadAssetsZip(
        assetIds: List<String>,
        fileName: String?
    ): ByteArray {
        if (assetIds.isEmpty()) return ByteArray(0)
        val response: HttpResponse = client.post("$baseUrl/api/assets/download-zip") {
            contentType(ContentType.Application.Json)
            setBody(DownloadZipBody(assetIds = assetIds, fileName = fileName))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Download failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAssetContent(assetId: String): AssetContentBytes {
        val response: HttpResponse =
            client.get("$baseUrl/api/assets/$assetId/content") {
                parameter("download", true)
            }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Asset content fetch failed (${response.status.value})"
            )
        }
        val contentType = response.headers[HttpHeaders.ContentType]
            ?: "application/octet-stream"
        val suggested = response.headers[HttpHeaders.ContentDisposition]
            ?.let(::parseContentDispositionFilename)
            ?: "photonne_$assetId"
        return AssetContentBytes(
            bytes = response.body(),
            mimeType = contentType,
            suggestedFileName = suggested
        )
    }

    private fun parseContentDispositionFilename(header: String): String? {
        // RFC 6266; very permissive — handles `filename="x"`, `filename=x`,
        // and `filename*=UTF-8''percent-encoded` from ASP.NET Core.
        val star = "filename*="
        val plain = "filename="
        val starIdx = header.indexOf(star, ignoreCase = true)
        if (starIdx >= 0) {
            val raw = header.substring(starIdx + star.length).substringBefore(';').trim()
            val payload = raw.substringAfter("''", raw)
            return runCatching { percentDecodeUtf8(payload) }
                .getOrDefault(payload)
                .ifEmpty { null }
        }
        val plainIdx = header.indexOf(plain, ignoreCase = true)
        if (plainIdx >= 0) {
            return header.substring(plainIdx + plain.length).substringBefore(';').trim()
                .trim('"').ifEmpty { null }
        }
        return null
    }

    private fun percentDecodeUtf8(input: String): String {
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '%' && i + 2 < input.length) {
                val code = input.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    bytes.add(code.toByte())
                    i += 3
                    continue
                }
            }
            bytes.add(ch.code.toByte())
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    override suspend fun toggleFavorite(assetId: String): Boolean {
        val response: HttpResponse = client.post("$baseUrl/api/assets/$assetId/favorite")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Favorite toggle failed (${response.status.value})"
            )
        }
        val body: FavoriteResponse = response.body()
        return body.isFavorite
    }

    override suspend fun getAlbums(): List<AlbumSummary> {
        val response: HttpResponse = client.get("$baseUrl/api/albums")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Albums fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAlbumAssets(albumId: String): List<TimelineItem> {
        val response: HttpResponse = client.get("$baseUrl/api/albums/$albumId/assets")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Album assets fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun createAlbum(name: String, description: String?): AlbumSummary {
        val response: HttpResponse = client.post("$baseUrl/api/albums") {
            contentType(ContentType.Application.Json)
            setBody(AlbumWriteRequest(name = name, description = description))
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Album create failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun updateAlbum(
        albumId: String,
        name: String,
        description: String?
    ): AlbumSummary {
        val response: HttpResponse = client.put("$baseUrl/api/albums/$albumId") {
            contentType(ContentType.Application.Json)
            setBody(AlbumWriteRequest(name = name, description = description))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Album update failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun deleteAlbum(albumId: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/albums/$albumId")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Album delete failed (${response.status.value})"
            )
        }
    }

    override suspend fun addAssetToAlbum(albumId: String, assetId: String) {
        val response: HttpResponse = client.post("$baseUrl/api/albums/$albumId/assets") {
            contentType(ContentType.Application.Json)
            setBody(AddAssetToAlbumRequest(assetId = assetId))
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Add asset to album failed (${response.status.value})"
            )
        }
    }

    override suspend fun updateAssetDescription(assetId: String, description: String?) {
        val response: HttpResponse = client.patch("$baseUrl/api/assets/$assetId/description") {
            contentType(ContentType.Application.Json)
            setBody(UpdateDescriptionBody(caption = description?.takeIf { it.isNotBlank() }))
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Update description failed (${response.status.value})"
            )
        }
    }

    override suspend fun updateAssetCaptureDate(
        assetId: String,
        dateTaken: Instant,
        writeToFile: Boolean
    ): com.photonne.app.data.models.CaptureDateUpdateResponse {
        val response: HttpResponse = client.patch("$baseUrl/api/assets/$assetId/date") {
            contentType(ContentType.Application.Json)
            // Instant.toString() yields ISO-8601 with 'Z'; the server parses it as
            // a DateTimeOffset and persists UTC.
            setBody(UpdateCaptureDateBody(dateTaken = dateTaken.toString(), writeToFile = writeToFile))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Update capture date failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun addAssetsToAlbumBatch(albumId: String, assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/albums/$albumId/assets/batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Add assets batch failed (${response.status.value})"
            )
        }
    }

    override suspend fun archiveAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/assets/archive") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Archive failed (${response.status.value})"
            )
        }
    }

    override suspend fun trashAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/assets/delete") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Trash failed (${response.status.value})"
            )
        }
    }

    override suspend fun unarchiveAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/assets/unarchive") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        ensureSuccess(response, "Unarchive failed")
    }

    override suspend fun unarchiveAll() {
        val response: HttpResponse = client.post("$baseUrl/api/assets/archive/unarchive-all")
        ensureSuccess(response, "Unarchive all failed")
    }

    override suspend fun restoreAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/assets/restore") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        ensureSuccess(response, "Restore failed")
    }

    override suspend fun restoreAllTrash() {
        val response: HttpResponse = client.post("$baseUrl/api/assets/trash/restore-all")
        ensureSuccess(response, "Restore all failed")
    }

    override suspend fun purgeAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val response: HttpResponse = client.post("$baseUrl/api/assets/purge") {
            contentType(ContentType.Application.Json)
            setBody(BatchAssetIdsRequest(assetIds = assetIds))
        }
        ensureSuccess(response, "Purge failed")
    }

    override suspend fun emptyTrash() {
        val response: HttpResponse = client.post("$baseUrl/api/assets/trash/empty")
        ensureSuccess(response, "Empty trash failed")
    }

    override suspend fun getArchivedAssets(cursor: Instant?, pageSize: Int): AssetPage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/archived") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Archived list failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getTrashedAssets(cursor: Instant?, pageSize: Int): AssetPage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/trash") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Trash list failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getFavoriteAssets(cursor: Instant?, pageSize: Int): AssetPage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/favorites") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Favorites list failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun uploadAsset(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        destination: String?,
        deviceName: String?
    ): UploadAssetResponse {
        val parsedType = runCatching { ContentType.parse(mimeType) }
            .getOrDefault(ContentType.Application.OctetStream)
        val response: HttpResponse = client.post("$baseUrl/api/assets/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, parsedType.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"${fileName.replace("\"", "")}\""
                                )
                            }
                        )
                        if (!destination.isNullOrBlank()) {
                            append("destination", destination)
                        }
                        if (!deviceName.isNullOrBlank()) {
                            append("deviceName", deviceName)
                        }
                    }
                )
            )
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Upload failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun assetExistsByChecksum(sha256: String): String? {
        val response: HttpResponse = client.get("$baseUrl/api/assets/exists/$sha256")
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body: ExistsByChecksumBody = response.body()
                body.assetId.takeIf { it.isNotBlank() }
            }
            HttpStatusCode.NotFound -> null
            else -> throw PhotonneApiException(
                status = response.status.value,
                message = "Hash lookup failed (${response.status.value})"
            )
        }
    }

    override suspend fun getMapPoints(): List<MapPoint> {
        val response: HttpResponse = client.get("$baseUrl/api/assets/map/points")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Map points fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    private fun ensureSuccess(response: HttpResponse, message: String) {
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "$message (${response.status.value})"
            )
        }
    }

    override suspend fun removeAssetFromAlbum(albumId: String, assetId: String) {
        val response: HttpResponse = client.delete(
            "$baseUrl/api/albums/$albumId/assets/$assetId"
        )
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Remove asset from album failed (${response.status.value})"
            )
        }
    }

    override suspend fun setAlbumCover(albumId: String, assetId: String): AlbumSummary {
        val response: HttpResponse = client.put("$baseUrl/api/albums/$albumId/cover") {
            contentType(ContentType.Application.Json)
            setBody(SetCoverRequest(assetId = assetId))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Set album cover failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun leaveAlbum(albumId: String) {
        val response: HttpResponse = client.post("$baseUrl/api/albums/$albumId/leave")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Leave album failed (${response.status.value})"
            )
        }
    }

    override suspend fun listAlbumShares(albumId: String): List<AlbumShareLink> {
        val response: HttpResponse = client.get("$baseUrl/api/share") {
            parameter("albumId", albumId)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Listing share links failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun createAlbumShare(
        albumId: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): AlbumShareLink {
        val response: HttpResponse = client.post("$baseUrl/api/share") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateShareRequest(
                    albumId = albumId,
                    expiresAt = expiresAt?.toString(),
                    password = password?.takeIf { it.isNotBlank() },
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
            )
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Creating share link failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun updateShare(
        token: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): ShareUpdateResult {
        val response: HttpResponse = client.patch("$baseUrl/api/share/$token") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateShareRequest(
                    expiresAt = expiresAt?.toString(),
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
            )
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Update share link failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun revokeShare(token: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/share/$token")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Revoke share link failed (${response.status.value})"
            )
        }
    }

    override suspend fun getShareableUsers(): List<ShareableUser> {
        val response: HttpResponse = client.get("$baseUrl/api/users/shareable")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Listing users failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listAlbumPermissions(albumId: String): List<AlbumPermission> {
        val response: HttpResponse = client.get("$baseUrl/api/albums/$albumId/permissions")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Listing album members failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun setAlbumPermission(
        albumId: String,
        userId: String,
        canRead: Boolean,
        canWrite: Boolean,
        canDelete: Boolean,
        canManagePermissions: Boolean
    ): AlbumPermission {
        val response: HttpResponse = client.post("$baseUrl/api/albums/$albumId/permissions") {
            contentType(ContentType.Application.Json)
            setBody(
                SetAlbumPermissionBody(
                    userId = userId,
                    canRead = canRead,
                    canWrite = canWrite,
                    canDelete = canDelete,
                    canManagePermissions = canManagePermissions
                )
            )
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Granting album permission failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun removeAlbumPermission(albumId: String, userId: String) {
        val response: HttpResponse = client.delete(
            "$baseUrl/api/albums/$albumId/permissions/$userId"
        )
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Removing album member failed (${response.status.value})"
            )
        }
    }

    override suspend fun getFolders(): List<FolderSummary> {
        val response: HttpResponse = client.get("$baseUrl/api/folders")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folders fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getFolder(folderId: String): FolderSummary {
        val response: HttpResponse = client.get("$baseUrl/api/folders/$folderId")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folder fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getFolderAssets(folderId: String): List<TimelineItem> {
        val response: HttpResponse = client.get("$baseUrl/api/folders/$folderId/assets")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folder assets fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun createFolder(
        name: String,
        parentFolderId: String?,
        isSharedSpace: Boolean
    ): FolderSummary {
        val response: HttpResponse = client.post("$baseUrl/api/folders") {
            contentType(ContentType.Application.Json)
            setBody(CreateFolderBody(name = name, parentFolderId = parentFolderId, isSharedSpace = isSharedSpace))
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folder create failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun updateFolder(
        folderId: String,
        name: String,
        parentFolderId: String?
    ): FolderSummary {
        val response: HttpResponse = client.put("$baseUrl/api/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateFolderBody(name = name, parentFolderId = parentFolderId))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folder update failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun deleteFolder(folderId: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/folders/$folderId")
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NoContent) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Folder delete failed (${response.status.value})"
            )
        }
    }

    override suspend fun listFolderPermissions(folderId: String): List<AlbumPermission> {
        val response: HttpResponse = client.get("$baseUrl/api/folders/$folderId/permissions")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Listing folder members failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun setFolderPermission(
        folderId: String,
        userId: String,
        canRead: Boolean,
        canWrite: Boolean,
        canDelete: Boolean,
        canManagePermissions: Boolean
    ): AlbumPermission {
        val response: HttpResponse = client.post("$baseUrl/api/folders/$folderId/permissions") {
            contentType(ContentType.Application.Json)
            setBody(
                SetFolderPermissionBody(
                    userId = userId,
                    canRead = canRead,
                    canWrite = canWrite,
                    canDelete = canDelete,
                    canManagePermissions = canManagePermissions
                )
            )
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Granting folder permission failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun removeFolderPermission(folderId: String, userId: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/folders/$folderId/permissions/$userId")
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NoContent) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Removing folder member failed (${response.status.value})"
            )
        }
    }

    override suspend fun moveFolderAssets(
        sourceFolderId: String?,
        targetFolderId: String,
        assetIds: List<String>
    ) {
        val response: HttpResponse = client.post("$baseUrl/api/folders/assets/move") {
            contentType(ContentType.Application.Json)
            setBody(
                MoveFolderAssetsBody(
                    sourceFolderId = sourceFolderId,
                    targetFolderId = targetFolderId,
                    assetIds = assetIds
                )
            )
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NoContent) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Moving folder assets failed (${response.status.value})"
            )
        }
    }

    override suspend fun searchAssets(
        q: String?,
        from: String?,
        to: String?,
        folder: String?,
        personIds: List<String>,
        objectLabels: List<String>,
        sceneLabels: List<String>,
        textQuery: String?,
        pageSize: Int?,
        offset: Int?
    ): SearchResponse {
        val response: HttpResponse = client.get("$baseUrl/api/assets/search") {
            if (!q.isNullOrBlank()) parameter("q", q)
            if (!from.isNullOrBlank()) parameter("from", from)
            if (!to.isNullOrBlank()) parameter("to", to)
            if (!folder.isNullOrBlank()) parameter("folder", folder)
            if (pageSize != null) parameter("pageSize", pageSize)
            if (offset != null && offset > 0) parameter("offset", offset)
            for (id in personIds) parameter("personId", id)
            for (label in objectLabels) parameter("objectLabel", label)
            for (label in sceneLabels) parameter("sceneLabel", label)
            if (!textQuery.isNullOrBlank()) parameter("textQuery", textQuery)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Search failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun semanticSearch(q: String, limit: Int?): SemanticSearchResponse {
        val response: HttpResponse = client.get("$baseUrl/api/assets/search/semantic") {
            parameter("q", q)
            if (limit != null) parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Semantic search failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listObjectLabels(q: String?, limit: Int?): List<ObjectLabel> {
        val response: HttpResponse = client.get("$baseUrl/api/objects/labels") {
            if (!q.isNullOrBlank()) parameter("q", q)
            if (limit != null) parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Object labels fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listSceneLabels(q: String?, limit: Int?): List<SceneLabel> {
        val response: HttpResponse = client.get("$baseUrl/api/scenes/labels") {
            if (!q.isNullOrBlank()) parameter("q", q)
            if (limit != null) parameter("limit", limit)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Scene labels fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listPeople(
        search: String?,
        includeHidden: Boolean,
        limit: Int?,
        offset: Int?
    ): PeoplePage {
        val response: HttpResponse = client.get("$baseUrl/api/people") {
            if (!search.isNullOrBlank()) parameter("search", search)
            if (includeHidden) parameter("includeHidden", true)
            if (limit != null) parameter("limit", limit)
            if (offset != null) parameter("offset", offset)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "People fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun renamePerson(personId: String, name: String?) {
        val response: HttpResponse = client.patch("$baseUrl/api/people/$personId") {
            contentType(ContentType.Application.Json)
            setBody(RenamePersonBody(name = name?.trim()?.takeIf { it.isNotEmpty() }))
        }
        ensurePersonSuccess(response, "Rename person failed")
    }

    override suspend fun hidePerson(personId: String) {
        val response: HttpResponse = client.post("$baseUrl/api/people/$personId/hide")
        ensurePersonSuccess(response, "Hide person failed")
    }

    override suspend fun unhidePerson(personId: String) {
        val response: HttpResponse = client.post("$baseUrl/api/people/$personId/unhide")
        ensurePersonSuccess(response, "Unhide person failed")
    }

    override suspend fun getPersonAssets(
        personId: String,
        limit: Int?,
        offset: Int?
    ): PersonAssetsPage {
        val response: HttpResponse = client.get("$baseUrl/api/search/people/$personId/assets") {
            if (limit != null) parameter("limit", limit)
            if (offset != null) parameter("offset", offset)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Person assets fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listPersonSuggestions(
        personId: String,
        limit: Int?,
        offset: Int?
    ): PersonSuggestionsPage {
        val response: HttpResponse = client.get("$baseUrl/api/people/$personId/suggestions") {
            if (limit != null) parameter("limit", limit)
            if (offset != null) parameter("offset", offset)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Suggestions fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun acceptAllPersonSuggestions(personId: String): BulkSuggestionResult {
        val response: HttpResponse =
            client.post("$baseUrl/api/people/$personId/suggestions/accept-all")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Accept-all failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun dismissAllPersonSuggestions(personId: String): BulkSuggestionResult {
        val response: HttpResponse =
            client.post("$baseUrl/api/people/$personId/suggestions/dismiss-all")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Dismiss-all failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun acceptFaceSuggestion(faceId: String): AssignFaceResponse {
        val response: HttpResponse =
            client.post("$baseUrl/api/faces/$faceId/accept-suggestion")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Accept face suggestion failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun dismissFaceSuggestion(faceId: String) {
        val response: HttpResponse =
            client.post("$baseUrl/api/faces/$faceId/dismiss-suggestion")
        ensurePersonSuccess(response, "Dismiss face suggestion failed")
    }

    override suspend fun assignFace(
        faceId: String,
        personId: String?,
        newPersonName: String?
    ): AssignFaceResponse {
        val response: HttpResponse = client.post("$baseUrl/api/faces/$faceId/assign") {
            contentType(ContentType.Application.Json)
            setBody(AssignFaceBody(personId = personId, newPersonName = newPersonName))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Assign face failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun rejectFace(faceId: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/faces/$faceId")
        ensurePersonSuccess(response, "Reject face failed")
    }

    override suspend fun unassignFace(faceId: String) {
        val response: HttpResponse = client.post("$baseUrl/api/faces/$faceId/unassign")
        ensurePersonSuccess(response, "Unassign face failed")
    }

    override suspend fun mergePeople(targetPersonId: String, sourcePersonId: String) {
        val response: HttpResponse =
            client.post("$baseUrl/api/people/$targetPersonId/merge/$sourcePersonId")
        ensurePersonSuccess(response, "Merge people failed")
    }

    override suspend fun reclusterPeople(): ReclusterResponse {
        val response: HttpResponse = client.post("$baseUrl/api/people/recluster")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Recluster failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAssetFaces(assetId: String): List<Face> {
        val response: HttpResponse = client.get("$baseUrl/api/assets/$assetId/faces")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Asset faces fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun listPersonFaces(
        personId: String,
        limit: Int?,
        offset: Int?
    ): PersonFacesPage {
        val response: HttpResponse = client.get("$baseUrl/api/people/$personId/faces") {
            if (limit != null) parameter("limit", limit)
            if (offset != null) parameter("offset", offset)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Person faces fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun setPersonCoverFace(personId: String, faceId: String) {
        val response: HttpResponse =
            client.post("$baseUrl/api/people/$personId/cover/$faceId")
        ensurePersonSuccess(response, "Set cover failed")
    }

    override suspend fun unlinkAssetFromPerson(
        personId: String,
        assetId: String
    ): UnlinkAssetResponse {
        val response: HttpResponse =
            client.post("$baseUrl/api/people/$personId/assets/$assetId/unlink")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Unlink failed (${response.status.value})"
            )
        }
        return response.body()
    }

    private fun ensurePersonSuccess(response: HttpResponse, message: String) {
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "$message (${response.status.value})"
            )
        }
    }

    override suspend fun getCurrentUser(): UserDto {
        val response: HttpResponse = client.get("$baseUrl/api/users/me")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Fetching user failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun updateProfile(
        request: com.photonne.app.data.models.UpdateProfileRequest
    ): UserDto {
        val response: HttpResponse = client.put("$baseUrl/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            // 400 carries `{ "error": "..." }` (UsersEndpoint.cs:381,388); surface that text
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Profile update failed"
            )
        }
        return response.body()
    }

    override suspend fun changePassword(
        request: com.photonne.app.data.models.ChangePasswordRequest
    ): com.photonne.app.data.models.ChangePasswordResponse {
        val response: HttpResponse = client.post("$baseUrl/api/users/me/change-password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Password change failed"
            )
        }
        return response.body()
    }

    override suspend fun getStorageInfo(): com.photonne.app.data.models.StorageInfoDto {
        val response: HttpResponse = client.get("$baseUrl/api/users/me/storage")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Storage info fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun adminListUsers(): List<UserDto> {
        val response: HttpResponse = client.get("$baseUrl/api/users")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Listing users failed"
            )
        }
        return response.body()
    }

    override suspend fun adminGetUser(id: String): UserDto {
        val response: HttpResponse = client.get("$baseUrl/api/users/$id")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Fetching user failed"
            )
        }
        return response.body()
    }

    override suspend fun adminCreateUser(
        request: com.photonne.app.data.models.CreateUserRequest
    ): UserDto {
        val response: HttpResponse = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Creating user failed"
            )
        }
        return response.body()
    }

    override suspend fun adminUpdateUser(
        id: String,
        request: com.photonne.app.data.models.UpdateUserRequest
    ): UserDto {
        val response: HttpResponse = client.put("$baseUrl/api/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Updating user failed"
            )
        }
        return response.body()
    }

    override suspend fun adminDeleteUser(id: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/users/$id")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Deleting user failed"
            )
        }
    }

    override suspend fun adminResetUserPassword(
        id: String,
        request: com.photonne.app.data.models.AdminResetPasswordRequest
    ): com.photonne.app.data.models.AdminResetPasswordResponse {
        val response: HttpResponse = client.post("$baseUrl/api/users/$id/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Resetting password failed"
            )
        }
        return response.body()
    }

    override suspend fun adminPromoteToPrimary(id: String) {
        val response: HttpResponse = client.post("$baseUrl/api/users/$id/promote-to-primary")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Promoting user to primary admin failed"
            )
        }
    }

    override suspend fun adminGetStats(): com.photonne.app.data.models.AdminStatsResponse {
        val response: HttpResponse = client.get("$baseUrl/api/admin/stats")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Fetching stats failed"
            )
        }
        return response.body()
    }

    override suspend fun adminGetVersion(
        refresh: Boolean
    ): com.photonne.app.data.models.VersionInfoResponse {
        val response: HttpResponse = client.get("$baseUrl/api/admin/version") {
            if (refresh) parameter("refresh", true)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Fetching version failed"
            )
        }
        return response.body()
    }

    override suspend fun adminGetTrashStats(): com.photonne.app.data.models.TrashStatsResponse {
        val response: HttpResponse = client.get("$baseUrl/api/admin/trash/stats")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Fetching trash stats failed"
            )
        }
        return response.body()
    }

    override suspend fun adminCleanupExpiredTrash():
        com.photonne.app.data.models.TrashCleanupResult {
        val response: HttpResponse = client.post("$baseUrl/api/admin/trash/cleanup-expired")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Trash cleanup failed"
            )
        }
        return response.body()
    }

    override suspend fun adminGetSetting(key: String): com.photonne.app.data.models.SettingDto {
        val response: HttpResponse = client.get("$baseUrl/api/settings") {
            parameter("key", key)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Fetching setting $key failed"
            )
        }
        return response.body()
    }

    override suspend fun adminSaveSetting(
        key: String,
        value: String
    ): com.photonne.app.data.models.SaveSettingResponse {
        val response: HttpResponse = client.post("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            setBody(com.photonne.app.data.models.SaveSettingRequest(key = key, value = value))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Saving setting $key failed"
            )
        }
        return response.body()
    }

    override suspend fun adminListLibraries():
        List<com.photonne.app.data.models.ExternalLibraryDto> {
        val response: HttpResponse = client.get("$baseUrl/api/libraries")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Listing libraries failed"
            )
        }
        return response.body()
    }

    override suspend fun adminCreateLibrary(
        request: com.photonne.app.data.models.CreateLibraryRequest
    ): com.photonne.app.data.models.ExternalLibraryDto {
        val response: HttpResponse = client.post("$baseUrl/api/libraries") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Creating library failed"
            )
        }
        return response.body()
    }

    override suspend fun adminUpdateLibrary(
        id: String,
        request: com.photonne.app.data.models.UpdateLibraryRequest
    ): com.photonne.app.data.models.ExternalLibraryDto {
        val response: HttpResponse = client.put("$baseUrl/api/libraries/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        // Server returns 204 NoContent; client must re-fetch to get latest dto. Use GET.
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Updating library failed"
            )
        }
        val refreshed: HttpResponse = client.get("$baseUrl/api/libraries/$id")
        if (refreshed.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = refreshed.status.value,
                message = "Library fetch after update failed"
            )
        }
        return refreshed.body()
    }

    override suspend fun adminDeleteLibrary(id: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/libraries/$id")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Deleting library failed"
            )
        }
    }

    override suspend fun adminScanLibrary(
        id: String
    ): Flow<com.photonne.app.data.models.LibraryScanProgress> =
        streamJsonLines("$baseUrl/api/libraries/$id/scan/stream")

    override suspend fun adminListLibraryPermissions(
        id: String
    ): List<com.photonne.app.data.models.LibraryPermissionDto> {
        val response: HttpResponse = client.get("$baseUrl/api/libraries/$id/permissions")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Listing permissions failed"
            )
        }
        return response.body()
    }

    override suspend fun adminSetLibraryPermission(
        id: String,
        userId: String,
        canRead: Boolean
    ): com.photonne.app.data.models.LibraryPermissionDto {
        val response: HttpResponse = client.post("$baseUrl/api/libraries/$id/permissions") {
            contentType(ContentType.Application.Json)
            setBody(
                com.photonne.app.data.models.SetLibraryPermissionRequest(
                    userId = userId,
                    canRead = canRead
                )
            )
        }
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Granting permission failed"
            )
        }
        return response.body()
    }

    override suspend fun adminRemoveLibraryPermission(id: String, userId: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/libraries/$id/permissions/$userId")
        if (response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.NoContent
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Removing permission failed"
            )
        }
    }

    override suspend fun adminBackfill(
        kind: String,
        request: com.photonne.app.data.models.BackfillRequest
    ): com.photonne.app.data.models.BackfillResponse {
        val response: HttpResponse = client.post(
            "$baseUrl/api/admin/maintenance/$kind/backfill"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Backfill $kind failed"
            )
        }
        return response.body()
    }

    override suspend fun adminPendingCount(
        kind: String
    ): com.photonne.app.data.models.PendingCountResponse {
        val response: HttpResponse = client.get(
            "$baseUrl/api/admin/maintenance/$kind/pending-count"
        )
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Pending count $kind failed"
            )
        }
        return response.body()
    }

    override suspend fun adminMlPendingTotal():
        com.photonne.app.data.models.MlPendingTotalResponse {
        val response: HttpResponse = client.get(
            "$baseUrl/api/admin/maintenance/ml-pending-total"
        )
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "ML pending total failed"
            )
        }
        return response.body()
    }

    override suspend fun adminCancelMlQueue(
        kind: String
    ): com.photonne.app.data.models.CancelQueueResponse {
        val response: HttpResponse = client.delete(
            "$baseUrl/api/admin/maintenance/$kind/queue"
        )
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Cancel ML queue $kind failed"
            )
        }
        return response.body()
    }

    override suspend fun adminRunFaceClustering():
        com.photonne.app.data.models.GlobalReclusterResponse {
        val response: HttpResponse =
            client.post("$baseUrl/api/admin/maintenance/face-clustering/run")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Face clustering failed"
            )
        }
        return response.body()
    }

    override suspend fun adminMaintenanceTask(
        kind: String
    ): com.photonne.app.data.models.MaintenanceTaskResult {
        val response: HttpResponse = client.post("$baseUrl/api/admin/maintenance/$kind")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Maintenance task $kind failed"
            )
        }
        return response.body()
    }

    override suspend fun adminIndexStream():
        Flow<com.photonne.app.data.models.IndexStreamEvent> =
        streamJsonLines("$baseUrl/api/assets/index/stream")

    override suspend fun adminThumbnailsStream(
        regenerate: Boolean
    ): Flow<com.photonne.app.data.models.ThumbnailStreamEvent> = flow {
        client.prepareGet("$baseUrl/api/assets/thumbnails/stream") {
            disableStreamTimeouts()
            parameter("regenerate", regenerate)
        }.execute { response ->
            ensureStreamSuccess(response, "Thumbnails stream failed")
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                runCatching {
                    streamJson.decodeFromString(
                        com.photonne.app.data.models.ThumbnailStreamEvent.serializer(),
                        line
                    )
                }.getOrNull()?.let { emit(it) }
            }
        }
    }

    override suspend fun adminMetadataStream(
        overwrite: Boolean
    ): Flow<com.photonne.app.data.models.MetadataStreamEvent> = flow {
        client.prepareGet("$baseUrl/api/assets/metadata/stream") {
            disableStreamTimeouts()
            parameter("overwrite", overwrite)
        }.execute { response ->
            ensureStreamSuccess(response, "Metadata stream failed")
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                runCatching {
                    streamJson.decodeFromString(
                        com.photonne.app.data.models.MetadataStreamEvent.serializer(),
                        line
                    )
                }.getOrNull()?.let { emit(it) }
            }
        }
    }

    override suspend fun adminDateRestoreStream(
        fromFile: Boolean
    ): Flow<com.photonne.app.data.models.MetadataStreamEvent> = flow {
        client.prepareGet("$baseUrl/api/assets/dates/restore/stream") {
            disableStreamTimeouts()
            parameter("fromFile", fromFile)
        }.execute { response ->
            ensureStreamSuccess(response, "Date restore stream failed")
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                runCatching {
                    streamJson.decodeFromString(
                        com.photonne.app.data.models.MetadataStreamEvent.serializer(),
                        line
                    )
                }.getOrNull()?.let { emit(it) }
            }
        }
    }

    override suspend fun listBackgroundTasks():
        List<com.photonne.app.data.models.BackgroundTaskDto> {
        val response: HttpResponse = client.get("$baseUrl/api/tasks")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Background tasks fetch failed"
            )
        }
        return response.body()
    }

    override suspend fun cancelBackgroundTask(id: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/tasks/$id")
        // 204 NoContent on success; 404 if task already gone (race with finish
        // + 1h cleanup) — treat as a no-op rather than surfacing an error.
        if (response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.NotFound
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Cancel task failed"
            )
        }
    }

    override suspend fun resumeIndexTaskStream(
        id: String
    ): Flow<com.photonne.app.data.models.IndexStreamEvent> =
        streamJsonLines("$baseUrl/api/tasks/$id/stream")

    override suspend fun resumeThumbnailsTaskStream(
        id: String
    ): Flow<com.photonne.app.data.models.ThumbnailStreamEvent> =
        streamJsonLines("$baseUrl/api/tasks/$id/stream")

    override suspend fun resumeMetadataTaskStream(
        id: String
    ): Flow<com.photonne.app.data.models.MetadataStreamEvent> =
        streamJsonLines("$baseUrl/api/tasks/$id/stream")

    override suspend fun resumeDateRestoreTaskStream(
        id: String
    ): Flow<com.photonne.app.data.models.MetadataStreamEvent> =
        streamJsonLines("$baseUrl/api/tasks/$id/stream")

    override suspend fun adminDuplicatesStream(
        cleanup: Boolean,
        physical: Boolean
    ): Flow<com.photonne.app.data.models.DuplicatesStreamEvent> = flow {
        client.prepareGet("$baseUrl/api/assets/duplicates/stream") {
            disableStreamTimeouts()
            parameter("cleanup", cleanup)
            parameter("physical", physical)
        }.execute { response ->
            ensureStreamSuccess(response, "Duplicates stream failed")
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                runCatching {
                    streamJson.decodeFromString(
                        com.photonne.app.data.models.DuplicatesStreamEvent.serializer(),
                        line
                    )
                }.getOrNull()?.let { emit(it) }
            }
        }
    }

    override suspend fun adminDownloadBackup(
        level: String
    ): AssetContentBytes {
        val response: HttpResponse = client.get("$baseUrl/api/admin/database/backup") {
            parameter("level", level)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Backup download failed"
            )
        }
        val mime = response.headers[HttpHeaders.ContentType] ?: "application/json"
        val suggested = response.headers[HttpHeaders.ContentDisposition]
            ?.let(::parseContentDispositionFilename)
            ?: "photonne_backup.json"
        return AssetContentBytes(
            bytes = response.body(),
            mimeType = mime,
            suggestedFileName = suggested
        )
    }

    override suspend fun adminRestoreBackup(
        fileName: String,
        bytes: ByteArray
    ): com.photonne.app.data.models.BackupRestoreResponse {
        val response: HttpResponse = client.post("$baseUrl/api/admin/database/restore") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"${fileName.replace("\"", "")}\""
                                )
                            }
                        )
                    }
                )
            )
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Backup restore failed"
            )
        }
        return response.body()
    }

    /** Shared streaming helper for endpoints that emit one JSON object
     *  per line via `IAsyncEnumerable<T>` on the server side. */
    private inline fun <reified T> streamJsonLines(url: String): Flow<T> = flow {
        client.prepareGet(url) { disableStreamTimeouts() }.execute { response ->
            ensureStreamSuccess(response, "Stream failed")
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                runCatching { streamJson.decodeFromString<T>(line) }
                    .getOrNull()
                    ?.let { emit(it) }
            }
        }
    }

    /**
     * Disable per-request socket / total-request timeouts for endpoints
     * that hold a long-lived NDJSON connection open. The Darwin engine
     * defaults to ~60 s `timeoutIntervalForRequest`, which would tear
     * down the stream any time the server goes that long between pushes
     * (normal during the file-scanning phase of a large indexing run).
     */
    private fun HttpRequestBuilder.disableStreamTimeouts() {
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    private fun ensureStreamSuccess(response: HttpResponse, message: String) {
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "$message (${response.status.value})"
            )
        }
    }

    override suspend fun utilitiesDuplicates():
        List<com.photonne.app.data.models.UserDuplicateGroup> {
        val response: HttpResponse = client.get("$baseUrl/api/utilities/duplicates")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Duplicates fetch failed"
            )
        }
        return response.body()
    }

    override suspend fun utilitiesLargeFiles(
        count: Int
    ): List<com.photonne.app.data.models.TimelineItem> {
        val response: HttpResponse = client.get("$baseUrl/api/utilities/large-files") {
            parameter("count", count)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Large files fetch failed"
            )
        }
        return response.body()
    }

    override suspend fun utilitiesFolderTree():
        List<com.photonne.app.data.models.FolderTreeNode> {
        val response: HttpResponse = client.get("$baseUrl/api/utilities/folders/tree")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = parseErrorMessage(response) ?: "Folder tree fetch failed"
            )
        }
        return response.body()
    }

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
        unreadOnly: Boolean
    ): com.photonne.app.data.models.NotificationsPage {
        val response: HttpResponse = client.get("$baseUrl/api/notifications") {
            parameter("page", page)
            parameter("pageSize", pageSize)
            parameter("unreadOnly", unreadOnly)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Notifications fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getUnreadNotificationCount(): Int {
        val response: HttpResponse = client.get("$baseUrl/api/notifications/unread-count")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Unread count fetch failed (${response.status.value})"
            )
        }
        return response.body<com.photonne.app.data.models.UnreadNotificationCount>().count
    }

    override suspend fun markNotificationRead(id: String) {
        val response: HttpResponse = client.patch("$baseUrl/api/notifications/$id/read")
        if (response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.OK
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Mark notification read failed (${response.status.value})"
            )
        }
    }

    override suspend fun markAllNotificationsRead() {
        val response: HttpResponse = client.patch("$baseUrl/api/notifications/read-all")
        if (response.status != HttpStatusCode.NoContent &&
            response.status != HttpStatusCode.OK
        ) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Mark all read failed (${response.status.value})"
            )
        }
    }

    override suspend fun listPendingEnrichment(
        cursor: Instant?,
        pageSize: Int
    ): PendingEnrichmentPage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/enrichment/pending") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Pending enrichment fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAssetEnrichment(assetId: String): AssetEnrichmentResponse {
        val response: HttpResponse = client.get("$baseUrl/api/assets/$assetId/enrichment")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Asset enrichment fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun retryEnrichmentTask(
        assetId: String,
        taskType: String
    ): RetryTaskResponse {
        val response: HttpResponse = client.post("$baseUrl/api/assets/$assetId/enrichment/retry") {
            parameter("taskType", taskType)
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Retry enrichment task failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun retryAllEnrichmentTasks(assetId: String): RetryAllTasksResponse {
        val response: HttpResponse = client.post("$baseUrl/api/assets/$assetId/enrichment/retry-all")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Retry all enrichment tasks failed (${response.status.value})"
            )
        }
        return response.body()
    }

    /**
     * The users endpoints surface 400-level validation issues as
     * `{ "error": "human readable text" }`. Try to lift that out so the
     * settings screens can render a meaningful inline message; fall back
     * to null when the body isn't shaped that way (and the caller will
     * use the generic "X failed" copy instead).
     */
    private suspend fun parseErrorMessage(response: HttpResponse): String? {
        return runCatching { response.body<com.photonne.app.data.models.ApiError>().error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        /** Lenient JSON for streaming events so unknown fields (added on
         *  newer servers) don't cause the whole stream to die. */
        val streamJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

class PhotonneApiException(
    val status: Int,
    message: String,
    val method: String? = null,
    val url: String? = null,
    val responseBody: String? = null,
) : RuntimeException(message)

/**
 * Lanza un [PhotonneApiException] enriquecido (method, url, body truncado)
 * si la respuesta no es 2xx. Reemplazo recomendado para los antiguos
 * `if (response.status != HttpStatusCode.OK) throw PhotonneApiException(...)`.
 */
internal suspend inline fun HttpResponse.ensureSuccess(
    messageBuilder: (Int) -> String,
) {
    val code = status.value
    if (code in 200..299) return
    val body = runCatching { bodyAsText().take(2048) }.getOrNull()
    throw PhotonneApiException(
        status = code,
        message = messageBuilder(code),
        method = request.method.value,
        url = request.url.toString(),
        responseBody = body,
    )
}
