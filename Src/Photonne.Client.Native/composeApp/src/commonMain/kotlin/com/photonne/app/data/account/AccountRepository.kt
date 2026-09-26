package com.photonne.app.data.account

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.RememberedCredentialsStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.ChangePasswordRequest
import com.photonne.app.data.models.RenamePreviewDto
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.data.models.UpdateProfileRequest
import com.photonne.app.data.models.UserDto

/**
 * Account-settings repository. Wraps `/api/users/me*` so the settings
 * view-models stay free of Ktor concerns, and pushes the updated user
 * back into [AuthStateHolder] so `MoreScreen` / the top bar reflect a
 * rename or email change without needing a reload.
 */
class AccountRepository(
    private val api: PhotonneApi,
    private val authStateHolder: AuthStateHolder,
    private val tokenStorage: TokenStorage,
    private val rememberedCredentials: RememberedCredentialsStore
) {
    suspend fun refreshCurrentUser(): UserDto {
        val user = api.getCurrentUser()
        tokenStorage.saveUser(user)
        authStateHolder.update(AuthState.Authenticated(user))
        return user
    }

    suspend fun updateProfile(
        username: String?,
        email: String?,
        firstName: String?,
        lastName: String?
    ): UserDto {
        val updated = api.updateProfile(
            UpdateProfileRequest(
                username = username?.takeIf { it.isNotBlank() },
                email = email?.takeIf { it.isNotBlank() },
                firstName = firstName,
                lastName = lastName
            )
        )
        tokenStorage.saveUser(updated)
        authStateHolder.update(AuthState.Authenticated(updated))
        // "Recordar credenciales" stored the old username: without this the
        // next manual sign-in would pre-fill a login that no longer exists.
        rememberedCredentials.get()?.let { saved ->
            if (saved.username != updated.username) {
                rememberedCredentials.save(updated.username, saved.password)
            }
        }
        return updated
    }

    /** What renaming to [newUsername] would move on the server. */
    suspend fun previewRename(newUsername: String): RenamePreviewDto =
        api.previewMyRename(newUsername)

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        api.changePassword(
            ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = newPassword
            )
        )
        // Same for the remembered password: keep it working after the change.
        rememberedCredentials.get()?.let { saved ->
            rememberedCredentials.save(saved.username, newPassword)
        }
    }

    suspend fun getStorageInfo(): StorageInfoDto = api.getStorageInfo()
}
