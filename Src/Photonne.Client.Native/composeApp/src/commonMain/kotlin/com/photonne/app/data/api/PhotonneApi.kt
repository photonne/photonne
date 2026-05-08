package com.photonne.app.data.api

import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.LoginRequest
import com.photonne.app.data.models.LoginResponse
import com.photonne.app.data.models.TimelinePage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Instant

interface PhotonneApi {
    suspend fun login(username: String, password: String, deviceId: String): LoginResponse
    suspend fun getTimeline(cursor: Instant? = null, pageSize: Int = DEFAULT_TIMELINE_PAGE_SIZE): TimelinePage
    suspend fun getAssetDetail(assetId: String): AssetDetail

    companion object {
        const val DEFAULT_TIMELINE_PAGE_SIZE = 80
    }
}

class PhotonneApiClient(
    private val client: HttpClient,
    private val baseUrl: String
) : PhotonneApi {

    override suspend fun login(username: String, password: String, deviceId: String): LoginResponse {
        val response: HttpResponse = client.post("$baseUrl/api/auth/login") {
            skipAuthRefresh()
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password, deviceId = deviceId))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Login failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getTimeline(cursor: Instant?, pageSize: Int): TimelinePage {
        val response: HttpResponse = client.get("$baseUrl/api/assets/timeline") {
            parameter("pageSize", pageSize)
            if (cursor != null) parameter("cursor", cursor.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Timeline fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }

    override suspend fun getAssetDetail(assetId: String): AssetDetail {
        val response: HttpResponse = client.get("$baseUrl/api/assets/$assetId")
        if (response.status != HttpStatusCode.OK) {
            throw PhotonneApiException(
                status = response.status.value,
                message = "Asset detail fetch failed (${response.status.value})"
            )
        }
        return response.body()
    }
}

class PhotonneApiException(
    val status: Int,
    message: String
) : RuntimeException(message)
