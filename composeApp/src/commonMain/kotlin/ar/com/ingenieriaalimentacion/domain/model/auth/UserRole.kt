package ar.com.ingenieriaalimentacion.domain.model.auth

/** Raw role strings as the backend sends them (`AuthUser.role`, `Profile.role`). */
object UserRole {
    const val SUPERADMIN = "superadmin"
    const val ADMIN = "admin"
    const val ANALYST = "analyst"
    const val FIELD_WORKER = "field_worker"
}

/** Roles with backoffice-wide authority, e.g. editing any survey response. */
val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.SUPERADMIN)

/**
 * How a user links to an establishment: an admin or superadmin subscribes directly
 * (approved on the spot), everyone else requests access and waits for an admin's approval.
 */
enum class EstablishmentLinkMode { RequestAccess, Subscribe }

fun establishmentLinkMode(role: String): EstablishmentLinkMode =
    if (role in ADMIN_ROLES) EstablishmentLinkMode.Subscribe else EstablishmentLinkMode.RequestAccess

private val LINKING_ROLES = setOf(UserRole.FIELD_WORKER, UserRole.ANALYST) + ADMIN_ROLES

/** Whether the user can manage their own establishment links from "Mis establecimientos". */
fun canLinkEstablishments(role: String): Boolean = role in LINKING_ROLES

private val LINKED_FIELD_WORKER_ROLES = setOf(UserRole.ANALYST) + ADMIN_ROLES

/**
 * Admins, superadmins and analysts act as field workers only inside an establishment they are approved
 * in, so their field-worker actions need an approved establishment selected. Field
 * workers keep their current flow.
 */
fun requiresApprovedEstablishment(role: String): Boolean = role in LINKED_FIELD_WORKER_ROLES
