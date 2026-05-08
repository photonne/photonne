package com.photonne.app.di

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.SettingsTokenStorage
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.timeline.TimelineRepository
import com.photonne.app.ui.login.LoginViewModel
import com.photonne.app.ui.timeline.TimelineViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

data class PhotonneAppConfig(val apiBaseUrl: String)

expect fun platformModule(): org.koin.core.module.Module

fun commonModule(config: PhotonneAppConfig) = module {
    single { config }
    singleOf(::AuthStateHolder)
    single<TokenStorage> { SettingsTokenStorage(get()) }
    single<HttpClient> {
        buildPhotonneHttpClient(
            engine = get<HttpClientEngine>(),
            baseUrl = config.apiBaseUrl,
            tokenStorage = get(),
            authState = get()
        )
    }
    single<PhotonneApi> { PhotonneApiClient(get(), config.apiBaseUrl) }
    singleOf(::AuthRepository)
    singleOf(::TimelineRepository)
    viewModelOf(::LoginViewModel)
    viewModelOf(::TimelineViewModel)
}
