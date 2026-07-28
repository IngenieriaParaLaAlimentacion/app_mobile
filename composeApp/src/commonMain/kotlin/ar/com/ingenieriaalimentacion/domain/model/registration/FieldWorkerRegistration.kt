package ar.com.ingenieriaalimentacion.domain.model.registration

/**
 * Self-registration payload. [locality], [description] and [contactPhone] are optional
 * contact details the admin sees when reviewing the account; they must be `null` (never
 * blank) when the user leaves them empty — the backend rejects empty strings.
 */
data class FieldWorkerRegistration(
    val email: String,
    val password: String,
    val name: String,
    val lastName: String,
    val establishmentIds: List<String>,
    val locality: String? = null,
    val description: String? = null,
    val contactPhone: String? = null,
)
