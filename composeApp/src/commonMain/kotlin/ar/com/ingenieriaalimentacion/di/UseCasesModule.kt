package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.domain.usecase.activity.WarmActivityCacheUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ClearLocalDataUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.RequestPasswordRecoveryUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ResetPasswordUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignInUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodOutflowUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodMovementHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.ObserveFoodMovementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.SearchFoodsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.CreateMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.DeleteMeasurementUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.GetMeasurementHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.ObserveMeasurementsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.RetryMeasurementUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.RegisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.SelectEstablishmentFromPushUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.notification.UnregisterPushDeviceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ClearEstablishmentCacheUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RefreshMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.NotifyEstablishmentAbsenceUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.WithdrawFromEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterFieldWorkerUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.DeleteSubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.RetrySubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.SyncSubjectsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.DiscardSurveyResponseUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.EditSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetAvailableSurveysUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseDetailUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.GetSurveyResponseHistoryPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseEditStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.ObserveSurveyResponseUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseEditUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.RetrySurveyResponseUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.SubmitSurveyResponseUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.UpdateSurveyResponseNumberUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.survey.WarmSurveyCacheUseCase
import org.koin.dsl.module

val useCasesModule = module {
    factory { InitializeAuthUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { RegisterPushDeviceUseCase(authRepository = get(), pushRepository = get()) }
    factory { UnregisterPushDeviceUseCase(repository = get()) }
    factory { SelectEstablishmentFromPushUseCase(profileRepository = get()) }
    factory { SignOutUseCase(repository = get(), unregisterPushDevice = get()) }
    factory { ClearLocalDataUseCase(get()) }
    factory { ResetPasswordUseCase(repository = get(), unregisterPushDevice = get()) }
    factory { RequestPasswordRecoveryUseCase(get()) }
    factory { ObserveSessionStatusUseCase(get()) }
    factory { GetEstablishmentsUseCase(get()) }
    factory { RegisterFieldWorkerUseCase(get()) }
    factory { CreateSubjectUseCase(get()) }
    factory { GetSubjectsUseCase(get()) }
    factory { ObserveSubjectUploadsUseCase(get()) }
    factory { GetSubjectHistoryPageUseCase(get()) }
    factory { SyncSubjectsUseCase(get()) }
    factory { RetrySubjectUploadUseCase(get()) }
    factory { DeleteSubjectUploadUseCase(get()) }
    factory { CreateMeasurementUseCase(get()) }
    factory { ObserveMeasurementsUseCase(get()) }
    factory { GetMeasurementHistoryPageUseCase(get()) }
    factory { RetryMeasurementUploadUseCase(get()) }
    factory { DeleteMeasurementUploadUseCase(get()) }
    factory { GetMyProfileUseCase(get()) }
    factory { GetMyEstablishmentsUseCase(get()) }
    factory { RefreshMyEstablishmentsUseCase(get()) }
    factory { ClearEstablishmentCacheUseCase(get()) }
    factory { GetCurrentEstablishmentUseCase(get()) }
    factory { SetCurrentEstablishmentUseCase(get()) }
    factory { UpdateMyProfileUseCase(get()) }
    factory { GetAvailableEstablishmentsUseCase(get()) }
    factory { RequestEstablishmentAccessUseCase(get()) }
    factory { WithdrawFromEstablishmentUseCase(get()) }
    factory { NotifyEstablishmentAbsenceUseCase(get()) }
    factory { WarmActivityCacheUseCase(get(), get(), get(), get()) }
    factory { GetAvailableSurveysUseCase(get()) }
    factory { WarmSurveyCacheUseCase(get()) }
    factory { GetSurveyDetailUseCase(get()) }
    factory { SubmitSurveyResponseUseCase(get()) }
    factory { UpdateSurveyResponseNumberUseCase(get()) }
    factory { GetSurveyResponseHistoryPageUseCase(get()) }
    factory { ObserveSurveyResponseUploadsUseCase(get()) }
    factory { GetSurveyResponseDetailUseCase(get()) }
    factory { RetrySurveyResponseUploadUseCase(get()) }
    factory { DiscardSurveyResponseUploadUseCase(get()) }
    factory { EditSurveyResponseUseCase(get()) }
    factory { ObserveSurveyResponseEditStatusUseCase(get()) }
    factory { RetrySurveyResponseEditUploadUseCase(get()) }
    factory { SearchFoodsUseCase(get()) }
    factory { CreateFoodIntakeUseCase(get()) }
    factory { CreateFoodOutflowUseCase(get()) }
    factory { GetFoodMovementHistoryPageUseCase(get()) }
    factory { ObserveFoodMovementsUseCase(get()) }
    factory { GetFoodStockPageUseCase(get()) }
    factory { GetFoodCategoriesUseCase(get()) }
    factory { GetEstablishmentFoodMovementPageUseCase(get()) }
    factory { GetEstablishmentFoodMovementDetailUseCase(get()) }
    factory { ResolveCurrentEstablishmentUseCase(get()) }
}
