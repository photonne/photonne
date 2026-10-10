package com.photonne.app.data.auth

import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.api.ServerUrlStore

/**
 * Restores the authenticated session on app startup. Tokens already persist
 * across restarts ([TokenStorage]); without this, the UI always booted into
 * [AuthState.Unknown] → the login screen even though a valid session existed.
 *
 * The restore is optimistic and offline-friendly: if a refresh token, a
 * configured server URL and a cached [com.photonne.app.data.models.UserDto]
 * are present, the session is restored immediately (no network, no login
 * flash), then validated/refreshed in the background. A definitive 401/403 on
 * that refresh logs out via [AuthRefreshPlugin]; transient failures (offline,
 * 5xx) keep the session.
 */
class SessionBootstrapper(
    private val tokenStorage: TokenStorage,
    private val serverUrlStore: ServerUrlStore,
    private val authStateHolder: AuthStateHolder,
    private val accountRepository: AccountRepository
) {
    suspend fun restore() {
        val hasRefreshToken = tokenStorage.getRefreshToken() != null
        val hasServerUrl =
            !serverUrlStore.getPublic().isNullOrEmpty() || !serverUrlStore.getLocal().isNullOrEmpty()
        if (!hasRefreshToken || !hasServerUrl) {
            authStateHolder.update(AuthState.Unauthenticated)
            return
        }

        val cached = tokenStorage.getUser()
        if (cached != null) {
            authStateHolder.update(AuthState.Authenticated(cached))
        }

        // Validate the session against the server. On success the cached user
        // is refreshed; the AuthRefreshPlugin handles token refresh and only
        // clears the session on a definitive auth rejection (401/403).
        runCatching { accountRepository.refreshCurrentUser() }
            .onFailure {
                // No cached user to fall back on and the validation could not
                // complete (e.g. offline): there is nothing to show, so land
                // on the login screen unless a 401/403 already did.
                if (cached == null && authStateHolder.state.value is AuthState.Unknown) {
                    authStateHolder.update(AuthState.Unauthenticated)
                }
            }
    }
}
