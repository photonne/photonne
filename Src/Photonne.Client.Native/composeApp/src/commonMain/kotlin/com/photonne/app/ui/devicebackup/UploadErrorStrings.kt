package com.photonne.app.ui.devicebackup

import androidx.compose.runtime.Composable
import com.photonne.app.data.devicebackup.UploadFailureReason
import com.photonne.app.resources.Res
import com.photonne.app.resources.upload_error_file_too_large
import com.photonne.app.resources.upload_error_file_unreadable
import com.photonne.app.resources.upload_error_network_error
import com.photonne.app.resources.upload_error_not_allowed
import com.photonne.app.resources.upload_error_quota_exceeded
import com.photonne.app.resources.upload_error_rate_limited
import com.photonne.app.resources.upload_error_server_error
import com.photonne.app.resources.upload_error_unauthorized
import com.photonne.app.resources.upload_error_unknown
import org.jetbrains.compose.resources.stringResource

/**
 * Localized label for an [UploadFailureReason]. Lives in the UI layer so the
 * viewmodel/repository stay decoupled from Compose resources.
 */
@Composable
fun uploadErrorLabel(reason: UploadFailureReason): String = when (reason) {
    UploadFailureReason.QuotaExceeded -> stringResource(Res.string.upload_error_quota_exceeded)
    UploadFailureReason.FileTooLarge -> stringResource(Res.string.upload_error_file_too_large)
    UploadFailureReason.NotAllowed -> stringResource(Res.string.upload_error_not_allowed)
    UploadFailureReason.Unauthorized -> stringResource(Res.string.upload_error_unauthorized)
    UploadFailureReason.RateLimited -> stringResource(Res.string.upload_error_rate_limited)
    UploadFailureReason.ServerError -> stringResource(Res.string.upload_error_server_error)
    UploadFailureReason.NetworkError -> stringResource(Res.string.upload_error_network_error)
    UploadFailureReason.FileUnreadable -> stringResource(Res.string.upload_error_file_unreadable)
    UploadFailureReason.Unknown -> stringResource(Res.string.upload_error_unknown)
}
