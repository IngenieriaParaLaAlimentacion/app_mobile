package ar.com.ingenieriaalimentacion.presentation.feature.get_started

sealed interface GetStartedAction {
    data object SignUpClick : GetStartedAction
    data object LogInClick : GetStartedAction
}
