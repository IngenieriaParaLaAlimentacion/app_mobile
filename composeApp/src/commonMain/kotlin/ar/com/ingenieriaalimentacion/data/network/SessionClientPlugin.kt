package ar.com.ingenieriaalimentacion.data.network

import ar.com.ingenieriaalimentacion.data.auth.SessionEnded
import ar.com.ingenieriaalimentacion.data.auth.SessionExit
import ar.com.ingenieriaalimentacion.data.auth.SessionLease
import ar.com.ingenieriaalimentacion.data.auth.SessionLifecycle
import ar.com.ingenieriaalimentacion.data.auth.USER_BLOCKED_TYPE
import ar.com.ingenieriaalimentacion.data.auth.problemTypeOrNull
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.SetupRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.AttributeKey

internal val RequestSessionLease = AttributeKey<SessionLease>("RequestSessionLease")

internal class SessionClientConfig {
    lateinit var lifecycle: SessionLifecycle
}

internal val SessionClientPlugin = createClientPlugin("SessionClient", ::SessionClientConfig) {
    val sessions = pluginConfig.lifecycle
    val apiOrigin = Url(ApiConfig.BASE_URL)
    fun Url.isApiOrigin(): Boolean =
        protocol == apiOrigin.protocol && host == apiOrigin.host && port == apiOrigin.port

    on(SetupRequest) { request ->
        request.attributes.put(RequestSessionLease, sessions.capture())
    }
    on(SendingRequest) { request, _ ->
        val lease = request.attributes[RequestSessionLease]
        val current = sessions.sessionFor(lease) ?: throw SessionEnded()
        if (request.headers[HttpHeaders.Authorization] != null) {
            if (!request.url.build().isApiOrigin()) throw SessionEnded()
            request.headers[HttpHeaders.Authorization] = "Bearer ${current.session.accessToken}"
        }
    }
    onResponse { response ->
        val request = response.call.request
        val lease = request.attributes[RequestSessionLease]
        if (sessions.sessionFor(lease) == null) throw SessionEnded()
        if (
            response.status == HttpStatusCode.Forbidden &&
            request.url.isApiOrigin() &&
            request.headers[HttpHeaders.Authorization] != null &&
            response.problemTypeOrNull() == USER_BLOCKED_TYPE
        ) {
            sessions.end(lease, SessionExit.Blocked)
            throw SessionEnded()
        }
    }
}
