package com.photonne.app.data.api

/**
 * Cierra los sockets keep-alive ociosos para que el siguiente request marque
 * una conexión nueva. Al volver a primer plano tras un rato en segundo plano,
 * un socket que quedó ocioso mientras el móvil dormía es casi seguro
 * medio-abierto (el server/NAT lo mató sin que saltara ningún evento de red
 * que evictara el pool). Reusarlo cuelga o falla tras el socket timeout — el
 * síntoma del banner "Reintentar" al reabrir la app.
 *
 * Es el mismo `evictAll()` que ya se dispara en un cambio de red, pero
 * expuesto para poder invocarlo también proactivamente desde el hook de
 * primer plano ([ForegroundRecovery]). El `ConnectionPool` es de OkHttp
 * (solo Android), así que commonMain no puede referenciarlo directamente y
 * cada plataforma provee su implementación por Koin.
 */
interface ConnectionRecycler {
    fun recycle()
}
