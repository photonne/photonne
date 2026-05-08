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
    @SerialName("storageQuotaBytes") val storageQuotaBytes: Long? = null
)

@Serializable
data class ApiError(val error: String)
