package com.photonne.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_leave
import com.photonne.app.resources.action_save
import com.photonne.app.resources.album_leave_message
import com.photonne.app.resources.album_leave_title
import com.photonne.app.resources.share_action_copy
import com.photonne.app.resources.share_action_edit
import com.photonne.app.resources.share_action_new
import com.photonne.app.resources.share_action_revoke
import com.photonne.app.resources.share_action_share_failed
import com.photonne.app.resources.share_action_share_link
import com.photonne.app.resources.share_attribute_expiry_format
import com.photonne.app.resources.share_attribute_no_downloads
import com.photonne.app.resources.share_attribute_password
import com.photonne.app.resources.share_attribute_upload
import com.photonne.app.resources.share_attribute_uploads_format
import com.photonne.app.resources.share_attribute_views_format
import com.photonne.app.resources.share_create_title
import com.photonne.app.resources.share_edit_title
import com.photonne.app.resources.share_empty
import com.photonne.app.resources.share_option_allow_downloads
import com.photonne.app.resources.share_option_allow_upload
import com.photonne.app.resources.share_option_allow_upload_hint
import com.photonne.app.resources.share_option_expiry
import com.photonne.app.resources.share_option_expiry_change
import com.photonne.app.resources.share_option_expiry_pick
import com.photonne.app.resources.share_option_max_views
import com.photonne.app.resources.share_option_max_views_field
import com.photonne.app.resources.share_option_password
import com.photonne.app.resources.share_link_copied
import com.photonne.app.resources.share_option_password_field
import com.photonne.app.resources.share_password_change
import com.photonne.app.resources.share_password_keep
import com.photonne.app.resources.share_password_remove
import com.photonne.app.resources.share_title
import com.photonne.app.ui.main.LocalSnackbarController
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSharesDialog(
    state: AlbumSharesUiState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (AlbumShareLink) -> Unit,
    onRevoke: (token: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarController.current
    val copiedMessage = stringResource(Res.string.share_link_copied)
    val shareFailedMessage = stringResource(Res.string.share_action_share_failed)
    val sharing: com.photonne.app.ui.actions.AssetSharing = koinInject()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!state.isMutating) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.share_title), style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.heightIn(min = 120.dp, max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.isLoading && state.links.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    state.links.isEmpty() -> Text(
                        stringResource(Res.string.share_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.links, key = { it.token }) { link ->
                            ShareLinkRow(
                                link = link,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(link.shareUrl))
                                    snackbar?.show(copiedMessage)
                                },
                                onShare = {
                                    scope.launch {
                                        runCatching { sharing.shareText(link.shareUrl) }
                                            .onFailure { error ->
                                                snackbar?.show(
                                                    error.message ?: shareFailedMessage
                                                )
                                            }
                                    }
                                },
                                onEdit = { onEdit(link) },
                                onRevoke = { onRevoke(link.token) }
                            )
                        }
                    }
                }
                state.error?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(err.userMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCreate, enabled = !state.isMutating) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.share_action_new))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss, enabled = !state.isMutating) {
                    Text(stringResource(Res.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun ShareLinkRow(
    link: AlbumShareLink,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.shareUrl.ifBlank { "/share/${link.token}" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            val passwordAttr = stringResource(Res.string.share_attribute_password)
            val noDownloadsAttr = stringResource(Res.string.share_attribute_no_downloads)
            val viewsAttr = stringResource(Res.string.share_attribute_views_format, link.viewCount)
            val expiryFormat = stringResource(Res.string.share_attribute_expiry_format, "")
            val uploadAttr = stringResource(Res.string.share_attribute_upload)
            val uploadsAttr = stringResource(Res.string.share_attribute_uploads_format, link.uploadCount)
            val attrs = buildList {
                if (link.hasPassword) add(passwordAttr)
                link.expiresAt?.let { add(expiryFormat.trim() + " " + formatExpiry(it)) }
                link.maxViews?.let { add("max $it") }
                add(viewsAttr)
                if (!link.allowDownload) add(noDownloadsAttr)
                if (link.allowUpload) {
                    add(uploadAttr)
                    if (link.uploadCount > 0) add(uploadsAttr)
                }
            }
            Text(
                text = attrs.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(Res.string.share_action_copy)
            )
        }
        // Copiar servía para pegar a mano; compartir manda el enlace por
        // donde el usuario lo manda todo (WhatsApp, correo…).
        IconButton(onClick = onShare) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = stringResource(Res.string.share_action_share_link)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(Res.string.share_action_edit)
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.share_action_revoke),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatExpiry(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return date.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShareDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?,
        allowUpload: Boolean
    ) -> Unit
) {
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var allowDownload by remember { mutableStateOf(true) }
    var allowUpload by remember { mutableStateOf(false) }
    var maxViewsEnabled by remember { mutableStateOf(false) }
    var maxViews by remember { mutableStateOf("") }
    var expiryEnabled by remember { mutableStateOf(false) }
    var expiryDate by remember { mutableStateOf<LocalDate?>(null) }
    var showExpiryPicker by remember { mutableStateOf(false) }

    val canSubmit = !isSubmitting &&
        (!passwordEnabled || password.trim().isNotEmpty()) &&
        (!maxViewsEnabled || maxViews.toIntOrNull()?.let { it > 0 } == true) &&
        (!expiryEnabled || expiryDate != null)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // Con teclado abierto en un móvil bajo, sin scroll los campos
                // del fondo (caducidad, guardar) quedaban inalcanzables.
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.share_create_title),
                style = MaterialTheme.typography.titleLarge
            )
            ToggleRow(
                label = stringResource(Res.string.share_option_allow_downloads),
                checked = allowDownload,
                onCheckedChange = { allowDownload = it },
                enabled = !isSubmitting
            )
            ToggleRow(
                label = stringResource(Res.string.share_option_allow_upload),
                checked = allowUpload,
                onCheckedChange = { allowUpload = it },
                enabled = !isSubmitting
            )
            if (allowUpload) {
                Text(
                    stringResource(Res.string.share_option_allow_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ToggleRow(
                label = stringResource(Res.string.share_option_password),
                checked = passwordEnabled,
                onCheckedChange = { passwordEnabled = it },
                enabled = !isSubmitting
            )
            if (passwordEnabled) {
                SharePasswordField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !isSubmitting
                )
            }
            ToggleRow(
                label = stringResource(Res.string.share_option_max_views),
                checked = maxViewsEnabled,
                onCheckedChange = { maxViewsEnabled = it },
                enabled = !isSubmitting
            )
            if (maxViewsEnabled) {
                OutlinedTextField(
                    value = maxViews,
                    onValueChange = { input -> maxViews = input.filter { it.isDigit() } },
                    label = { Text(stringResource(Res.string.share_option_max_views_field)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ToggleRow(
                label = stringResource(Res.string.share_option_expiry),
                checked = expiryEnabled,
                onCheckedChange = {
                    expiryEnabled = it
                    if (!it) expiryDate = null
                },
                enabled = !isSubmitting
            )
            if (expiryEnabled) {
                TextButton(
                    onClick = { showExpiryPicker = true },
                    enabled = !isSubmitting
                ) {
                    Text(
                        expiryDate?.toString()
                            ?: stringResource(Res.string.share_option_expiry_pick)
                    )
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(
                            if (expiryEnabled) expiryDate?.let(::endOfDayInstant) else null,
                            if (passwordEnabled) password else null,
                            allowDownload,
                            if (maxViewsEnabled) maxViews.toIntOrNull() else null,
                            allowUpload
                        )
                    },
                    enabled = canSubmit
                ) { Text(stringResource(Res.string.action_create)) }
            }
        }
    }
    if (showExpiryPicker) {
        ShareExpiryDatePickerDialog(
            initial = expiryDate,
            onDismiss = { showExpiryPicker = false },
            onPick = { picked ->
                if (picked != null) expiryDate = picked
                showExpiryPicker = false
            }
        )
    }
}

@Composable
fun LeaveAlbumDialog(
    albumName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // Delegado al diálogo estándar (punto 44): mismo comportamiento, un
    // único sitio que mantener para progreso, error inline y botones.
    com.photonne.app.ui.library.ConfirmActionDialog(
        title = stringResource(Res.string.album_leave_title),
        message = stringResource(Res.string.album_leave_message, albumName),
        confirmLabel = stringResource(Res.string.action_leave),
        isDestructive = true,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

private enum class EditPasswordAction { Keep, Remove, Change }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShareDialog(
    link: AlbumShareLink,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?,
        allowUpload: Boolean
    ) -> Unit
) {
    var allowDownload by remember(link.token) { mutableStateOf(link.allowDownload) }
    var allowUpload by remember(link.token) { mutableStateOf(link.allowUpload) }
    var expiryEnabled by remember(link.token) { mutableStateOf(link.expiresAt != null) }
    var expiryDate by remember(link.token) {
        mutableStateOf(
            link.expiresAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
        )
    }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var maxViewsEnabled by remember(link.token) { mutableStateOf(link.maxViews != null) }
    var maxViews by remember(link.token) { mutableStateOf(link.maxViews?.toString() ?: "") }
    var passwordAction by remember(link.token) {
        mutableStateOf(if (link.hasPassword) EditPasswordAction.Keep else EditPasswordAction.Remove)
    }
    var newPassword by remember(link.token) { mutableStateOf("") }

    val canSubmit = !isSubmitting &&
        (passwordAction != EditPasswordAction.Change || newPassword.trim().isNotEmpty()) &&
        (!maxViewsEnabled || maxViews.toIntOrNull()?.let { it > 0 } == true) &&
        (!expiryEnabled || expiryDate != null)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.share_edit_title),
                style = MaterialTheme.typography.titleLarge
            )
            ToggleRow(
                label = stringResource(Res.string.share_option_allow_downloads),
                checked = allowDownload,
                onCheckedChange = { allowDownload = it },
                enabled = !isSubmitting
            )
            ToggleRow(
                label = stringResource(Res.string.share_option_allow_upload),
                checked = allowUpload,
                onCheckedChange = { allowUpload = it },
                enabled = !isSubmitting
            )
            if (allowUpload) {
                Text(
                    stringResource(Res.string.share_option_allow_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                stringResource(Res.string.share_option_password),
                style = MaterialTheme.typography.titleSmall
            )
            if (link.hasPassword) {
                PasswordRadioRow(
                    label = stringResource(Res.string.share_password_keep),
                    selected = passwordAction == EditPasswordAction.Keep,
                    enabled = !isSubmitting,
                    onSelect = { passwordAction = EditPasswordAction.Keep }
                )
                PasswordRadioRow(
                    label = stringResource(Res.string.share_password_remove),
                    selected = passwordAction == EditPasswordAction.Remove,
                    enabled = !isSubmitting,
                    onSelect = { passwordAction = EditPasswordAction.Remove }
                )
            }
            PasswordRadioRow(
                label = stringResource(Res.string.share_password_change),
                selected = passwordAction == EditPasswordAction.Change,
                enabled = !isSubmitting,
                onSelect = { passwordAction = EditPasswordAction.Change }
            )
            if (passwordAction == EditPasswordAction.Change) {
                SharePasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    enabled = !isSubmitting
                )
            }

            ToggleRow(
                label = stringResource(Res.string.share_option_max_views),
                checked = maxViewsEnabled,
                onCheckedChange = {
                    maxViewsEnabled = it
                    if (!it) maxViews = ""
                },
                enabled = !isSubmitting
            )
            if (maxViewsEnabled) {
                OutlinedTextField(
                    value = maxViews,
                    onValueChange = { input -> maxViews = input.filter { it.isDigit() } },
                    label = { Text(stringResource(Res.string.share_option_max_views_field)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ToggleRow(
                label = stringResource(Res.string.share_option_expiry),
                checked = expiryEnabled,
                onCheckedChange = {
                    expiryEnabled = it
                    if (!it) expiryDate = null
                },
                enabled = !isSubmitting
            )
            if (expiryEnabled) {
                TextButton(
                    onClick = { showExpiryPicker = true },
                    enabled = !isSubmitting
                ) {
                    Text(
                        expiryDate?.toString()
                            ?: stringResource(Res.string.share_option_expiry_pick)
                    )
                }
            }

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val passwordPayload = when (passwordAction) {
                            EditPasswordAction.Keep -> null
                            EditPasswordAction.Remove -> ""
                            EditPasswordAction.Change -> newPassword
                        }
                        onConfirm(
                            if (expiryEnabled) expiryDate?.let(::endOfDayInstant) else null,
                            passwordPayload,
                            allowDownload,
                            if (maxViewsEnabled) maxViews.toIntOrNull() else null,
                            allowUpload
                        )
                    },
                    enabled = canSubmit
                ) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }
    if (showExpiryPicker) {
        ShareExpiryDatePickerDialog(
            initial = expiryDate,
            onDismiss = { showExpiryPicker = false },
            onPick = { picked ->
                if (picked != null) expiryDate = picked
                showExpiryPicker = false
            }
        )
    }
}

@Composable
private fun PasswordRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareExpiryDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate?) -> Unit
) {
    val initialMillis = initial?.let {
        it.toEpochDays().toLong() * 86_400_000L
    }
    // Una caducidad en el pasado crea un enlace muerto al nacer.
    val todayEpochDays = kotlin.time.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (utcTimeMillis / 86_400_000L) >= todayEpochDays
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val days = (millis / 86_400_000L).toInt()
                    onPick(LocalDate.fromEpochDays(days))
                } else {
                    onPick(null)
                }
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun endOfDayInstant(date: LocalDate): Instant {
    val tz = TimeZone.currentSystemDefault()
    return LocalDateTime(date, LocalTime(23, 59, 59)).toInstant(tz)
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
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * Campo de contraseña de enlace: oculto por defecto con ojo para verlo, y
 * teclado de contraseña (antes iba en claro con teclado normal).
 */
@Composable
private fun SharePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.share_option_password_field)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) androidx.compose.material.icons.Icons.Outlined.VisibilityOff
                    else androidx.compose.material.icons.Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña"
                    else "Mostrar contraseña"
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}
