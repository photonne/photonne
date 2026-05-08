package com.photonne.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.models.UserDto
import org.koin.compose.koinInject

@Composable
fun HomeScreen(user: UserDto) {
    val authRepository: AuthRepository = koinInject()
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Hola, ${user.firstName ?: user.username}", style = MaterialTheme.typography.headlineMedium)
                Text("Photonne Native — scaffold v0", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { authRepository.logout() }) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}
