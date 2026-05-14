package com.photonne.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.photonne.app.data.models.UserDto
import com.photonne.app.ui.theme.actionButtonHeight
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_profile_email
import com.photonne.app.resources.account_profile_first_name
import com.photonne.app.resources.account_profile_last_name
import com.photonne.app.resources.account_profile_save
import com.photonne.app.resources.account_profile_saved
import com.photonne.app.resources.account_profile_summary_account_created
import com.photonne.app.resources.account_profile_summary_last_login
import com.photonne.app.resources.account_profile_username
import com.photonne.app.resources.admin_user_role_admin
import com.photonne.app.resources.admin_user_role_user
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountProfileScreen(viewModel: AccountProfileViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.baseline?.let { user ->
            ProfileSummaryCard(user)
        }

        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::onFirstNameChange,
            label = { Text(stringResource(Res.string.account_profile_first_name)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            ),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.lastName,
            onValueChange = viewModel::onLastNameChange,
            label = { Text(stringResource(Res.string.account_profile_last_name)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            ),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(Res.string.account_profile_email)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(Res.string.account_profile_username)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        state.errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        if (state.successMessage != null) {
            Text(
                stringResource(Res.string.account_profile_saved),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.actionButtonHeight()
            ) {
                Text(stringResource(Res.string.account_profile_save))
            }
        }
    }
}

/**
 * Header card that mirrors the PWA's profile summary: large circular
 * avatar with the user's initial, full name, role chip, and a divider
 * followed by the account-created / last-access timestamps.
 */
@Composable
private fun ProfileSummaryCard(user: UserDto) {
    val fullName = listOfNotNull(
        user.firstName?.takeIf { it.isNotBlank() },
        user.lastName?.takeIf { it.isNotBlank() }
    ).joinToString(" ").ifBlank { user.username }
    val initial = (user.firstName?.takeIf { it.isNotBlank() } ?: user.username)
        .trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val isAdmin = user.role.equals("Admin", ignoreCase = true)
    val roleLabel = if (isAdmin) {
        stringResource(Res.string.admin_user_role_admin)
    } else {
        stringResource(Res.string.admin_user_role_user)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(roleLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            parseProfileInstant(user.createdAt)?.let { instant ->
                ProfileMetaRow(
                    icon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                    caption = stringResource(Res.string.account_profile_summary_account_created),
                    value = formatProfileDate(instant)
                )
            }
            parseProfileInstant(user.lastLoginAt)?.let { instant ->
                ProfileMetaRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                    caption = stringResource(Res.string.account_profile_summary_last_login),
                    value = formatProfileDateTime(instant)
                )
            }
        }
    }
}

@Composable
private fun ProfileMetaRow(
    icon: @Composable () -> Unit,
    caption: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Parses an ISO-8601 timestamp from the API, tolerating the same
 * variants as `FlexibleInstantSerializer` (offset-less values produced
 * by PostgreSQL `timestamp without time zone`).
 */
private fun parseProfileInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    val normalized = when {
        trimmed.endsWith("Z", ignoreCase = true) -> trimmed
        HAS_OFFSET_REGEX.containsMatchIn(trimmed) -> trimmed
        else -> "${trimmed}Z"
    }
    return runCatching { Instant.parse(normalized) }.getOrNull()
}

private val HAS_OFFSET_REGEX = Regex("[+-]\\d{2}:?\\d{2}$")
