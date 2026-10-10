@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.photonne.app

import androidx.compose.ui.window.ComposeUIViewController
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import org.koin.core.context.startKoin
import kotlin.native.Platform

private var koinStarted = false

fun MainViewController(
    apiBaseUrl: String?,
    useFakeMemories: Boolean = false
) = ComposeUIViewController {
    if (!koinStarted) {
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
    App()
}
