package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed interface HomeAction {
    data object PrimaryClick : HomeAction
}
