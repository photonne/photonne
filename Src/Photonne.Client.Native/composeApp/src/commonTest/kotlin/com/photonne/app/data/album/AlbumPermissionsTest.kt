package com.photonne.app.data.album

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
import kotlin.test.assertTrue

private class PermissionsStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun memberPayload(
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

class AlbumPermissionsTest {

    private fun newRepository(engine: MockEngine): AlbumsRepository =
        AlbumsRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = PermissionsStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            )
        )

    @Test
    fun list_members_returns_album_permissions() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            val payload = "[${memberPayload("p-1", "u-1", canWrite = true)}]"
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = newRepository(engine)
        val members = repository.listMembers("alb-1")
        assertEquals(1, members.size)
        assertEquals(AlbumMemberRole.Editor, members[0].role)
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/albums/alb-1/permissions", captured.single().second)
    }

    @Test
    fun grant_member_with_admin_role_sets_all_flags() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(
                    memberPayload(
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
        val repository = newRepository(engine)

        val perm = repository.grantMember("alb-1", "u-1", AlbumMemberRole.Admin)
        assertEquals(AlbumMemberRole.Admin, perm.role)
        assertEquals(HttpMethod.Post, captured.single().first)
        assertEquals("/api/albums/alb-1/permissions", captured.single().second)
    }

    @Test
    fun revoke_member_calls_delete_with_user_id() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repository = newRepository(engine)

        repository.revokeMember("alb-1", "u-1")
        assertEquals(HttpMethod.Delete, captured.single().first)
        assertEquals("/api/albums/alb-1/permissions/u-1", captured.single().second)
    }

    @Test
    fun shareable_users_hits_dedicated_endpoint() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(
                content = ByteReadChannel(
                    """[{"id":"u-1","username":"alice","email":"alice@x"}]"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = newRepository(engine)

        val users = repository.shareableUsers()
        assertTrue(users.any { it.id == "u-1" && it.username == "alice" })
        assertEquals(HttpMethod.Get, captured.single().first)
        assertEquals("/api/users/shareable", captured.single().second)
    }
}
