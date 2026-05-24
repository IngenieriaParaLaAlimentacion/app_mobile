package ar.com.ingenieriaalimentacion.presentation.feature.get_started

sealed class GetStartedDestination {
    data object NavigateToRegister : GetStartedDestination()
}
