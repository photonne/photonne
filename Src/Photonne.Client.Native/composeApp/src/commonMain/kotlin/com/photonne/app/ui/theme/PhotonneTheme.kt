package com.photonne.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.photonne.app.data.settings.ThemePreference

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
    preference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
