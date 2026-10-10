package com.photonne.app.data.api

/**
 * Recuperación proactiva de conexión al volver la app a primer plano.
 *
 * Hasta ahora toda la recuperación era reactiva: el pool solo se purgaba en un
 * cambio de red y el re-sondeo LAN/público solo tras un request ya fallido. Al
 * despertar el móvil sin cambio de red, el primer request del usuario reutiliza
 * un socket medio-abierto y falla → sale el banner "Reintentar" (que funciona
 * justamente porque ese fallo ya dispara la recuperación).
 *
 * Este coordinador ejecuta esa misma recuperación *antes* de que el usuario
 * toque nada, disparado por el evento de primer plano en `App()`:
 *  1. [ConnectionRecycler.recycle] tira los sockets muertos (Android).
 *  2. [LocalReachabilityProbe.runProbe] dial fresco al HEAD de login: calienta
 *     una conexión nueva y re-decide LAN↔público por si cambiamos de red
 *     mientras estábamos fuera (no-op seguro si no hay URL local configurada).
 */
class ForegroundRecovery(
    private val recycler: ConnectionRecycler,
    private val probe: LocalReachabilityProbe,
) {
    suspend fun onEnterForeground() {
        recycler.recycle()
        probe.runProbe()
    }
}
