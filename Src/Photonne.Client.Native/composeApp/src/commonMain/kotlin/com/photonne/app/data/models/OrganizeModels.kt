package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/** Body of GET /api/organize/inbox/count. */
@Serializable
data class OrganizeCountResponse(val count: Int = 0)

/** Result of POST /api/organize/rule/move — how many assets were filed out of
 *  MobileBackup into the target folder. */
@Serializable
data class OrganizeRuleMoveResponse(val moved: Int = 0)
