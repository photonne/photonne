package com.photonne.app.data.api

import com.photonne.app.data.models.LoginRequest
import com.photonne.app.data.models.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

interface PhotonneApi {
    suspend fun login(username: String, password: String, deviceId: String): LoginResponse
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
}

class PhotonneApiException(
    val status: Int,
    message: String
) : RuntimeException(message)
