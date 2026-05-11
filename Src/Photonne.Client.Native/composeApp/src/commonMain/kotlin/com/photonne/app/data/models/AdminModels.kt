package com.photonne.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null,
    val storageQuotaBytes: Long? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null,
    val storageQuotaBytes: Long? = null
)

@Serializable
data class AdminResetPasswordRequest(val newPassword: String)

@Serializable
data class AdminResetPasswordResponse(val message: String = "")

@Serializable
data class AdminUserUsage(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val photos: Int = 0,
    val videos: Int = 0,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L
)

@Serializable
data class AdminStatsResponse(
    val totalPhotos: Int = 0,
    val totalVideos: Int = 0,
    val totalBytes: Long = 0L,
    val users: List<AdminUserUsage> = emptyList()
)

@Serializable
data class VersionInfoResponse(
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val latestReleaseUrl: String? = null,
    val releaseNotes: String? = null,
    val publishedAt: String? = null,
    val hasUpdate: Boolean = false,
    val checkError: String? = null,
    val checkedAt: String? = null
)

@Serializable
data class TrashStatsResponse(
    val totalItems: Int = 0,
    val totalBytes: Long = 0L,
    val expiredItems: Int = 0,
    val retentionDays: Int = 0,
    val maxQuotaMb: Int = 0,
    val overQuotaUsers: Int = 0,
    val overQuotaBytes: Long = 0L
)

@Serializable
data class TrashCleanupResult(
    val success: Boolean = false,
    val message: String = "",
    val deleted: Int = 0
)
