package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.dto.LoginRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RefreshRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RequestPasswordRecoveryRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.ResetPasswordRequest
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

    suspend fun signIn(email: String, password: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password, deviceId))
        }.body()

    suspend fun refresh(refreshToken: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken, deviceId))
        }.body()

    suspend fun resetPassword(token: String, newPassword: String) {
        refreshClient.post("api/v1/auth/password-recovery/confirm") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = token, password = newPassword))
        }
    }

    suspend fun requestPasswordRecovery(email: String) {
        refreshClient.post("api/v1/auth/password-recovery/request") {
            contentType(ContentType.Application.Json)
            setBody(RequestPasswordRecoveryRequest(email = email))
        }
    }
}
