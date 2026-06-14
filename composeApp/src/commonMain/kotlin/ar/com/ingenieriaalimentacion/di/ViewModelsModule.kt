package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.app.AppViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.SelectEstablishmentViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password.ForgotPasswordViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.login.LoginViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password.ResetPasswordViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.GetStartedViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.edit.EditProfileViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments.MyEstablishmentsViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment.RequestEstablishmentViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.view.ProfileViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.register.RegisterViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.CreateSubjectViewModel
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::GetStartedViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::CreateSubjectViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::RequestEstablishmentViewModel)
    viewModelOf(::MyEstablishmentsViewModel)
    viewModel { (route: Route.SelectEstablishment) ->
        SelectEstablishmentViewModel(route = route, getEstablishments = get())
    }
    viewModel { (route: Route.ResetPassword) ->
        ResetPasswordViewModel(route = route, resetPassword = get())
    }
    viewModel { (route: Route.EditProfile) ->
        EditProfileViewModel(route = route, updateMyProfile = get())
    }
}
