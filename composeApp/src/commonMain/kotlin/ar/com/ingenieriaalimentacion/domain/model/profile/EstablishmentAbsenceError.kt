package ar.com.ingenieriaalimentacion.domain.model.profile

sealed interface EstablishmentAbsenceError {
    data object Unauthorized : EstablishmentAbsenceError  // 401
    data object Forbidden : EstablishmentAbsenceError     // 403 non-field-worker
    /**
     * 400/422 invalid data. [field] is the backend's `field` extension, the dotted body path of the
     * first field that failed (`formatted_address`, `characterization.premises_other`), when reported.
     */
    data class Validation(val field: String? = null) : EstablishmentAbsenceError
    data object Network : EstablishmentAbsenceError
    data class Unknown(val cause: Throwable?) : EstablishmentAbsenceError
}
