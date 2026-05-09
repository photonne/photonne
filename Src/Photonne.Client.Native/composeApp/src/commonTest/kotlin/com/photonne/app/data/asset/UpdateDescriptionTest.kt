package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class UpdateDescriptionStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class UpdateDescriptionTest {

    private fun newRepo(engine: MockEngine) = AssetDetailRepository(
        api = PhotonneApiClient(
            client = buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = UpdateDescriptionStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            baseUrl = "http://test.local"
        )
    )

    @Test
    fun update_description_uses_patch_with_caption_body() = runTest {
        val captured = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            captured += request.method to request.url.encodedPath
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        }
        val repo = newRepo(engine)

        repo.updateDescription("aaa", "Sunset over the bay")
        assertEquals(HttpMethod.Patch, captured.single().first)
        assertEquals("/api/assets/aaa/description", captured.single().second)
    }

    @Test
    fun update_description_with_blank_value_sends_null() = runTest {
        val captured = mutableListOf<HttpMethod>()
        val engine = MockEngine { request ->
            captured += request.method
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        val repo = newRepo(engine)

        repo.updateDescription("aaa", "   ")
        assertEquals(HttpMethod.Patch, captured.single())
    }
}
