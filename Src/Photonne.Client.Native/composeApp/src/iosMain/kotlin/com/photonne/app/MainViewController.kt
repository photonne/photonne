package com.photonne.app

import androidx.compose.ui.window.ComposeUIViewController
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import org.koin.core.context.startKoin

private var koinStarted = false

fun MainViewController(apiBaseUrl: String) = ComposeUIViewController {
    if (!koinStarted) {
        startKoin {
            modules(
                commonModule(PhotonneAppConfig(apiBaseUrl = apiBaseUrl)),
                platformModule()
            )
        }
        koinStarted = true
    }
    App()
}
