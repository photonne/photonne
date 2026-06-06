package com.photonne.app.data.auth

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.devicebackup.BackupLedger

class AuthRepository(
    private val api: PhotonneApi,
    private val tokenStorage: TokenStorage,
    private val authStateHolder: AuthStateHolder,
    private val backupLedger: BackupLedger,
    private val serverUrlStore: ServerUrlStore
) {
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val deviceId = tokenStorage.getDeviceId()
        val response = api.login(username = username, password = password, deviceId = deviceId)
        tokenStorage.saveTokens(response.token, response.refreshToken)
        authStateHolder.update(AuthState.Authenticated(response.user))
        // The backup ledger's verdicts are only valid for one account on one
        // server. Logging into a different one wipes them so stale "synced"
        // flags never leak across accounts.
        runCatching {
            val server = serverUrlStore.getPublic() ?: serverUrlStore.getLocal().orEmpty()
            backupLedger.ensureScope("$server|${response.user.username}")
        }
    }

    fun logout() {
        tokenStorage.clear()
        authStateHolder.update(AuthState.Unauthenticated)
    }
}
