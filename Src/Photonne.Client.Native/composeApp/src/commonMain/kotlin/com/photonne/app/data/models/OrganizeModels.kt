package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/** Body of GET /api/organize/inbox/count. */
@Serializable
data class OrganizeCountResponse(val count: Int = 0)

/** One capture-year bucket, shared by the move preview ("se repartirán en…") and
 *  the post-move summary ("repartidas en…"). */
@Serializable
data class YearCount(val year: Int = 0, val count: Int = 0)

/** Result of a move (POST /api/organize/rule/move and /api/folders/assets/move):
 *  how many assets were filed out, plus the real per-year split (empty unless the
 *  move organized by year). Both endpoints share this shape. */
@Serializable
data class MoveOutcome(
    val moved: Int = 0,
    val yearBreakdown: List<YearCount> = emptyList(),
)

/** Body of POST /api/assets/year-breakdown — the year split of a set of assets,
 *  for the manual-move preview. */
@Serializable
data class AssetYearBreakdownResponse(val yearBreakdown: List<YearCount> = emptyList())
