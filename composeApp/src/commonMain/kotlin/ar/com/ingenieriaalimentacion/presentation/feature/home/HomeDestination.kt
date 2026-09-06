package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed class HomeDestination {
    data object Back : HomeDestination()
    data object NavigateToCreateSubject : HomeDestination()
    data object NavigateToCreateMeasurement : HomeDestination()
    data object NavigateToHistory : HomeDestination()   // switch to the History tab
    data object NavigateToSurveyList : HomeDestination()
    data object NavigateToCreateFoodIntake : HomeDestination()
}
