package com.photonne.native.data.auth

import com.photonne.native.data.api.PhotonneApi

class AuthRepository(
    private val api: PhotonneApi,
    private val tokenStorage: TokenStorage,
    private val authStateHolder: AuthStateHolder
) {
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val deviceId = tokenStorage.getDeviceId()
        val response = api.login(username = username, password = password, deviceId = deviceId)
        tokenStorage.saveTokens(response.token, response.refreshToken)
        authStateHolder.update(AuthState.Authenticated(response.user))
    }

    fun logout() {
        tokenStorage.clear()
        authStateHolder.update(AuthState.Unauthenticated)
    }
}
