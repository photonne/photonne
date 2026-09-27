package com.photonne.app.data.auth

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.devicebackup.BackgroundSyncScheduler
import com.photonne.app.data.devicebackup.BackupLedger
import com.photonne.app.data.devicebackup.DeviceBackupStateStore
import com.photonne.app.data.models.UserDto
import com.photonne.app.data.notifications.ActivityNotifications

class AuthRepository(
    private val api: PhotonneApi,
    private val tokenStorage: TokenStorage,
    private val authStateHolder: AuthStateHolder,
    private val backupLedger: BackupLedger,
    private val serverUrlStore: ServerUrlStore,
    private val backupStateStore: DeviceBackupStateStore,
    private val backupScheduler: BackgroundSyncScheduler,
    private val activityNotifications: ActivityNotifications
) {
    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val deviceId = tokenStorage.getDeviceId()
        val response = api.login(username = username, password = password, deviceId = deviceId)
        tokenStorage.saveTokens(response.token, response.refreshToken)
        tokenStorage.saveUser(response.user)
        // Before publishing the session: the backup view model of the new
        // session reads the backup setup as soon as it's created, so it must
        // already be the right one for this account.
        runCatching { bindBackupToAccount(response.user) }
        // Avisos de actividad con la app cerrada: solo con sesión.
        activityNotifications.reconcile()
        authStateHolder.update(AuthState.Authenticated(response.user))
    }

    /**
     * The backup ledger's verdicts and the backup setup itself (switch,
     * origins) belong to one account on one server. Same account as last
     * time: everything stays and the schedule is re-armed. A different one:
     * the ledger is wiped (stale "synced" flags must never leak across
     * accounts) and backup is switched off with no origins, so the phone is
     * never uploaded into the new account without the user asking.
     *
     * Keyed by user id, not username: a rename used to look like a new
     * account and wiped the ledger (full rehash, "Omitir" choices lost).
     * The legacy "server|username" key of the same user is adopted.
     */
    private fun bindBackupToAccount(user: UserDto) {
        val server = serverUrlStore.getPublic() ?: serverUrlStore.getLocal().orEmpty()
        val change = backupLedger.ensureScope(
            scope = "$server|${user.id}",
            legacyScope = "$server|${user.username}"
        )
        if (change == BackupLedger.ScopeChange.Switched) {
            backupStateStore.resetForNewAccount()
        }
        backupScheduler.apply(backupStateStore.backgroundSyncPreferences())
    }

    fun logout() {
        stopBackupForSignOut()
        tokenStorage.clear()
        authStateHolder.update(AuthState.Unauthenticated)
    }

    /**
     * Signing out ends the backup too: nothing scheduled keeps uploading to an
     * account nobody is signed into. The setup itself stays, so the same
     * account signing back in picks up where it left off. Shared by the
     * voluntary [logout] and the forced one of an expired session (the
     * refresh plugin calls this after moving to [AuthState.SessionExpired]).
     */
    fun stopBackupForSignOut() {
        runCatching {
            backupScheduler.cancelForegroundBackup()
            backupScheduler.apply(
                backupStateStore.backgroundSyncPreferences().copy(enabled = false)
            )
        }
        // Tampoco quedan avisos de actividad de una cuenta sin sesión.
        runCatching { activityNotifications.onSignedOut() }
    }
}
