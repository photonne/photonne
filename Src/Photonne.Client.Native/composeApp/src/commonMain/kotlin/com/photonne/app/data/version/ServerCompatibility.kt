package com.photonne.app.data.version

/**
 * Si esta app y el servidor pueden entenderse. Se actualizan por separado
 * (el servidor en cada push, las apps cuando el usuario instala), así que cada
 * lado declara el mínimo que necesita del otro en Directory.Build.props: el
 * servidor publica `minClientVersion` en `GET /api/version` y la app lleva
 * [com.photonne.app.PhotonneMinServerVersion] compilado.
 */
sealed interface ServerCompatibility {
    data object Compatible : ServerCompatibility

    /** El servidor es más antiguo de lo que esta app necesita: hay que actualizar el servidor. */
    data class ServerTooOld(val serverVersion: String, val minServerVersion: String) : ServerCompatibility

    /** Esta app es más antigua de lo que el servidor atiende: hay que actualizar la app. */
    data class ClientTooOld(val clientVersion: String, val minClientVersion: String) : ServerCompatibility
}

/**
 * Sin versión del servidor (aún no se ha consultado, o no se pudo) se da por
 * compatible: el aviso solo sale cuando se sabe que no lo es. Si fallan los dos
 * lados manda la app, que es lo que el usuario puede actualizar él mismo.
 */
fun serverCompatibility(
    serverVersion: String?,
    minClientVersion: String?,
    clientVersion: String,
    minServerVersion: String,
): ServerCompatibility = when {
    serverVersion == null -> ServerCompatibility.Compatible
    isNewerVersion(minClientVersion, clientVersion) ->
        ServerCompatibility.ClientTooOld(clientVersion, minClientVersion!!)
    isNewerVersion(minServerVersion, serverVersion) ->
        ServerCompatibility.ServerTooOld(serverVersion, minServerVersion)
    else -> ServerCompatibility.Compatible
}
