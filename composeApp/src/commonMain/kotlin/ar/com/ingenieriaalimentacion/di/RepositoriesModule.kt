package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.AuthRepositoryImpl
import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoriesModule = module {
    single<Json>(named("auth")) {
        Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }
    single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }
    single<AuthRepository> {
        AuthRepositoryImpl(
            api = get(),
            sessionManager = get(),
            httpClient = get(),
            statusSink = get(),
        )
    }
}
