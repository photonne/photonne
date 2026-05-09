package com.photonne.app.data.api

import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.LoginRequest
import com.photonne.app.data.models.LoginResponse
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelinePage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
    suspend fun getAssetDetail(assetId: String): AssetDetail
    suspend fun toggleFavorite(assetId: String): Boolean
    suspend fun getAlbums(): List<AlbumSummary>
    suspend fun getAlbumAssets(albumId: String): List<TimelineItem>
    suspend fun createAlbum(name: String, description: String?): AlbumSummary
    suspend fun updateAlbum(albumId: String, name: String, description: String?): AlbumSummary
    suspend fun deleteAlbum(albumId: String)
    suspend fun addAssetToAlbum(albumId: String, assetId: String)
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
}

class PhotonneApiException(
    val status: Int,
    message: String
) : RuntimeException(message)
