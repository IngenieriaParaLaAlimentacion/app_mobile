package ar.com.ingenieriaalimentacion.domain.model.registration

data class ManagerRegistration(
    val email: String,
    val password: String,
    val name: String,
    val lastName: String,
    val establishmentIds: List<String>,
)
