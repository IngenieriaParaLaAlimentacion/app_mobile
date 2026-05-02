package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionSource
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

object HttpClientFactory {

    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** No `Auth` plugin — used by `AuthApi` for /auth/login and /auth/refresh. */
    fun createRefreshClient(engine: HttpClientEngine, json: Json): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(Logging) {
                logger = object : Logger { override fun log(message: String) = println(message) }
                level = LogLevel.NONE
            }
            defaultRequest { url(ApiConfig.BASE_URL) }
        }

    /** Main client with Bearer auth + automatic refresh on 401. */
    fun createAuthedClient(
        engine: HttpClientEngine,
        json: Json,
        sessionManager: SessionManager,
        authApi: AuthApi,
        statusSink: MutableStateFlow<SessionStatus>,
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger { override fun log(message: String) = println(message) }
            level = LogLevel.NONE
        }
        defaultRequest { url(ApiConfig.BASE_URL) }
        install(Auth) {
            bearer {
                loadTokens {
                    sessionManager.loadSessionOrNull()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val current = sessionManager.loadSessionOrNull() ?: run {
                        statusSink.value = SessionStatus.NotAuthenticated(isSignOut = false)
                        return@refreshTokens null
                    }
                    runCatching { authApi.refresh(current.refreshToken) }.fold(
                        onSuccess = { fresh: UserSession ->
                            sessionManager.saveSession(fresh)
                            statusSink.value = SessionStatus.Authenticated(fresh, SessionSource.Refresh(current))
                            BearerTokens(fresh.accessToken, fresh.refreshToken)
                        },
                        onFailure = { cause ->
                            sessionManager.deleteSession()
                            statusSink.value = SessionStatus.RefreshFailure(cause.message ?: "refresh failed")
                            statusSink.value = SessionStatus.NotAuthenticated(isSignOut = false)
                            null
                        },
                    )
                }
                sendWithoutRequest { true }
            }
        }
    }
}
