package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.RequestPasswordRecoveryUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ResetPasswordUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignInUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterManagerUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.DeleteSubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.ObserveSubjectUploadsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.RetrySubjectUploadUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.subject.SyncSubjectsUseCase
import org.koin.dsl.module

val useCasesModule = module {
    factory { InitializeAuthUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { RequestPasswordRecoveryUseCase(get()) }
    factory { ObserveSessionStatusUseCase(get()) }
    factory { GetEstablishmentsUseCase(get()) }
    factory { RegisterManagerUseCase(get()) }
    factory { CreateSubjectUseCase(get()) }
    factory { GetSubjectsUseCase(get()) }
    factory { ObserveSubjectUploadsUseCase(get()) }
    factory { SyncSubjectsUseCase(get()) }
    factory { RetrySubjectUploadUseCase(get()) }
    factory { DeleteSubjectUploadUseCase(get()) }
    factory { GetMyProfileUseCase(get()) }
    factory { GetMyEstablishmentsUseCase(get()) }
    factory { UpdateMyProfileUseCase(get()) }
    factory { GetAvailableEstablishmentsUseCase(get()) }
    factory { RequestEstablishmentAccessUseCase(get()) }
}
