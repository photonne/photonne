package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/** Body of GET /api/organize/inbox/count. */
@Serializable
data class OrganizeCountResponse(val count: Int = 0)

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
