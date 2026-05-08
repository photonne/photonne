package com.photonne.app.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient

/**
 * Builds a Coil [ImageLoader] that routes network requests through the
 * shared authenticated [HttpClient], so thumbnail downloads carry the
 * Bearer token and benefit from refresh-on-401.
 */
fun buildPhotonneImageLoader(
    context: PlatformContext,
    httpClient: HttpClient
): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
    }
    .crossfade(true)
    .build()
