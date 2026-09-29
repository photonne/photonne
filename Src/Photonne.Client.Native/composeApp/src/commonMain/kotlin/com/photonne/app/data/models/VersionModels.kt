package com.photonne.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Respuesta de `GET /api/version` (público, sin auth). */
@Serializable
data class PublicVersionResponse(
    @SerialName("version") val version: String,
    /** La app más antigua que el servidor atiende; nulo en servidores anteriores a 1.160. */
    @SerialName("minClientVersion") val minClientVersion: String? = null
)

/**
 * Respuesta de `GET /api/version/latest-release`: la última release de GitHub,
 * que es la que trae instaladores. Nulos si no hay o no se pudo consultar.
 */
@Serializable
data class LatestReleaseResponse(
    val latestVersion: String? = null,
    val releaseUrl: String? = null
)

/**
 * Un dataset de terceros que el servidor redistribuye, de `GET /api/attributions`.
 *
 * Se pregunta al servidor en vez de escribirlo en el cliente porque solo el
 * servidor sabe qué lleva dentro: una imagen cuyo build no pudo descargar
 * GeoNames no debe acreditar datos que no tiene.
 */
@Serializable
data class Attribution(
    val name: String,
    val license: String,
    val licenseUrl: String,
    val sourceUrl: String,
    /** Línea lista para mostrar tal cual; la redacta el servidor. */
    val notice: String,
)
