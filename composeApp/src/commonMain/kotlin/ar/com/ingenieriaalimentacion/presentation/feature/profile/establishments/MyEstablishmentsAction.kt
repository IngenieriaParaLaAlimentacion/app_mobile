package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

sealed interface MyEstablishmentsAction {
    data object Load : MyEstablishmentsAction
    data object RetryClick : MyEstablishmentsAction
    data object RequestAccessClick : MyEstablishmentsAction
    data object BackClick : MyEstablishmentsAction
    data class WithdrawClick(val establishmentId: String) : MyEstablishmentsAction
    data object ConfirmWithdraw : MyEstablishmentsAction
    data object DismissWithdrawConfirm : MyEstablishmentsAction
    data object ToastDismissed : MyEstablishmentsAction
}
