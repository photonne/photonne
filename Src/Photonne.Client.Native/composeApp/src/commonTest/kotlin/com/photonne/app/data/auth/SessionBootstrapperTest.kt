package com.photonne.app.data.auth

import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.api.InMemorySettings
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.models.UserDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeTokenStorage(
    private var access: String? = "access-1",
    private var refresh: String? = "refresh-1",
    private var user: UserDto? = null
) : TokenStorage {
    override fun getAccessToken() = access
    override fun getRefreshToken() = refresh
    override fun getDeviceId() = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }
    override fun getUser() = user
    override fun saveUser(user: UserDto) { this.user = user }
    override fun clear() {
        access = null
        refresh = null
        user = null
    }
}

private fun cachedUser() = UserDto(
    id = "u1",
    username = "marc",
    email = "marc@example.com",
    role = "User"
)

private const val SERVER_USER_JSON = """
    {"id":"u1","username":"marc","email":"marc@example.com","role":"User"}
"""

class SessionBootstrapperTest {

    private fun bootstrapper(
        storage: TokenStorage,
        urlStore: ServerUrlStore,
        authState: AuthStateHolder,
        engine: MockEngine
    ): SessionBootstrapper {
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = storage,
            authState = authState
        )
        val account = AccountRepository(
            api = PhotonneApiClient(client, "http://test.local"),
            authStateHolder = authState,
            tokenStorage = storage
        )
        return SessionBootstrapper(storage, urlStore, authState, account)
    }

    private fun configuredUrlStore() =
        ServerUrlStore(InMemorySettings()).apply { setPublic("http://test.local") }

    @Test
    fun restores_cached_user_offline_without_logging_out() = runTest {
        val storage = FakeTokenStorage(user = cachedUser())
        val authState = AuthStateHolder()
        // Network is down: the validation request throws (transient failure).
        val engine = MockEngine { throw IOException("offline") }

        bootstrapper(storage, configuredUrlStore(), authState, engine).restore()

        val state = authState.state.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("marc", state.user.username)
    }

    @Test
    fun validates_and_refreshes_cached_user_when_online() = runTest {
        val storage = FakeTokenStorage(user = cachedUser())
        val authState = AuthStateHolder()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(SERVER_USER_JSON.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        bootstrapper(storage, configuredUrlStore(), authState, engine).restore()

        assertIs<AuthState.Authenticated>(authState.state.value)
        // The fetched user is persisted so the cache stays fresh.
        assertEquals("marc", storage.getUser()?.username)
    }

    @Test
    fun stays_logged_out_without_refresh_token() = runTest {
        val storage = FakeTokenStorage(refresh = null, user = cachedUser())
        val authState = AuthStateHolder()
        var requested = false
        val engine = MockEngine {
            requested = true
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }

        bootstrapper(storage, configuredUrlStore(), authState, engine).restore()

        assertEquals(AuthState.Unauthenticated, authState.state.value)
        assertTrue(!requested, "should not hit the network without a refresh token")
    }

    @Test
    fun stays_logged_out_without_configured_server() = runTest {
        val storage = FakeTokenStorage(user = cachedUser())
        val authState = AuthStateHolder()
        val engine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }

        bootstrapper(storage, ServerUrlStore(InMemorySettings()), authState, engine).restore()

        assertEquals(AuthState.Unauthenticated, authState.state.value)
    }

    @Test
    fun falls_back_to_login_when_no_cache_and_offline() = runTest {
        val storage = FakeTokenStorage(user = null)
        val authState = AuthStateHolder()
        val engine = MockEngine { throw IOException("offline") }

        bootstrapper(storage, configuredUrlStore(), authState, engine).restore()

        assertEquals(AuthState.Unauthenticated, authState.state.value)
    }
}
