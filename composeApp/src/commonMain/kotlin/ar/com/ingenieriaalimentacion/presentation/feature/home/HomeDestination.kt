package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed class HomeDestination {
    data object Back : HomeDestination()
    data object NavigateToCreateSubject : HomeDestination()
    data object NavigateToCreateMeasurement : HomeDestination()
}
