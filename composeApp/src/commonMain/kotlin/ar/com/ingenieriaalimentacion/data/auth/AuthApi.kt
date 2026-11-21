package ar.com.ingenieriaalimentacion.data.auth

import ar.com.ingenieriaalimentacion.data.auth.dto.LoginRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.LogoutRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RefreshRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.RequestPasswordRecoveryRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.ResetPasswordRequest
import ar.com.ingenieriaalimentacion.data.auth.dto.VerifyEmailRequest
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Auth endpoints that must NOT carry an `Authorization` header.
 * Constructed against the no-auth refresh `HttpClient` (Koin qualifier `"refresh"`).
 */
class AuthApi(private val refreshClient: HttpClient) {

    suspend fun signIn(email: String, password: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            expectSuccess = false
            setBody(LoginRequest(email, password, deviceId))
        }.authSession(isLogin = true)

    suspend fun refresh(refreshToken: String, deviceId: String): UserSession =
        refreshClient.post("api/v1/auth/refresh") {
            timeout { requestTimeoutMillis = 15_000 }
            contentType(ContentType.Application.Json)
            expectSuccess = false
            setBody(RefreshRequest(refreshToken, deviceId, reportUserBlocked = true))
        }.authSession(isLogin = false)

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

    suspend fun logout(refreshToken: String, deviceId: String) {
        refreshClient.post("api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(deviceId = deviceId, refreshToken = refreshToken))
        }
    }

    /**
     * `POST /api/v1/auth/verify-email/confirm` — unauthenticated, `204` on success.
     *
     * The three token failures are
     * all `401` and differ only in the problem `type`, so the status alone is not enough. The
     * refresh client sets `expectSuccess = true`, hence the per-request opt-out; from there it
     * is the same status-then-type branching as `FoodIntakeApi`.
     */
    suspend fun verifyEmail(token: String): VerifyEmailOutcome = runCatching {
        val response = refreshClient.post("api/v1/auth/verify-email/confirm") {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(VerifyEmailRequest(token = token))
        }
        when (response.status.value) {
            in 200..299 -> VerifyEmailOutcome.Verified
            401 -> when (response.problemTypeOrNull()) {
                VERIFICATION_TOKEN_USED_TYPE -> VerifyEmailOutcome.TokenUsed
                VERIFICATION_TOKEN_EXPIRED_TYPE -> VerifyEmailOutcome.TokenExpired
                // `/errors/auth/invalid-verification-token`, or a 401 this build does not
                // recognize: either way this link cannot confirm the email.
                else -> VerifyEmailOutcome.InvalidToken
            }
            400, 422 -> VerifyEmailOutcome.InvalidToken // malformed body, e.g. an empty token
            else -> VerifyEmailOutcome.Retryable(null) // 5xx and anything unexpected
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        VerifyEmailOutcome.Retryable(t) // transport failures
    }

    private suspend fun HttpResponse.authSession(isLogin: Boolean): UserSession = when {
        status.value in 200..299 -> body()
        status == HttpStatusCode.Forbidden -> when (problemTypeOrNull()) {
            USER_BLOCKED_TYPE -> throw AuthFailure(AuthError.AccountBlocked)
            PENDING_VALIDATION_TYPE -> throw AuthFailure(
                if (isLogin) AuthError.AccountPendingValidation else AuthError.Network,
            )
            else -> throw AuthFailure(AuthError.Network)
        }
        status == HttpStatusCode.Unauthorized && isLogin -> throw AuthFailure(AuthError.InvalidCredentials)
        else -> throw AuthFailure(AuthError.Network)
    }
}

internal const val USER_BLOCKED_TYPE = "/errors/user-blocked"
private const val PENDING_VALIDATION_TYPE = "/errors/account-pending-validation"
private const val VERIFICATION_TOKEN_USED_TYPE = "/errors/auth/verification-token-used"
private const val VERIFICATION_TOKEN_EXPIRED_TYPE = "/errors/auth/verification-token-expired"

@Serializable
private data class AuthProblem(val type: String? = null)

private val problemJson = Json { ignoreUnknownKeys = true }

internal suspend fun HttpResponse.problemTypeOrNull(): String? {
    val text = bodyAsText()
    return try {
        problemJson.decodeFromString<AuthProblem>(text).type
    } catch (_: SerializationException) {
        null
    }
}

sealed interface VerifyEmailOutcome {
    data object Verified : VerifyEmailOutcome
    data object TokenUsed : VerifyEmailOutcome
    data object TokenExpired : VerifyEmailOutcome
    data object InvalidToken : VerifyEmailOutcome
    data class Retryable(val cause: Throwable?) : VerifyEmailOutcome
}
