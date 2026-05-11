package com.photonne.app.data.people

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.PeoplePage
import com.photonne.app.data.models.PersonAssetsPage

class PeopleRepository(
    private val api: PhotonneApi
) {
    suspend fun list(
        includeHidden: Boolean = false,
        limit: Int = 80,
        offset: Int = 0,
        search: String? = null
    ): PeoplePage = api.listPeople(
        search = search,
        includeHidden = includeHidden,
        limit = limit,
        offset = offset
    )

    suspend fun rename(personId: String, name: String?) {
        api.renamePerson(personId, name)
    }

    suspend fun hide(personId: String) {
        api.hidePerson(personId)
    }

    suspend fun unhide(personId: String) {
        api.unhidePerson(personId)
    }

    suspend fun assets(
        personId: String,
        limit: Int = 80,
        offset: Int = 0
    ): PersonAssetsPage = api.getPersonAssets(
        personId = personId,
        limit = limit,
        offset = offset
    )
}
