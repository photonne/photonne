package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.ui.home.HomeScreen
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.theme.PhotonneTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    PhotonneTheme {
        val authState: AuthStateHolder = koinInject()
        val state by authState.state.collectAsState()
        when (state) {
            is AuthState.Authenticated -> HomeScreen(user = (state as AuthState.Authenticated).user)
            AuthState.Unauthenticated, AuthState.Unknown -> LoginScreen()
        }
    }
}
