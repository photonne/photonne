package com.photonne.app.data.admin

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.NO_STORAGE_QUOTA
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class QuotaStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class CreateUserQuotaTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun userJson(quota: Long?) =
        """{"id":"u1","username":"nuria","email":"nuria@test.local","role":"User","isActive":true,"storageQuotaBytes":$quota}"""

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AdminRepository {
        val client = buildPhotonneHttpClient(
            engine = MockEngine(handler),
            baseUrl = "http://test.local",
            tokenStorage = QuotaStubTokenStorage(),
            authState = AuthStateHolder()
        )
        return AdminRepository(PhotonneApiClient(client, "http://test.local"))
    }

    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private suspend fun AdminRepository.create(quota: Long?) = createUser(
        username = "nuria",
        email = "nuria@test.local",
        password = "Valid-Pass-1!",
        firstName = null,
        lastName = null,
        role = null,
        isActive = null,
        storageQuotaBytes = quota
    )

    @Test
    fun no_quota_on_a_current_server_is_one_request() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { request ->
            calls += "${request.method.value} ${request.url.encodedPath} ${request.bodyText()}"
            respond(ByteReadChannel(userJson(null)), HttpStatusCode.Created, json)
        }

        val created = repository.create(NO_STORAGE_QUOTA)

        assertNull(created.storageQuotaBytes)
        assertEquals(1, calls.size)
        assertEquals(true, "\"storageQuotaBytes\":-1" in calls.single())
    }

    @Test
    fun no_quota_on_an_older_server_is_put_right_with_the_edit() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            when (request.url.encodedPath) {
                // Older servers stored the -1 as it came.
                "/api/users" -> respond(ByteReadChannel(userJson(-1)), HttpStatusCode.Created, json)
                else -> {
                    assertEquals(true, "\"storageQuotaBytes\":-1" in request.bodyText())
                    respond(ByteReadChannel(userJson(null)), HttpStatusCode.OK, json)
                }
            }
        }

        val created = repository.create(NO_STORAGE_QUOTA)

        assertNull(created.storageQuotaBytes)
        assertEquals(listOf("POST /api/users", "PUT /api/users/u1"), calls)
    }

    @Test
    fun the_default_quota_is_left_to_the_server() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { request ->
            calls += request.bodyText()
            respond(ByteReadChannel(userJson(5L * 1024 * 1024 * 1024)), HttpStatusCode.Created, json)
        }

        repository.create(null)

        assertEquals(false, "\"storageQuotaBytes\":-1" in calls.single())
        assertEquals(1, calls.size)
    }
}
