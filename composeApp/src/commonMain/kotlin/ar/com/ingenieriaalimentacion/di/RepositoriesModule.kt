package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.DefaultActivityLocalStore
import ar.com.ingenieriaalimentacion.data.auth.AuthRepositoryImpl
import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.data.establishment.EstablishmentRepositoryImpl
import ar.com.ingenieriaalimentacion.data.measurement.MeasurementRepositoryImpl
import ar.com.ingenieriaalimentacion.data.measurement.local.DefaultMeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.measurement.local.MeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.profile.ProfileRepositoryImpl
import ar.com.ingenieriaalimentacion.data.profile.local.DefaultEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.local.EstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.registration.RegistrationRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectQueryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.local.DefaultSubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.domain.util.UuidGenerator
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

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
    single<SubjectLocalStore> {
        DefaultSubjectLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<IdGenerator> { UuidGenerator() }
    single<SubjectRepository> {
        SubjectRepositoryImpl(
            api = get(),
            local = get(),
            idGenerator = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<SubjectQueryRepository> { SubjectQueryRepositoryImpl(api = get(), local = get()) }
    single<MeasurementLocalStore> {
        DefaultMeasurementLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<MeasurementRepository> {
        MeasurementRepositoryImpl(
            api = get(),
            local = get(),
            subjects = get(),
            idGenerator = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<EstablishmentLocalStore> {
        DefaultEstablishmentLocalStore(queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<ProfileRepository> { ProfileRepositoryImpl(api = get(), local = get()) }
    single<ActivityLocalStore> { DefaultActivityLocalStore(queries = get()) }
}
