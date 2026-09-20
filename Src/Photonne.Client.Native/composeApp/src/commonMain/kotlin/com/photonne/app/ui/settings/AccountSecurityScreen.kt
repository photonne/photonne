package com.photonne.app.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.photonne.app.ui.main.ResultSnackbar
import com.photonne.app.ui.theme.PrimaryActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_security_changed
import com.photonne.app.resources.account_security_confirm
import com.photonne.app.resources.account_security_current
import com.photonne.app.resources.account_security_min_length
import com.photonne.app.resources.account_security_mismatch
import com.photonne.app.resources.account_security_new
import com.photonne.app.resources.account_security_submit
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.contentWidth
import com.photonne.app.ui.theme.Spacing

@Composable
fun AccountSecurityScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AccountSecurityViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val state by viewModel.state.collectAsState()
    // The result goes where every other one in the app goes: the snackbar.
    ResultSnackbar(
        message = stringResource(Res.string.account_security_changed).takeIf { state.successMessage != null },
        onShown = viewModel::consumeSuccess
    )

    Box(modifier = Modifier.fillMaxSize()) {
    // Un único toggle para los tres campos: si enseñas una, quieres verlas.
    var passwordsVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .hazeSource(hazeState)
            .contentWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp + reservedTop, bottom = 16.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
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
private fun VisibilityToggle(visible: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.IconButton(onClick = { onChange(!visible) }) {
        androidx.compose.material3.Icon(
            if (visible) androidx.compose.material.icons.Icons.Outlined.VisibilityOff
            else androidx.compose.material.icons.Icons.Outlined.Visibility,
            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
        )
    }
}
