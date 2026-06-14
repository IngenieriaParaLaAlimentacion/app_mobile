package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

sealed interface ProfileAction {
    data object Load : ProfileAction
    data object RetryClick : ProfileAction
    data object EditClick : ProfileAction
    data object MyEstablishmentsClick : ProfileAction
    data object SignOutClick : ProfileAction
    data object ConfirmSignOut : ProfileAction
    data object DismissSignOutConfirm : ProfileAction
}
