package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.dto.LoginRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RefreshRequest
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Auth endpoints that must NOT carry an `Authorization` header.
 * Constructed against the no-auth refresh `HttpClient` (Koin qualifier `"refresh"`).
 */
class AuthApi(private val refreshClient: HttpClient) {

    suspend fun signIn(email: String, password: String): UserSession =
        refreshClient.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()

    suspend fun refresh(refreshToken: String): UserSession =
        refreshClient.post("auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()
}
