package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.app.AppViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment.SelectEstablishmentViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.forgot_password.ForgotPasswordViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.login.LoginViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password.ResetPasswordViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake.CreateFoodIntakeViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history.FoodMovementHistoryViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.GetStartedViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.MeasurementViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads.MeasurementUploadsViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.edit.EditProfileViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments.MyEstablishmentsViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment.NotifyEstablishmentViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment.RequestEstablishmentViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.profile.view.ProfileViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.register.RegisterViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.CreateSubjectViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject.SelectSubjectViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads.SubjectUploadsViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list.SurveyListViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_detail.SurveyResponseDetailViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_edit.SurveyResponseEditViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_form.SurveyResponseFormViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history.SurveyResponseHistoryViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_number_fix.SurveyResponseNumberFixViewModel
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlin.time.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::GetStartedViewModel)
    viewModel {
        HomeViewModel(
            observeSubjectUploads = get(),
            observeMeasurements = get(),
            observeSurveyResponseUploads = get(),
            observeFoodMovements = get(),
            getMyProfile = get(),
            getMyEstablishments = get(),
            getCurrentEstablishment = get(),
            setCurrentEstablishment = get(),
            syncCoordinator = get(),
            connectivity = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    viewModelOf(::RegisterViewModel)
    viewModelOf(::LoginViewModel)
    viewModel { (route: Route.CreateSubject) ->
        CreateSubjectViewModel(route = route, createSubject = get(), syncCoordinator = get())
    }
    viewModelOf(::MeasurementViewModel)
    viewModelOf(::CreateFoodIntakeViewModel)
    viewModelOf(::MeasurementUploadsViewModel)
    viewModelOf(::SelectSubjectViewModel)
    viewModelOf(::SubjectUploadsViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::RequestEstablishmentViewModel)
    viewModelOf(::NotifyEstablishmentViewModel)
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
    viewModelOf(::SurveyListViewModel)
    viewModel { (route: Route.SurveyResponseForm) ->
        SurveyResponseFormViewModel(
            route = route,
            getSurveyDetail = get(),
            getMyEstablishments = get(),
            getCurrentEstablishment = get(),
            submitSurveyResponse = get(),
            syncCoordinator = get(),
        )
    }
    viewModelOf(::SurveyResponseHistoryViewModel)
    viewModelOf(::FoodMovementHistoryViewModel)
    viewModel { (route: Route.SurveyResponseDetail) ->
        SurveyResponseDetailViewModel(
            route = route,
            getSurveyResponseDetail = get(),
            getSurveyDetail = get(),
            getMyProfile = get(),
            observeEditStatus = get(),
            retryEditUpload = get(),
            syncCoordinator = get(),
        )
    }
    viewModel { (route: Route.SurveyResponseEdit) ->
        SurveyResponseEditViewModel(
            route = route,
            getSurveyResponseDetail = get(),
            getSurveyDetail = get(),
            editSurveyResponse = get(),
            syncCoordinator = get(),
        )
    }
    viewModel { (route: Route.SurveyResponseNumberFix) ->
        SurveyResponseNumberFixViewModel(route, get(), get())
    }
}
