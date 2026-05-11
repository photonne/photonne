package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_security_changed
import com.photonne.app.resources.account_security_confirm
import com.photonne.app.resources.account_security_current
import com.photonne.app.resources.account_security_min_length
import com.photonne.app.resources.account_security_mismatch
import com.photonne.app.resources.account_security_new
import com.photonne.app.resources.account_security_submit
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountSecurityScreen(viewModel: AccountSecurityViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.currentPassword,
            onValueChange = viewModel::onCurrentChange,
            label = { Text(stringResource(Res.string.account_security_current)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        state.errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        if (state.successMessage != null) {
            Text(
                stringResource(Res.string.account_security_changed),
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
                onClick = viewModel::submit,
                enabled = state.canSave
            ) {
                Text(stringResource(Res.string.account_security_submit))
            }
        }
    }
}
