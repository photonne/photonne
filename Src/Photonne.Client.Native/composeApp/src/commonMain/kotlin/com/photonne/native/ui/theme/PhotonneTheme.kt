package com.photonne.native.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhotonneBlue = Color(0xFF1565C0)
private val PhotonneBlueDark = Color(0xFF0D47A1)
private val PhotonneAccent = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = PhotonneBlue,
    secondary = PhotonneAccent,
    tertiary = PhotonneBlueDark
)

private val DarkColors = darkColorScheme(
    primary = PhotonneBlue,
    secondary = PhotonneAccent,
    tertiary = PhotonneBlueDark
)

@Composable
fun PhotonneTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
