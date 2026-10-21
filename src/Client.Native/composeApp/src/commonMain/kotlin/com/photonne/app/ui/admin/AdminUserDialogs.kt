package com.photonne.app.ui.admin

import com.photonne.app.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_security_min_length
import com.photonne.app.resources.account_security_new
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.admin_user_action_delete_message
import com.photonne.app.resources.admin_user_action_delete_title
import com.photonne.app.resources.admin_user_action_promote_confirm
import com.photonne.app.resources.admin_user_action_promote_message
import com.photonne.app.resources.admin_user_action_promote_title
import com.photonne.app.resources.admin_user_action_reset_password
import com.photonne.app.resources.admin_user_action_reset_password_message
import com.photonne.app.resources.admin_user_action_reset_password_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.CompactPrimaryButton
import com.photonne.app.ui.theme.CompactSecondaryButton
import com.photonne.app.ui.theme.SheetActions
import com.photonne.app.ui.library.ConfirmActionDialog

@Composable
fun AdminDeleteUserDialog(
    user: UserDto,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ConfirmActionDialog(
        title = stringResource(Res.string.admin_user_action_delete_title),
        message = stringResource(Res.string.admin_user_action_delete_message, user.username),
        confirmLabel = stringResource(Res.string.action_delete),
        isDestructive = true,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        confirmEnabled = !user.isPrimaryAdmin,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun AdminPromoteToPrimaryDialog(
    targetUser: UserDto,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ConfirmActionDialog(
        title = stringResource(Res.string.admin_user_action_promote_title),
        message = stringResource(Res.string.admin_user_action_promote_message, targetUser.username),
        confirmLabel = stringResource(Res.string.admin_user_action_promote_confirm),
        isDestructive = false,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminResetPasswordDialog(
    user: UserDto,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String) -> Unit
) {
    var newPassword by remember(user.id) { mutableStateOf("") }
    val canSubmit = !isSubmitting && newPassword.length >= ADMIN_USER_MIN_PASSWORD_LENGTH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(stringResource(Res.string.admin_user_action_reset_password_title))
            Text(
                stringResource(
                    Res.string.admin_user_action_reset_password_message,
                    user.username
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(Res.string.account_security_new)) },
                singleLine = true,
                enabled = !isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text(
                        stringResource(
                            Res.string.account_security_min_length,
                            ADMIN_USER_MIN_PASSWORD_LENGTH
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
            SheetActions {
                CompactSecondaryButton(
                    label = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    enabled = !isSubmitting
                )
                CompactPrimaryButton(
                    label = stringResource(Res.string.admin_user_action_reset_password),
                    onClick = { onConfirm(newPassword) },
                    enabled = canSubmit,
                    isLoading = isSubmitting
                )
            }
        }
    }
}
