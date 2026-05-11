package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_profile_email
import com.photonne.app.resources.account_profile_first_name
import com.photonne.app.resources.account_profile_last_name
import com.photonne.app.resources.account_profile_username
import com.photonne.app.resources.account_security_min_length
import com.photonne.app.resources.account_security_new
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_user_action_delete_message
import com.photonne.app.resources.admin_user_action_delete_title
import com.photonne.app.resources.admin_user_action_new
import com.photonne.app.resources.admin_user_action_reset_password
import com.photonne.app.resources.admin_user_action_reset_password_message
import com.photonne.app.resources.admin_user_action_reset_password_title
import com.photonne.app.resources.admin_user_edit_title
import com.photonne.app.resources.admin_user_field_active
import com.photonne.app.resources.admin_user_field_admin
import com.photonne.app.resources.admin_user_field_password
import com.photonne.app.resources.admin_user_field_quota_mb
import com.photonne.app.resources.admin_user_field_quota_mb_hint
import org.jetbrains.compose.resources.stringResource

/** Snapshot of dialog form state passed back to the view-model on save. */
data class AdminUserFormInput(
    val username: String,
    val email: String,
    val password: String?,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val isActive: Boolean,
    val storageQuotaBytes: Long?
)

@Composable
fun AdminUserFormDialog(
    existing: UserDto?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmCreate: (AdminUserFormInput) -> Unit,
    onConfirmEdit: (userId: String, AdminUserFormInput) -> Unit,
    onResetPassword: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isEdit = existing != null

    var username by remember(existing?.id) { mutableStateOf(existing?.username.orEmpty()) }
    var email by remember(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }
    var firstName by remember(existing?.id) { mutableStateOf(existing?.firstName.orEmpty()) }
    var lastName by remember(existing?.id) { mutableStateOf(existing?.lastName.orEmpty()) }
    var password by remember(existing?.id) { mutableStateOf("") }
    var isAdmin by remember(existing?.id) {
        mutableStateOf(existing?.role.equals("Admin", ignoreCase = true))
    }
    var isActive by remember(existing?.id) { mutableStateOf(existing?.isActive ?: true) }
    val initialQuotaMb = existing?.storageQuotaBytes?.takeIf { it > 0 }
        ?.let { (it / (1024L * 1024L)).toString() }
        .orEmpty()
    var quotaMb by remember(existing?.id) { mutableStateOf(initialQuotaMb) }

    val canSubmit = !isSubmitting &&
        username.isNotBlank() &&
        email.isNotBlank() &&
        (isEdit || password.length >= MIN_PASSWORD_LENGTH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEdit) Res.string.admin_user_edit_title
                    else Res.string.admin_user_action_new
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.account_profile_username)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.account_profile_email)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(Res.string.account_profile_first_name)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(Res.string.account_profile_last_name)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isEdit) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.admin_user_field_password)) },
                        singleLine = true,
                        enabled = !isSubmitting,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = {
                            Text(
                                stringResource(
                                    Res.string.account_security_min_length,
                                    MIN_PASSWORD_LENGTH
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = quotaMb,
                    onValueChange = { input ->
                        quotaMb = input.filter { it.isDigit() }
                    },
                    label = { Text(stringResource(Res.string.admin_user_field_quota_mb)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(stringResource(Res.string.admin_user_field_quota_mb_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_user_field_admin),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
                        enabled = !isSubmitting && existing?.isPrimaryAdmin != true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_user_field_active),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        enabled = !isSubmitting && existing?.isPrimaryAdmin != true
                    )
                }

                errorMessage?.let { msg ->
                    Spacer(Modifier.height(4.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }

                if (isEdit && (onResetPassword != null || onDelete != null)) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        onResetPassword?.let { handler ->
                            OutlinedButton(
                                onClick = handler,
                                enabled = !isSubmitting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.admin_user_action_reset_password))
                            }
                        }
                        onDelete?.let { handler ->
                            OutlinedButton(
                                onClick = handler,
                                enabled = !isSubmitting && existing?.isPrimaryAdmin != true,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.action_delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    val quotaBytes = quotaMb.toLongOrNull()?.takeIf { it > 0 }
                        ?.let { it * 1024L * 1024L }
                    val role = if (isAdmin) "Admin" else "User"
                    val input = AdminUserFormInput(
                        username = username,
                        email = email,
                        password = password.takeIf { it.isNotEmpty() },
                        firstName = firstName,
                        lastName = lastName,
                        role = role,
                        isActive = isActive,
                        storageQuotaBytes = quotaBytes
                    )
                    if (isEdit) onConfirmEdit(existing!!.id, input) else onConfirmCreate(input)
                }
            ) {
                Text(
                    stringResource(
                        if (isEdit) Res.string.action_save else Res.string.action_create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
fun AdminDeleteUserDialog(
    user: UserDto,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_user_action_delete_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        Res.string.admin_user_action_delete_message,
                        user.username
                    )
                )
                errorMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting && !user.isPrimaryAdmin
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
fun AdminResetPasswordDialog(
    user: UserDto,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String) -> Unit
) {
    var newPassword by remember(user.id) { mutableStateOf("") }
    val canSubmit = !isSubmitting && newPassword.length >= MIN_PASSWORD_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_user_action_reset_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        Res.string.admin_user_action_reset_password_message,
                        user.username
                    )
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
                                MIN_PASSWORD_LENGTH
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = canSubmit, onClick = { onConfirm(newPassword) }) {
                Text(stringResource(Res.string.admin_user_action_reset_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

private const val MIN_PASSWORD_LENGTH = 8
