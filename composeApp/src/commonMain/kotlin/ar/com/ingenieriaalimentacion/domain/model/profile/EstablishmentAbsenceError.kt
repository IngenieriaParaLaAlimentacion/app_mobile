package ar.com.ingenieriaalimentacion.domain.model.profile

sealed interface EstablishmentAbsenceError {
    data object Unauthorized : EstablishmentAbsenceError  // 401
    data object Forbidden : EstablishmentAbsenceError     // 403 non-field-worker
    data object Validation : EstablishmentAbsenceError    // 400/422 invalid data
    data object Network : EstablishmentAbsenceError
    data class Unknown(val cause: Throwable?) : EstablishmentAbsenceError
}
