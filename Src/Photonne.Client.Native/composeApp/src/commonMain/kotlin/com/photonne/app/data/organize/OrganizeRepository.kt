package com.photonne.app.data.organize

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.data.models.SmartAlbumPreview
import com.photonne.app.data.models.SmartRule
import com.photonne.app.data.models.TimelinePage
import com.photonne.app.data.models.YearCount
import kotlinx.datetime.Instant

/**
 * "Para organizar" inbox: the assets still sitting under MobileBackup (dropped
 * by automatic backup, not yet filed into a folder). Moving an asset out of
 * MobileBackup is what marks it organized, so the inbox drains to zero.
 */
class OrganizeRepository(
    private val api: PhotonneApi
) {
    suspend fun inbox(cursor: Instant? = null): TimelinePage =
        api.getOrganizeInbox(cursor)

    suspend fun count(): Int =
        api.getOrganizeCount()

    /** Physical move out of MobileBackup into [targetFolderId]. Source folders
     *  differ per asset (one per device), so we pass a null source and let the
     *  server authorize per asset. When [organizeByYear] is set the server files
     *  each asset into a Year subfolder (e.g. 2026) under the destination. */
    suspend fun moveAssets(
        targetFolderId: String,
        assetIds: List<String>,
        organizeByYear: Boolean = false
    ): MoveOutcome =
        api.moveFolderAssets(
            sourceFolderId = null,
            targetFolderId = targetFolderId,
            assetIds = assetIds,
            organizeByCaptureYear = organizeByYear
        )

    /** Year split of [assetIds] (server-computed from CapturedAt), for the manual
     *  move preview under the picker. */
    suspend fun yearBreakdown(assetIds: List<String>): List<YearCount> =
        api.assetYearBreakdown(assetIds)

    /** Dry-run of a condition [rule] within the inbox: match count + a sample of
     *  ids, for the live "N fotos coinciden" preview. */
    suspend fun previewRule(rule: SmartRule, sampleSize: Int = 24): SmartAlbumPreview =
        api.previewOrganizeRule(rule, sampleSize)

    /** Files every inbox asset matching [rule] into [targetFolderId] in one shot
     *  (resolved server-side); returns the moved count + real per-year split. When
     *  [organizeByYear] is set the server files each match into a Year subfolder. */
    suspend fun moveByRule(rule: SmartRule, targetFolderId: String, organizeByYear: Boolean = false): MoveOutcome =
        api.moveOrganizeRule(rule, targetFolderId, organizeByCaptureYear = organizeByYear)
}
