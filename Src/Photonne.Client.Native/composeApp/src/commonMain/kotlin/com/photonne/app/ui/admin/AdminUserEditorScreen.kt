package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_user_action_promote
import com.photonne.app.resources.admin_user_action_reset_password
import com.photonne.app.resources.admin_user_field_active
import com.photonne.app.resources.admin_user_field_admin
import com.photonne.app.resources.admin_user_field_password
import com.photonne.app.resources.admin_user_field_quota_mb
import com.photonne.app.resources.admin_user_field_quota_mb_hint
import com.photonne.app.resources.admin_user_promote_success
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

internal const val ADMIN_USER_MIN_PASSWORD_LENGTH = 8

/**
 * Full-page editor used to create a new user or edit an existing one,
 * replacing the previous AlertDialog. Reset-password and Delete remain
 * available as nested confirmations (bottom sheet / alert dialog).
 */
@Composable
fun AdminUserEditorScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminUsersViewModel,
    userId: String?,
    onDone: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val existing = userId?.let { id -> state.users.firstOrNull { it.id == id } }
    val isEdit = userId != null

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

    var showResetPassword by remember(existing?.id) { mutableStateOf(false) }
    var showDelete by remember(existing?.id) { mutableStateOf(false) }
    var showPromoteToPrimary by remember(existing?.id) { mutableStateOf(false) }

    val canPromoteToPrimary = isEdit
        && existing != null
        && state.isCurrentUserPrimaryAdmin
        && existing.role.equals("Admin", ignoreCase = true)
        && existing.isActive
        && !existing.isPrimaryAdmin
    val promoteSuccessMessage = if (existing != null) {
        stringResource(Res.string.admin_user_promote_success, existing.username)
    } else {
        ""
    }

    val isSubmitting = state.isMutating
    val canSubmit = !isSubmitting &&
        username.isNotBlank() &&
        email.isNotBlank() &&
        (isEdit || password.length >= ADMIN_USER_MIN_PASSWORD_LENGTH)

    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    if (isEdit && existing == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .hazeSource(hazeState)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp + reservedTop, bottom = 12.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            ADMIN_USER_MIN_PASSWORD_LENGTH
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = quotaMb,
            onValueChange = { input -> quotaMb = input.filter { it.isDigit() } },
            label = { Text(stringResource(Res.string.admin_user_field_quota_mb)) },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(stringResource(Res.string.admin_user_field_quota_mb_hint))
            },
            modifier = Modifier.fillMaxWidth()
        )

        ToggleRow(
            label = stringResource(Res.string.admin_user_field_admin),
            checked = isAdmin,
            onCheckedChange = { isAdmin = it },
            enabled = !isSubmitting && existing?.isPrimaryAdmin != true
        )
        ToggleRow(
            label = stringResource(Res.string.admin_user_field_active),
            checked = isActive,
            onCheckedChange = { isActive = it },
            enabled = !isSubmitting && existing?.isPrimaryAdmin != true
        )

        state.error?.userMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val quotaBytes = quotaMb.toLongOrNull()?.takeIf { it > 0 }
                    ?.let { it * 1024L * 1024L }
                val role = if (isAdmin) "Admin" else "User"
                if (isEdit) {
                    viewModel.update(
                        userId = existing!!.id,
                        username = username,
                        email = email,
                        firstName = firstName,
                        lastName = lastName,
                        role = role,
                        isActive = isActive,
                        storageQuotaBytes = quotaBytes,
                        onDone = onDone
                    )
                } else {
                    viewModel.create(
                        username = username,
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        role = role,
                        isActive = isActive,
                        storageQuotaBytes = quotaBytes,
                        onDone = onDone
                    )
                }
            }
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                stringResource(
                    if (isEdit) Res.string.action_save else Res.string.action_create
                )
            )
        }

        if (isEdit) {
            OutlinedButton(
                onClick = { showResetPassword = true },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.admin_user_action_reset_password))
            }
            if (canPromoteToPrimary) {
                OutlinedButton(
                    onClick = { showPromoteToPrimary = true },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.admin_user_action_promote))
                }
            }
            OutlinedButton(
                onClick = { showDelete = true },
                enabled = !isSubmitting && existing?.isPrimaryAdmin != true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(Res.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showResetPassword && existing != null) {
        AdminResetPasswordDialog(
            user = existing,
            isSubmitting = state.isMutating,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                showResetPassword = false
                viewModel.clearMessages()
            },
            onConfirm = { newPassword ->
                viewModel.resetPassword(existing.id, newPassword) {
                    showResetPassword = false
                }
            }
        )
    }
    if (showDelete && existing != null) {
        AdminDeleteUserDialog(
            user = existing,
            isSubmitting = state.isMutating,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                showDelete = false
                viewModel.clearMessages()
            },
            onConfirm = {
                viewModel.delete(existing.id) {
                    showDelete = false
                    onDone()
                }
            }
        )
    }
    if (showPromoteToPrimary && existing != null) {
        AdminPromoteToPrimaryDialog(
            targetUser = existing,
            isSubmitting = state.isMutating,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                showPromoteToPrimary = false
                viewModel.clearMessages()
            },
            onConfirm = {
                viewModel.promoteToPrimary(existing.id, promoteSuccessMessage) {
                    showPromoteToPrimary = false
                }
            }
        )
    }
    }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
