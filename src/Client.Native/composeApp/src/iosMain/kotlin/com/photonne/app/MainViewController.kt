@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.photonne.app

import androidx.compose.ui.window.ComposeUIViewController
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import org.koin.core.context.startKoin
import kotlin.native.Platform

private var koinStarted = false

/**
 * Starts Koin once per process. Called from `AppDelegate` at launch, because
 * iOS can launch the app only to run the backup BGTask, with no scene and so
 * no [MainViewController]; the task's bridge resolves everything from Koin.
 * [MainViewController] calls it too, which is then a no-op.
 */
fun startPhotonneKoin(apiBaseUrl: String?, useFakeMemories: Boolean) {
    if (koinStarted) return
    startKoin {
        modules(
            commonModule(
                PhotonneAppConfig(
                    apiBaseUrl = apiBaseUrl?.takeIf { it.isNotBlank() },
                    useFakeMemories = useFakeMemories,
                    httpLogging = Platform.isDebugBinary,
                )
            ),
            platformModule()
        )
    }
    koinStarted = true
}

fun MainViewController(
    apiBaseUrl: String?,
    useFakeMemories: Boolean = false
) = ComposeUIViewController {
    startPhotonneKoin(apiBaseUrl, useFakeMemories)
    App()
}
