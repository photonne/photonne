package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_connection_server
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_settings_device_error_local_invalid
import com.photonne.app.resources.admin_settings_device_error_local_missing
import com.photonne.app.resources.admin_settings_device_error_public_invalid
import com.photonne.app.resources.admin_settings_device_error_public_unreachable
import com.photonne.app.resources.admin_settings_device_local_url
import com.photonne.app.resources.admin_settings_device_local_url_hint
import com.photonne.app.resources.admin_settings_device_probe_button
import com.photonne.app.resources.admin_settings_device_probe_reachable
import com.photonne.app.resources.admin_settings_device_probe_unreachable
import com.photonne.app.resources.admin_settings_device_public_url
import com.photonne.app.resources.admin_settings_device_saved
import com.photonne.app.resources.admin_settings_device_section
import com.photonne.app.resources.admin_settings_device_section_hint
import com.photonne.app.resources.admin_settings_device_status_local
import com.photonne.app.resources.admin_settings_device_status_public
import com.photonne.app.resources.admin_settings_device_status_public_no_local
import com.photonne.app.ui.admin.DeviceConnectionViewModel
import com.photonne.app.ui.admin.SettingSectionHeader
import com.photonne.app.ui.admin.SettingTextField
import com.photonne.app.ui.admin.URL_PLACEHOLDER
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.SecondaryActionButton
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Cuenta → Conexión (Lote N10): a qué servidor habla la app, si va por la red
 * local o la pública, y las URL de ESTE dispositivo. Son ajustes del teléfono
 * ([com.photonne.app.data.api.ServerUrlStore]), no del servidor, así que no
 * tenían por qué vivir solo en la configuración de admin; la URL pública
 * GLOBAL del servidor (la de los enlaces compartidos) sigue siendo de admin.
 */
@Composable
fun AccountConnectionScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: DeviceConnectionViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    LaunchedEffect(Unit) { viewModel.reload() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    FormPageScaffold(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        hasUnsavedChanges = state.hasUnsavedChanges
    ) { page ->
        page {
            DeviceConnectionSection(viewModel)
        }
    }
}

@Composable
private fun DeviceConnectionSection(viewModel: DeviceConnectionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val statusText = when {
        state.localReachable && state.localUrl.isNotBlank() ->
            stringResource(Res.string.admin_settings_device_status_local)
        state.localUrl.isBlank() ->
            stringResource(Res.string.admin_settings_device_status_public_no_local)
        else ->
            stringResource(Res.string.admin_settings_device_status_public)
    }
    // Primero lo que cualquier usuario quiere saber: a qué servidor está
    // hablando la app y por qué red.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                imageVector = if (state.localReachable && state.localUrl.isNotBlank()) {
                    Icons.Outlined.Wifi
                } else {
                    Icons.Outlined.Public
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.account_connection_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    state.effectiveUrl.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.localReachable) PhotonneColors.success
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    SettingSectionHeader(stringResource(Res.string.admin_settings_device_section))
    Text(
        stringResource(Res.string.admin_settings_device_section_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SettingTextField(
        label = stringResource(Res.string.admin_settings_device_public_url),
        value = state.publicUrl,
        enabled = !state.isSaving && !state.isProbing,
        placeholder = URL_PLACEHOLDER
    ) { viewModel.onPublicUrlChange(it) }

    SettingTextField(
        label = stringResource(Res.string.admin_settings_device_local_url),
        value = state.localUrl,
        enabled = !state.isSaving && !state.isProbing,
        supporting = stringResource(Res.string.admin_settings_device_local_url_hint)
    ) { viewModel.onLocalUrlChange(it) }

    val errorText: String? = when (state.errorMessage) {
        DeviceConnectionViewModel.ERROR_LOCAL_MISSING ->
            stringResource(Res.string.admin_settings_device_error_local_missing)
        DeviceConnectionViewModel.ERROR_LOCAL_INVALID ->
            stringResource(Res.string.admin_settings_device_error_local_invalid)
        DeviceConnectionViewModel.ERROR_PUBLIC_INVALID ->
            stringResource(Res.string.admin_settings_device_error_public_invalid)
        DeviceConnectionViewModel.ERROR_PUBLIC_UNREACHABLE ->
            stringResource(Res.string.admin_settings_device_error_public_unreachable)
        else -> state.errorMessage
    }
    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val infoText: String? = when (state.infoMessage) {
        DeviceConnectionViewModel.PROBE_REACHABLE ->
            stringResource(Res.string.admin_settings_device_probe_reachable)
        DeviceConnectionViewModel.PROBE_UNREACHABLE ->
            stringResource(Res.string.admin_settings_device_probe_unreachable)
        DeviceConnectionViewModel.SAVED ->
            stringResource(Res.string.admin_settings_device_saved)
        else -> null
    }
    val infoColor = when (state.infoMessage) {
        DeviceConnectionViewModel.PROBE_UNREACHABLE -> PhotonneColors.warning
        else -> PhotonneColors.success
    }
    infoText?.let { Text(it, color = infoColor) }

    SecondaryActionButton(
        label = stringResource(Res.string.admin_settings_device_probe_button),
        enabled = !state.isSaving && state.localUrl.isNotBlank(),
        isLoading = state.isProbing,
        onClick = viewModel::testLocalConnection
    )
    PrimaryActionButton(
        label = stringResource(Res.string.action_save),
        enabled = !state.isProbing && state.publicUrl.isNotBlank(),
        isLoading = state.isSaving,
        onClick = viewModel::save
    )
}
