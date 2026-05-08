package com.photonne.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import org.koin.core.context.startKoin

fun main() {
    val apiBaseUrl = System.getProperty("photonne.api.baseUrl")
        ?: System.getenv("PHOTONNE_API_BASE_URL")
        ?: "http://localhost:1107"

    startKoin {
        modules(
            commonModule(PhotonneAppConfig(apiBaseUrl = apiBaseUrl)),
            platformModule()
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(),
            title = "Photonne"
        ) {
            App()
        }
    }
}
