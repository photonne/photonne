package com.photonne.app.data.utilities

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class UtilitiesStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class UtilitiesRepositoryTest {

    @Test
    fun delete_duplicates_emits_removed_on_the_bus() = runTest(UnconfinedTestDispatcher()) {
        val engine = MockEngine { respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent) }
        val bus = AssetMutationBus()
        val repo = UtilitiesRepository(
            api = PhotonneApiClient(
                client = buildPhotonneHttpClient(
                    engine = engine,
                    baseUrl = "http://test.local",
                    tokenStorage = UtilitiesStubTokenStorage(),
                    authState = AuthStateHolder()
                ),
                baseUrl = "http://test.local"
            ),
            mutationBus = bus
        )
        val event = async { bus.events.first() }

        repo.deleteAssets(listOf("a-1", "a-2"))

        assertEquals(AssetMutation.Removed(listOf("a-1", "a-2")), event.await())
    }
}
