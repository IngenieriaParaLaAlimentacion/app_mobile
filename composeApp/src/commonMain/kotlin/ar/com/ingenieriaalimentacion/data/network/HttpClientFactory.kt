package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
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
import io.ktor.client.plugins.HttpTimeout
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
            // Throw ClientRequestException/ServerResponseException on non-2xx so repositories
            // can map typed errors (e.g. 409 -> UserAlreadyExists). Without this, Ktor returns
            // the error body and `.body()` would fail deserializing it into the success DTO.
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            install(Logging) {
                logger = object : Logger { override fun log(message: String) = println(message) }
                // Method/URL/status on tst, silent on prod. Deliberately INFO and not
                // HEADERS/ALL — those would print the Authorization header into logcat.
                level = if (ApiConfig.IS_PROD) LogLevel.NONE else LogLevel.INFO
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
        deviceIdProvider: DeviceIdProvider,
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout)
        install(Logging) {
            logger = object : Logger { override fun log(message: String) = println(message) }
            // Method/URL/status on tst, silent on prod. Deliberately INFO and not
            // HEADERS/ALL — those would print the Authorization header into logcat.
            level = if (ApiConfig.IS_PROD) LogLevel.NONE else LogLevel.INFO
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
                    val deviceId = deviceIdProvider.getOrCreate()
                    runCatching { authApi.refresh(current.refreshToken, deviceId) }.fold(
                        onSuccess = { fresh: UserSession ->
                            val merged = fresh.copy(user = fresh.user ?: current.user)
                            sessionManager.saveSession(merged)
                            statusSink.value = SessionStatus.Authenticated(merged, SessionSource.Refresh(current))
                            BearerTokens(merged.accessToken, merged.refreshToken)
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
