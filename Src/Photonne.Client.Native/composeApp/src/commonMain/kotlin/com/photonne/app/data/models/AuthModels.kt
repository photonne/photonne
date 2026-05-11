package com.photonne.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: UserDto
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
    val deviceId: String
)

@Serializable
data class RefreshTokenResponse(
    val token: String,
    val refreshToken: String
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val isActive: Boolean = true,
    val isPrimaryAdmin: Boolean = false,
    @SerialName("storageQuotaBytes") val storageQuotaBytes: Long? = null,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)

@Serializable
data class ApiError(val error: String)

/**
 * Profile fields the user can edit from the account settings → profile
 * page. Mirrors `UpdateProfileRequest` on `UsersEndpoint.cs:486`: every
 * field is optional so we can patch a single value at a time, but the
 * UI always submits the four together with the current values.
 */
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordResponse(val message: String)

/** Storage usage shown in the account settings → storage page. */
@Serializable
data class StorageInfoDto(
    val usedBytes: Long,
    val quotaBytes: Long? = null
)
