package ar.com.ingenieriaalimentacion.domain.model.profile

sealed interface EstablishmentWithdrawalError {
    data object Unauthorized : EstablishmentWithdrawalError           // 401
    data object Forbidden : EstablishmentWithdrawalError              // 403
    data object EstablishmentNotFound : EstablishmentWithdrawalError  // 404 no relation with the caller
    data object NotWithdrawable : EstablishmentWithdrawalError        // 409 relation isn't approved/pending
    data object Network : EstablishmentWithdrawalError
    data class Unknown(val cause: Throwable?) : EstablishmentWithdrawalError
}
