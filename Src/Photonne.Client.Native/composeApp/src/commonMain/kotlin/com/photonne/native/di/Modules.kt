package com.photonne.native.di

import com.photonne.native.data.api.PhotonneApi
import com.photonne.native.data.api.PhotonneApiClient
import com.photonne.native.data.api.buildPhotonneHttpClient
import com.photonne.native.data.auth.AuthRepository
import com.photonne.native.data.auth.AuthStateHolder
import com.photonne.native.data.auth.SettingsTokenStorage
import com.photonne.native.data.auth.TokenStorage
import com.photonne.native.ui.login.LoginViewModel
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
    viewModelOf(::LoginViewModel)
}
