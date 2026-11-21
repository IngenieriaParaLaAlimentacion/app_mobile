package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.data.auth.AuthFailure
import ar.com.ingenieriaalimentacion.data.auth.SessionEnded
import ar.com.ingenieriaalimentacion.data.auth.SessionExit
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.domain.model.auth.AuthError
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
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
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
            install(HttpTimeout)
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
        lifecycle: SessionLifecycle,
        authApi: AuthApi,
        getDeviceId: suspend () -> String,
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
        install(SessionClientPlugin) { this.lifecycle = lifecycle }
        install(Auth) {
            bearer {
                cacheTokens = false
                nonCancellableRefresh = true
                loadTokens {
                    lifecycle.active()?.session?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val lease = response.call.request.attributes[RequestSessionLease]
                    val current = lifecycle.sessionFor(lease)?.session ?: throw SessionEnded()
                    val sent = response.call.request.headers[HttpHeaders.Authorization]
                        ?: return@refreshTokens null
                    if (sent != "Bearer ${current.accessToken}") {
                        return@refreshTokens BearerTokens(current.accessToken, current.refreshToken)
                    }
                    val fresh = try {
                        authApi.refresh(current.refreshToken, getDeviceId())
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        if ((failure as? AuthFailure)?.error == AuthError.AccountBlocked) {
                            lifecycle.end(lease, SessionExit.Blocked)
                            throw SessionEnded()
                        }
                        if (lifecycle.failRefreshIfCurrent(lease, current.refreshToken)) return@refreshTokens null
                        val rotated = lifecycle.sessionFor(lease)?.session ?: throw SessionEnded()
                        return@refreshTokens BearerTokens(rotated.accessToken, rotated.refreshToken)
                    }
                    val rotated = lifecycle.completeRefresh(lease, current.refreshToken, fresh) ?: throw SessionEnded()
                    BearerTokens(rotated.accessToken, rotated.refreshToken)
                }
                sendWithoutRequest { true }
            }
        }
    }
}
