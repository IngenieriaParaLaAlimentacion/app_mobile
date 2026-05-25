package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.AuthApi
import ar.com.ingenieriaalimentacion.data.establishment.EstablishmentApi
import ar.com.ingenieriaalimentacion.data.network.HttpClientFactory
import ar.com.ingenieriaalimentacion.data.registration.RegistrationApi
import ar.com.ingenieriaalimentacion.data.subject.SubjectApi
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val clientsModule = module {
    single<Json> { HttpClientFactory.json() }

    // Internal mutable status sink shared by HttpClient (refresh path) and AuthRepository.
    single<MutableStateFlow<SessionStatus>> { MutableStateFlow(SessionStatus.Initializing) }

    // Refresh client (no Auth plugin) — used by AuthApi only.
    single<HttpClient>(named("refresh")) {
        HttpClientFactory.createRefreshClient(get<HttpClientEngine>(), get())
    }

    single { AuthApi(refreshClient = get(named("refresh"))) }
    single { EstablishmentApi(refreshClient = get(named("refresh"))) }
    single { RegistrationApi(refreshClient = get(named("refresh"))) }

    // Main authed client.
    single<HttpClient> {
        HttpClientFactory.createAuthedClient(
            engine = get<HttpClientEngine>(),
            json = get(),
            sessionManager = get<SessionManager>(),
            authApi = get(),
            statusSink = get(),
            deviceIdProvider = get(),
        )
    }

    single { SubjectApi(client = get()) }
}
