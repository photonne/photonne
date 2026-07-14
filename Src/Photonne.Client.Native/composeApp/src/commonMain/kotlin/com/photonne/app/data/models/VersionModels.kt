package com.photonne.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Respuesta de `GET /api/version` (público, sin auth). */
@Serializable
data class PublicVersionResponse(
    @SerialName("version") val version: String
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
