package com.photonne.app.data.api

import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.AssetPage
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.LoginRequest
import com.photonne.app.data.models.LoginResponse
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.models.SearchResponse
import com.photonne.app.data.models.SemanticSearchResponse
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelinePage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
internal data class SetAlbumPermissionBody(
    val userId: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canManagePermissions: Boolean
)

interface PhotonneApi {
    suspend fun login(username: String, password: String, deviceId: String): LoginResponse
    suspend fun getTimeline(cursor: Instant? = null, pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE): TimelinePage
    suspend fun getMemories(): List<TimelineItem>
    suspend fun getAssetDetail(assetId: String): AssetDetail
    suspend fun toggleFavorite(assetId: String): Boolean
    suspend fun updateAssetDescription(assetId: String, description: String?)
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

    companion object {
        const val DEFAULT_TIMELINE_PAGE_SIZE = 80
    }
}

class PhotonneApiClient(
    private val client: HttpClient,
    private val baseUrl: String
) : PhotonneApi {

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
}

class PhotonneApiException(
    val status: Int,
    message: String
) : RuntimeException(message)
