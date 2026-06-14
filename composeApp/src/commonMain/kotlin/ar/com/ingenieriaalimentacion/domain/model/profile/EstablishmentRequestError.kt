package ar.com.ingenieriaalimentacion.domain.model.profile

sealed interface EstablishmentRequestError {
    data object Unauthorized : EstablishmentRequestError          // 401
    data object Forbidden : EstablishmentRequestError             // 403 non-manager
    data object EstablishmentNotFound : EstablishmentRequestError // 404 (all-or-nothing)
    data object Network : EstablishmentRequestError
    data class Unknown(val cause: Throwable?) : EstablishmentRequestError
}
