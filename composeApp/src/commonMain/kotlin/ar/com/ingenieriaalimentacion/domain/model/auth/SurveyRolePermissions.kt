package ar.com.ingenieriaalimentacion.domain.model.auth

private val SURVEY_RESPONSE_ROLES = setOf(
    UserRole.FIELD_WORKER,
    UserRole.ADMIN,
    UserRole.ANALYST,
    UserRole.SUPERADMIN,
)

/**
 * Roles that may submit or edit survey responses. Admins and analysts only do so as
 * field workers of an establishment they are approved in; the backend validates that link
 * per response, and callers gate on [requiresApprovedEstablishment].
 */
fun canRespondToSurveys(role: String): Boolean = role in SURVEY_RESPONSE_ROLES
