package com.photonne.app.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.photonne.app.ui.main.ResultSnackbar
import com.photonne.app.ui.theme.PrimaryActionButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.theme.actionButtonHeight
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_delete_button
import com.photonne.app.resources.account_delete_confirm
import com.photonne.app.resources.account_delete_dialog_message
import com.photonne.app.resources.account_delete_dialog_title
import com.photonne.app.resources.account_delete_password
import com.photonne.app.resources.account_delete_section
import com.photonne.app.resources.account_delete_summary
import com.photonne.app.resources.account_security_changed
import com.photonne.app.resources.account_security_confirm
import com.photonne.app.resources.account_security_current
import com.photonne.app.resources.account_security_min_length
import com.photonne.app.resources.account_security_mismatch
import com.photonne.app.resources.account_security_new
import com.photonne.app.resources.account_security_submit
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.Spacing

@Composable
fun AccountSecurityScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AccountSecurityViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The result goes where every other one in the app goes: the snackbar.
    ResultSnackbar(
        message = stringResource(Res.string.account_security_changed).takeIf { state.successMessage != null },
        onShown = viewModel::consumeSuccess
    )

    // Un único toggle para los tres campos: si enseñas una, quieres verlas.
    var passwordsVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
    FormPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            OutlinedTextField(
                value = state.currentPassword,
                onValueChange = viewModel::onCurrentChange,
                label = { Text(stringResource(Res.string.account_security_current)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                visualTransformation = if (passwordsVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = { VisibilityToggle(passwordsVisible) { passwordsVisible = it } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.newPassword,
                onValueChange = viewModel::onNewChange,
                label = { Text(stringResource(Res.string.account_security_new)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.newPasswordTooShort,
                supportingText = if (state.newPasswordTooShort) {
                    {
                        Text(
                            stringResource(
                                Res.string.account_security_min_length,
                                AccountSecurityUiState.MIN_LENGTH
                            )
                        )
                    }
                } else null,
                visualTransformation = if (passwordsVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = { Text(stringResource(Res.string.account_security_confirm)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.mismatch,
                supportingText = if (state.mismatch) {
                    { Text(stringResource(Res.string.account_security_mismatch)) }
                } else null,
                visualTransformation = if (passwordsVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (state.canSave) viewModel.submit()
                }),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.userMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.xs))
            PrimaryActionButton(
                label = stringResource(Res.string.account_security_submit),
                enabled = state.canSave,
                isLoading = state.isSubmitting,
                onClick = viewModel::submit
            )

            // Borrar la cuenta desde la app: lo exigen las tiendas a toda app
            // donde se pueden crear cuentas.
            com.photonne.app.ui.admin.SettingSectionHeader(stringResource(Res.string.account_delete_section))
            Text(
                stringResource(Res.string.account_delete_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.OutlinedButton(
                onClick = viewModel::openDeleteAccount,
                enabled = !state.isSubmitting,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().actionButtonHeight()
            ) {
                Text(stringResource(Res.string.account_delete_button))
            }
        }
    }

    state.deleteAccount?.let { dialog ->
        var deletePasswordVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
        ConfirmActionDialog(
            title = stringResource(Res.string.account_delete_dialog_title),
            message = stringResource(Res.string.account_delete_dialog_message),
            confirmLabel = stringResource(Res.string.account_delete_confirm),
            isDestructive = true,
            isSubmitting = dialog.isDeleting,
            errorMessage = dialog.error?.userMessage,
            confirmEnabled = dialog.canConfirm,
            onDismiss = viewModel::dismissDeleteAccount,
            onConfirm = viewModel::confirmDeleteAccount
        ) {
            OutlinedTextField(
                value = dialog.password,
                onValueChange = viewModel::onDeletePasswordChange,
                label = { Text(stringResource(Res.string.account_delete_password)) },
                singleLine = true,
                enabled = !dialog.isDeleting,
                visualTransformation = if (deletePasswordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = { VisibilityToggle(deletePasswordVisible) { deletePasswordVisible = it } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.confirmDeleteAccount() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.IconButton(onClick = { onChange(!visible) }) {
        androidx.compose.material3.Icon(
            if (visible) androidx.compose.material.icons.Icons.Outlined.VisibilityOff
            else androidx.compose.material.icons.Icons.Outlined.Visibility,
            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
        )
    }
}
