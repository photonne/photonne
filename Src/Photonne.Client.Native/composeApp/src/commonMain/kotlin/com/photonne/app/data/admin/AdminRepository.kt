package com.photonne.app.data.admin

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AdminResetPasswordRequest
import com.photonne.app.data.models.AdminStatsResponse
import com.photonne.app.data.models.CreateUserRequest
import com.photonne.app.data.models.TrashCleanupResult
import com.photonne.app.data.models.TrashStatsResponse
import com.photonne.app.data.models.UpdateUserRequest
import com.photonne.app.data.models.UserDto
import com.photonne.app.data.models.VersionInfoResponse

/**
 * Admin-only operations exposed by `/api/users/*` and `/api/admin/*`.
 * The server gates everything with the `Admin` role policy, so the UI
 * shows the entry point only when the current user has that role.
 */
class AdminRepository(private val api: PhotonneApi) {

    suspend fun listUsers(): List<UserDto> = api.adminListUsers()

    suspend fun createUser(
        username: String,
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        role: String?,
        isActive: Boolean,
        storageQuotaBytes: Long?
    ): UserDto = api.adminCreateUser(
        CreateUserRequest(
            username = username,
            email = email,
            password = password,
            firstName = firstName?.takeIf { it.isNotBlank() },
            lastName = lastName?.takeIf { it.isNotBlank() },
            role = role?.takeIf { it.isNotBlank() },
            isActive = isActive,
            storageQuotaBytes = storageQuotaBytes
        )
    )

    suspend fun updateUser(
        id: String,
        username: String?,
        email: String?,
        firstName: String?,
        lastName: String?,
        role: String?,
        isActive: Boolean?,
        storageQuotaBytes: Long?
    ): UserDto = api.adminUpdateUser(
        id,
        UpdateUserRequest(
            username = username?.takeIf { it.isNotBlank() },
            email = email?.takeIf { it.isNotBlank() },
            firstName = firstName,
            lastName = lastName,
            role = role?.takeIf { it.isNotBlank() },
            isActive = isActive,
            storageQuotaBytes = storageQuotaBytes
        )
    )

    suspend fun deleteUser(id: String) = api.adminDeleteUser(id)

    suspend fun resetUserPassword(id: String, newPassword: String): String =
        api.adminResetUserPassword(id, AdminResetPasswordRequest(newPassword)).message

    suspend fun getStats(): AdminStatsResponse = api.adminGetStats()

    suspend fun getVersion(): VersionInfoResponse = api.adminGetVersion()

    suspend fun getTrashStats(): TrashStatsResponse = api.adminGetTrashStats()

    suspend fun cleanupExpiredTrash(): TrashCleanupResult = api.adminCleanupExpiredTrash()
}
