package ar.com.ingenieriaalimentacion.domain.model.registration

sealed interface RegistrationError {
    data object UserAlreadyExists : RegistrationError       // 409
    data object EstablishmentNotFound : RegistrationError   // 404
    data object InvalidData : RegistrationError             // 400
    data object Network : RegistrationError
    data class Unknown(val cause: Throwable?) : RegistrationError
}
