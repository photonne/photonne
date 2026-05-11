package com.photonne.app.data.folder

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.AlbumMemberRole
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FoldersCrudStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun folderPayload(id: String, name: String, path: String = "/$name"): String = """
    {
      "id": "$id",
      "path": "$path",
      "name": "$name",
      "parentFolderId": null,
      "createdAt": "2026-05-09T08:00:00Z",
      "assetCount": 0,
      "previewAssetIds": [],
      "isShared": false,
      "isOwner": true,
      "sharedWithCount": 0
    }
""".trimIndent()

private fun permissionPayload(
    permissionId: String,
    userId: String,
    canWrite: Boolean = false,
    canDelete: Boolean = false,
    canManagePermissions: Boolean = false
): String = """
    {
      "id": "$permissionId",
      "userId": "$userId",
      "username": "user$userId",
      "email": "user$userId@photonne.local",
      "canRead": true,
      "canWrite": $canWrite,
      "canDelete": $canDelete,
      "canManagePermissions": $canManagePermissions,
      "grantedAt": "2026-05-09T08:00:00Z"
    }
""".trimIndent()

class FoldersCrudTest {

    private fun newRepo(engine: MockEngine) = FoldersRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = FoldersCrudStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun create_posts_to_folders_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(folderPayload("f-1", "Trips")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val folder = repo.create(name = "Trips", parentFolderId = null, isSharedSpace = false)
        assertEquals("Trips", folder.name)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/folders", captured.single().second)
    }

    @Test
    fun update_uses_put_with_folder_id() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(folderPayload("f-1", "Renamed")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val folder = repo.update("f-1", "Renamed", parentFolderId = null)
        assertEquals("Renamed", folder.name)
        assertEquals(HttpMethod.Put, captured.single().first)
        assertEquals("/api/folders/f-1", captured.single().second)
    }

    @Test
    fun delete_returns_unit_on_success() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.delete("f-1")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/folders/f-1", captured.single().second)
    }

    @Test
    fun list_members_calls_permissions_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel("[${permissionPayload("p-1", "u-1", canWrite = true)}]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val members = repo.listMembers("f-1")
        assertEquals(1, members.size)
        assertEquals(AlbumMemberRole.Editor, members[0].role)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/folders/f-1/permissions", captured.single().second)
    }

    @Test
    fun grant_member_with_admin_role_posts_to_permissions() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(
                    permissionPayload(
                        permissionId = "p-1",
                        userId = "u-1",
                        canWrite = true,
                        canDelete = true,
                        canManagePermissions = true
                    )
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = newRepo(engine)

        val perm = repo.grantMember("f-1", "u-1", AlbumMemberRole.Admin)
        assertEquals(AlbumMemberRole.Admin, perm.role)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/folders/f-1/permissions", captured.single().second)
    }

    @Test
    fun move_assets_posts_to_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.moveAssets(
            sourceFolderId = "f-1",
            targetFolderId = "f-2",
            assetIds = listOf("a-1", "a-2")
        )
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/folders/assets/move", captured.single().second)
    }

    @Test
    fun revoke_member_deletes_user_id() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.revokeMember("f-1", "u-1")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/folders/f-1/permissions/u-1", captured.single().second)
    }
}
