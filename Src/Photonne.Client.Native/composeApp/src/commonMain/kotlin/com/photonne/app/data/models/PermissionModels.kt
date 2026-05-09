package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ShareableUser(
    val id: String,
    val username: String,
    val email: String
)

@Serializable
data class AlbumPermission(
    val id: String,
    val userId: String,
    val username: String,
    val email: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canManagePermissions: Boolean,
    @Serializable(with = FlexibleInstantSerializer::class) val grantedAt: Instant,
    val grantedByUserId: String? = null
) {
    val role: AlbumMemberRole get() = when {
        canManagePermissions || canDelete -> AlbumMemberRole.Admin
        canWrite -> AlbumMemberRole.Editor
        else -> AlbumMemberRole.Viewer
    }
}

enum class AlbumMemberRole(
    val label: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canManagePermissions: Boolean
) {
    Viewer(
        label = "Viewer",
        canRead = true,
        canWrite = false,
        canDelete = false,
        canManagePermissions = false
    ),
    Editor(
        label = "Editor",
        canRead = true,
        canWrite = true,
        canDelete = false,
        canManagePermissions = false
    ),
    Admin(
        label = "Admin",
        canRead = true,
        canWrite = true,
        canDelete = true,
        canManagePermissions = true
    );
}
