package ar.com.ingenieriaalimentacion.domain.model.profile

/**
 * The signed-in user. [establishments] is three-state: `null` means the user is
 * not a manager (no section), `[]` means a manager with no relations yet, and a
 * non-empty list carries each relation with its [EstablishmentStatus].
 */
data class Profile(
    val id: String,
    val email: String,
    val name: String,
    val lastName: String,
    val role: String,
    val establishments: List<EstablishmentWithStatus>?,
) {
    val isManager: Boolean get() = establishments != null
}
