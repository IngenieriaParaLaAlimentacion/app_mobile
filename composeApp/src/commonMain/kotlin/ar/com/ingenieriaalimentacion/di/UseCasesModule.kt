package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.domain.usecase.auth.InitializeAuthUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.ObserveSessionStatusUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignInUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.registration.RegisterManagerUseCase
import org.koin.dsl.module

val useCasesModule = module {
    factory { InitializeAuthUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { ObserveSessionStatusUseCase(get()) }
    factory { GetEstablishmentsUseCase(get()) }
    factory { RegisterManagerUseCase(get()) }
}
