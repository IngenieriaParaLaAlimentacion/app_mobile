package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.auth.AuthRepositoryImpl
import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.data.establishment.EstablishmentRepositoryImpl
import ar.com.ingenieriaalimentacion.data.profile.ProfileRepositoryImpl
import ar.com.ingenieriaalimentacion.data.registration.RegistrationRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoriesModule = module {
    single<Json>(named("auth")) {
        Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }
    single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }
    single { DeviceIdProvider(store = get()) }
    single<AuthRepository> {
        AuthRepositoryImpl(
            api = get(),
            sessionManager = get(),
            httpClient = get(),
            statusSink = get(),
            deviceIdProvider = get(),
        )
    }
    single<EstablishmentRepository> { EstablishmentRepositoryImpl(api = get()) }
    single<RegistrationRepository> { RegistrationRepositoryImpl(api = get()) }
    single<SubjectRepository> { SubjectRepositoryImpl(api = get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(api = get()) }
}
