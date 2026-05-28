package com.photonne.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Respuesta de `GET /api/version` (público, sin auth). */
@Serializable
data class PublicVersionResponse(
    @SerialName("version") val version: String
)
