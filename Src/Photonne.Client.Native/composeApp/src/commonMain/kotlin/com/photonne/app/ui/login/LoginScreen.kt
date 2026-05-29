package com.photonne.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.app_name
import com.photonne.app.ui.theme.ButtonLoadingIndicator
import com.photonne.app.ui.theme.actionButtonHeight
import com.photonne.app.ui.theme.photonneLogoPainter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen() {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = photonneLogoPainter(),
                        contentDescription = stringResource(Res.string.app_name),
                        modifier = Modifier.height(56.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))

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
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.localUrl,
        onValueChange = viewModel::onLocalUrlChange,
        label = { Text("URL local (opcional)") },
        placeholder = { Text("http://192.168.1.10:5000") },
        supportingText = {
            Text(
                "Se usará cuando estés en la misma red WiFi que el servidor.",
                style = MaterialTheme.typography.bodySmall
            )
        },
        enabled = !state.isSubmitting,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = {
            if (!state.isSubmitting && state.serverUrl.isNotBlank()) {
                viewModel.submitServerUrl()
            }
        }),
        modifier = Modifier.fillMaxWidth()
    )

    state.error?.userMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = viewModel::submitServerUrl,
        enabled = !state.isSubmitting && state.serverUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth().actionButtonHeight()
    ) {
        if (state.isSubmitting) {
            ButtonLoadingIndicator()
        } else {
            Text("Continuar")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CredentialsStep(state: LoginUiState, viewModel: LoginViewModel) {
    Text(
        "Paso 2 de 2 · Inicia sesión",
        style = MaterialTheme.typography.bodyMedium
    )
    val effectiveUrl = rememberApiBaseUrl().ifEmpty { state.serverUrl }
    Text(
        effectiveUrl,
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
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier
            .fillMaxWidth()
            .autofill(
                autofillTypes = listOf(AutofillType.Username, AutofillType.EmailAddress),
                onFill = viewModel::onUsernameChange
            )
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
        keyboardActions = KeyboardActions(onGo = {
            if (!state.isSubmitting) viewModel.submit()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .autofill(
                autofillTypes = listOf(AutofillType.Password),
                onFill = viewModel::onPasswordChange
            )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isSubmitting) {
                viewModel.onRememberMeChange(!state.rememberMe)
            }
    ) {
        Checkbox(
            checked = state.rememberMe,
            onCheckedChange = { viewModel.onRememberMeChange(it) },
            enabled = !state.isSubmitting
        )
        Text(
            "Recuérdame en este dispositivo",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    state.error?.userMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = viewModel::submit,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth().actionButtonHeight()
    ) {
        if (state.isSubmitting) {
            ButtonLoadingIndicator()
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

/**
 * Registers the field with Android's autofill framework so password managers
 * (1Password, Bitwarden, Google, …) can detect and fill it. No-op on iOS and
 * desktop, where Compose Multiplatform does not yet bridge to native autofill.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit
): Modifier {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val currentOnFill by rememberUpdatedState(onFill)
    val autofillNode = remember(autofillTypes) {
        AutofillNode(
            autofillTypes = autofillTypes,
            onFill = { value -> currentOnFill(value) }
        )
    }
    DisposableEffect(autofillNode) {
        autofillTree += autofillNode
        onDispose { autofillTree.children.remove(autofillNode.id) }
    }
    return this
        .onGloballyPositioned { coordinates ->
            autofillNode.boundingBox = coordinates.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) {
                    requestAutofillForNode(autofillNode)
                } else {
                    cancelAutofillForNode(autofillNode)
                }
            }
        }
}
