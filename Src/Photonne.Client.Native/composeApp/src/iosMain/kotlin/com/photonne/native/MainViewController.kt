package com.photonne.native

import androidx.compose.ui.window.ComposeUIViewController
import com.photonne.native.di.PhotonneAppConfig
import com.photonne.native.di.commonModule
import com.photonne.native.di.platformModule
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
