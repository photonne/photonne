package com.photonne.app.ui.folder

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.UserDto
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.data.version.AppVersionStore
import com.photonne.app.ui.TrackedViewModels
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RenameStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

// Quiet reloads skip an empty list, so the server has to return something.
private const val FOLDERS = """[{"id":"f-1","path":"/assets/users/marc","name":"marc",
"parentFolderId":null,"createdAt":"2026-05-09T08:00:00Z","assetCount":0,"previewAssetIds":[],
"isShared":false,"isOwner":true,"sharedWithCount":0}]"""

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelRenameTest {

    // vms.runTest stops the view model (and waits for it) inside the test,
    // before tearDown swaps Dispatchers.Main back.
    private val vms = TrackedViewModels()

    @Volatile
    private var folderListCalls = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun user(name: String) = UserDto(id = "u-1", username = name, email = "$name@x.local", role = "User")

    @Test
    fun renaming_the_account_reloads_the_folders() = vms.runTest {
        val auth = AuthStateHolder().apply { update(AuthState.Authenticated(user("marc"))) }
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/folders") {
                folderListCalls++
            }
            respond(
                content = ByteReadChannel(if (request.url.encodedPath == "/api/folders") FOLDERS else "{}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = PhotonneApiClient(
            buildPhotonneHttpClient(
                engine = engine,
                baseUrl = "http://test.local",
                tokenStorage = RenameStubTokenStorage(),
                authState = auth
            ),
            "http://test.local"
        )
        val vm = FoldersViewModel(
            FoldersRepository(api),
            OrganizeRepository(api),
            auth,
            MapSettings(),
            UiErrorFactory(ServerUrlStore(MapSettings()), AppVersionStore(api)),
            AssetMutationBus()
        ).also { vms.track(it) }

        // The view model loads itself on creation.
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { !it.isLoading } } }
        assertEquals(1, folderListCalls)

        auth.update(AuthState.Authenticated(user("marc2")))

        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (folderListCalls < 2) delay(10) }
        }
        assertEquals(2, folderListCalls)
    }
}
