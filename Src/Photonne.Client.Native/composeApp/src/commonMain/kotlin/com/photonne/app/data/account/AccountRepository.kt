package com.photonne.app.data.account

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.models.ChangePasswordRequest
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
    private val authStateHolder: AuthStateHolder
) {
    suspend fun refreshCurrentUser(): UserDto {
        val user = api.getCurrentUser()
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
        authStateHolder.update(AuthState.Authenticated(updated))
        return updated
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        api.changePassword(
            ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = newPassword
            )
        )
    }

    suspend fun getStorageInfo(): StorageInfoDto = api.getStorageInfo()
}
