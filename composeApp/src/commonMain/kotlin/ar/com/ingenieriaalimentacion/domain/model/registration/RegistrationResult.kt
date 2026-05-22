package ar.com.ingenieriaalimentacion.domain.model.registration

data class RegistrationResult(
    val id: String,
    val email: String,
    val status: String, // expected "pending_validation"
)
