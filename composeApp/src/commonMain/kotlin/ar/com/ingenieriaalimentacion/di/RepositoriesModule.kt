package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.DefaultActivityLocalStore
import ar.com.ingenieriaalimentacion.data.recipe.RecipeRepositoryImpl
import ar.com.ingenieriaalimentacion.domain.repository.RecipeRepository
import ar.com.ingenieriaalimentacion.data.auth.AuthRepositoryImpl
import ar.com.ingenieriaalimentacion.data.auth.storage.TokenStoreSessionManager
import ar.com.ingenieriaalimentacion.data.device.DeviceIdProvider
import ar.com.ingenieriaalimentacion.data.establishment.EstablishmentRepositoryImpl
import ar.com.ingenieriaalimentacion.data.establishment.characterization.EstablishmentCharacterizationRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.FoodCatalogRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.EstablishmentFoodMovementRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.FoodIntakeRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.ReceiptIntakeRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.FoodOutflowRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.FoodMovementHistoryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.food.FoodStockRepositoryImpl
import ar.com.ingenieriaalimentacion.data.measurement.MeasurementHistoryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.measurement.MeasurementRepositoryImpl
import ar.com.ingenieriaalimentacion.data.measurement.local.DefaultMeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.measurement.local.MeasurementLocalStore
import ar.com.ingenieriaalimentacion.data.notification.PushDeviceRepositoryImpl
import ar.com.ingenieriaalimentacion.data.profile.ProfileRepositoryImpl
import ar.com.ingenieriaalimentacion.data.profile.local.CurrentEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.local.DefaultCurrentEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.local.DefaultEstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.profile.local.EstablishmentLocalStore
import ar.com.ingenieriaalimentacion.data.registration.RegistrationRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectHistoryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectQueryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl
import ar.com.ingenieriaalimentacion.data.subject.local.DefaultSubjectLocalStore
import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.survey.SurveyRepositoryImpl
import ar.com.ingenieriaalimentacion.data.survey.SurveyResponseEditRepositoryImpl
import ar.com.ingenieriaalimentacion.data.survey.SurveyResponseHistoryRepositoryImpl
import ar.com.ingenieriaalimentacion.data.survey.SurveyResponseRepositoryImpl
import ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyResponseEditLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseEditLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentCharacterizationRepository
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentFoodMovementRepository
import ar.com.ingenieriaalimentacion.domain.repository.FoodIntakeRepository
import ar.com.ingenieriaalimentacion.domain.repository.ReceiptIntakeRepository
import ar.com.ingenieriaalimentacion.domain.repository.FoodOutflowRepository
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.FoodStockRepository
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository
import ar.com.ingenieriaalimentacion.domain.repository.PushDeviceRepository
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementRepository
import ar.com.ingenieriaalimentacion.domain.repository.RegistrationRepository
import ar.com.ingenieriaalimentacion.domain.repository.SessionManager
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectQueryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.domain.util.UuidGenerator
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

val repositoriesModule = module {
    single<RecipeRepository> { RecipeRepositoryImpl(get()) }
    single<Json>(named("auth")) {
        Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    }
    single<SessionManager> { TokenStoreSessionManager(tokenStore = get(), json = get(named("auth"))) }
    single { DeviceIdProvider(store = get()) }
    single<AuthRepository> {
        AuthRepositoryImpl(
            api = get(),
            lifecycle = get(),
            deviceIdProvider = get(),
        )
    }
    single<EstablishmentRepository> { EstablishmentRepositoryImpl(api = get()) }
    single<EstablishmentCharacterizationRepository> {
        EstablishmentCharacterizationRepositoryImpl(api = get())
    }
    single<RegistrationRepository> { RegistrationRepositoryImpl(api = get()) }
    single<SubjectLocalStore> {
        DefaultSubjectLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<IdGenerator> { UuidGenerator() }
    single<SubjectRepository> {
        SubjectRepositoryImpl(
            lifecycle = get(),
            api = get(),
            local = get(),
            idGenerator = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<SubjectQueryRepository> { SubjectQueryRepositoryImpl(lifecycle = get(), api = get(), local = get()) }
    single<MeasurementLocalStore> {
        DefaultMeasurementLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<MeasurementRepository> {
        MeasurementRepositoryImpl(
            lifecycle = get(),
            api = get(),
            local = get(),
            subjects = get(),
            idGenerator = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<EstablishmentLocalStore> {
        DefaultEstablishmentLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<CurrentEstablishmentLocalStore> {
        DefaultCurrentEstablishmentLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<ProfileRepository> {
        ProfileRepositoryImpl(lifecycle = get(), api = get(), local = get(), currentEstablishment = get())
    }
    single<ActivityLocalStore> { DefaultActivityLocalStore(lifecycle = get(), queries = get()) }
    single<SubjectHistoryRepository> {
        SubjectHistoryRepositoryImpl(lifecycle = get(), api = get(), cache = get(), queue = get(), json = get(named("auth")))
    }
    single<MeasurementHistoryRepository> {
        MeasurementHistoryRepositoryImpl(lifecycle = get(), api = get(), cache = get(), queue = get(), json = get(named("auth")))
    }
    single<SurveyLocalStore> {
        DefaultSurveyLocalStore(
            lifecycle = get(),
            queries = get(),
            json = get(named("auth")),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<SurveyRepository> { SurveyRepositoryImpl(lifecycle = get(), api = get(), local = get()) }
    single<SurveyResponseLocalStore> {
        DefaultSurveyResponseLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<SurveyResponseRepository> {
        SurveyResponseRepositoryImpl(
            lifecycle = get(),
            api = get(),
            local = get(),
            subjects = get(),
            surveys = get(),
            idGenerator = get(),
            json = get(named("auth")),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<SurveyResponseEditLocalStore> {
        DefaultSurveyResponseEditLocalStore(lifecycle = get(), queries = get(), now = { Clock.System.now().toEpochMilliseconds() })
    }
    single<SurveyResponseEditRepository> {
        SurveyResponseEditRepositoryImpl(
            lifecycle = get(),
            api = get(),
            local = get(),
            responses = get(),
            surveys = get(),
            idGenerator = get(),
            json = get(named("auth")),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single<FoodCatalogRepository> { FoodCatalogRepositoryImpl(api = get()) }
    single<FoodIntakeRepository> { FoodIntakeRepositoryImpl(api = get()) }
    single<ReceiptIntakeRepository> { ReceiptIntakeRepositoryImpl(api = get()) }
    single<FoodOutflowRepository> { FoodOutflowRepositoryImpl(api = get()) }
    single<FoodStockRepository> { FoodStockRepositoryImpl(api = get()) }
    single<EstablishmentFoodMovementRepository> {
        EstablishmentFoodMovementRepositoryImpl(api = get())
    }
    single<PushDeviceRepository> {
        PushDeviceRepositoryImpl(api = get(), deviceIdProvider = get(), tokenProvider = get())
    }
    single<FoodMovementHistoryRepository> {
        FoodMovementHistoryRepositoryImpl(lifecycle = get(), api = get(), cache = get(), json = get(named("auth")))
    }
    single<SurveyResponseHistoryRepository> {
        SurveyResponseHistoryRepositoryImpl(
            lifecycle = get(),
            api = get(),
            activityApi = get(),
            local = get(),
            cache = get(),
            surveys = get(),
            json = get(named("auth")),
        )
    }
}
