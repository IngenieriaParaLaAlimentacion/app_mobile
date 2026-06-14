package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

sealed interface MyEstablishmentsAction {
    data object Load : MyEstablishmentsAction
    data object RetryClick : MyEstablishmentsAction
    data object RequestAccessClick : MyEstablishmentsAction
    data object BackClick : MyEstablishmentsAction
}
