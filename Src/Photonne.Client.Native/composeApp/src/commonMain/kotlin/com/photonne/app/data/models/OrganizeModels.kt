package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * Body of GET /api/organize/inbox/count.
 *
 * Resumen de la bandeja. Además del contador trae el tramo de fechas que cubre:
 * "1.240 sin organizar" se lee igual si es el viaje de la semana pasada que si
 * son cuatro años de atraso, y esas dos cosas piden decisiones distintas.
 *
 * Las fechas llegan como hora local de pared (sin zona), igual que CapturedAt.
 */
@Serializable
data class OrganizeSummary(
    val count: Int = 0,
    val oldest: String? = null,
    val newest: String? = null,
)

/** One capture-year bucket, shared by the move preview ("se repartirán en…") and
 *  the post-move summary ("repartidas en…"). */
@Serializable
data class YearCount(val year: Int = 0, val count: Int = 0)

/** A capture-year bucket with the asset ids that fall in it (newest year first;
 *  ids within a year by capture date desc). Powers the "Revisar" thumbnail grid. */
@Serializable
data class YearGroup(val year: Int = 0, val assetIds: List<String> = emptyList()) {
    val count: Int get() = assetIds.size
}

/** Result of a move (POST /api/organize/rule/move and /api/folders/assets/move):
 *  how many assets were filed out, plus the real per-year split (empty unless the
 *  move organized by year). Both endpoints share this shape. */
@Serializable
data class MoveOutcome(
    val moved: Int = 0,
    val yearBreakdown: List<YearCount> = emptyList(),
)

/** Body of POST /api/assets/year-breakdown — the given assets grouped by capture
 *  year (with ids), for the manual-move chips and "Revisar" grid. */
@Serializable
data class AssetYearBreakdownResponse(val groups: List<YearGroup> = emptyList())

/** Body of POST /api/organize/rule/review — every matching inbox asset grouped by
 *  capture year (with ids), for the condition-move "Revisar" grid. */
@Serializable
data class OrganizeRuleReviewResponse(val groups: List<YearGroup> = emptyList())

/**
 * Aparta assets de la bandeja sin moverlos ni archivarlos: cosas que nunca hay
 * que guardar en ninguna carpeta (capturas, memes, recibos). Es lo que permite
 * que la bandeja llegue de verdad a cero.
 */
@Serializable
data class OrganizeExcludeRequest(
    val assetIds: List<String> = emptyList(),
    val excluded: Boolean = true,
)

/**
 * Un lote propuesto por el servidor a partir de lo que sigue sin organizar.
 *
 * La bandeja dejaba al usuario inventar los grupos a mano sobre un muro
 * cronológico, teniendo la app las señales para agruparlos. Cada lote es una
 * decisión ya formada — un viaje, una persona, una escena, un mes — con sus
 * ids dentro, así que se revisa y se mueve con lo que ya existía.
 */
@Serializable
data class OrganizeSuggestion(
    /** trip | person | scene | month. Elige el icono, no el comportamiento. */
    val kind: String = "",
    val key: String = "",
    val title: String = "",
    /** Extremos del tramo, ya como "yyyy-MM". */
    val from: String? = null,
    val to: String? = null,
    val count: Int = 0,
    val coverAssetId: String? = null,
    val assetIds: List<String> = emptyList(),
)
