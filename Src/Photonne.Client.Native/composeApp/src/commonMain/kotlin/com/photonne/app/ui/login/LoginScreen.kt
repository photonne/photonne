package com.photonne.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen() {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Photonne", style = MaterialTheme.typography.headlineMedium)

                when (state.step) {
                    LoginStep.ServerUrl -> ServerUrlStep(state, viewModel)
                    LoginStep.Credentials -> CredentialsStep(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ServerUrlStep(state: LoginUiState, viewModel: LoginViewModel) {
    Text(
        "Paso 1 de 2 · Configura el servidor",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "Introduce la dirección de tu instancia de Photonne.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = { Text("URL del servidor") },
        placeholder = { Text("https://photos.example.com") },
        enabled = !state.isSubmitting,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go
        ),
        modifier = Modifier.fillMaxWidth()
    )

    state.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = viewModel::submitServerUrl,
        enabled = !state.isSubmitting && state.serverUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        } else {
            Text("Continuar")
        }
    }
}

@Composable
private fun CredentialsStep(state: LoginUiState, viewModel: LoginViewModel) {
    Text(
        "Paso 2 de 2 · Inicia sesión",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        state.serverUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text("Usuario o email") },
        enabled = !state.isSubmitting,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Contraseña") },
        visualTransformation = PasswordVisualTransformation(),
        enabled = !state.isSubmitting,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go
        ),
        modifier = Modifier.fillMaxWidth()
    )

    state.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = viewModel::submit,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        } else {
            Text("Entrar")
        }
    }

    TextButton(
        onClick = viewModel::changeServer,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Cambiar servidor")
    }
}
