package com.photonne.app.ui.album

import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.version.AppVersionStore
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class AlbumsStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

private fun albums(count: Int, cover: String?) =
    """[{"id":"al-1","name":"Viaje","createdAt":"2026-05-09T08:00:00Z","updatedAt":"2026-05-09T08:00:00Z",""" +
        """"assetCount":$count,"coverThumbnailUrl":${cover?.let { "\"$it\"" } ?: "null"}}]"""

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelAddedTest {

    private var vm: AlbumsViewModel? = null

    @Volatile
    private var serverAlbums = albums(count = 0, cover = null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun added_photos_reload_the_real_count_and_cover() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(serverAlbums),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = PhotonneApiClient(
            buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = AlbumsStubTokenStorage(),
                authState = AuthStateHolder()
            ),
            "http://test.local"
        )
        val vm = AlbumsViewModel(
            AlbumsRepository(api),
            MapSettings(),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            AssetMutationBus()
        ).also { vm = it }
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { it.albums.isNotEmpty() } } }

        // 3 picked, but the server already had one of them: it stores 2.
        serverAlbums = albums(count = 2, cover = "/thumb/a.jpg")
        vm.applyAssetsAdded("al-1", 3)

        val album = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.state.first { it.albums.single().coverThumbnailUrl != null }
            }
        }.albums.single()
        assertEquals(2, album.assetCount)
        assertEquals("/thumb/a.jpg", album.coverThumbnailUrl)
    }
}
