package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/** Body of GET /api/organize/inbox/count. */
@Serializable
data class OrganizeCountResponse(val count: Int = 0)
