package ar.com.ingenieriaalimentacion.domain.model.auth

private val SURVEY_RESPONSE_ROLES = setOf("field_worker", "admin", "superadmin")

/** `analyst` cannot submit or edit survey responses — the only role excluded. */
fun canRespondToSurveys(role: String): Boolean = role in SURVEY_RESPONSE_ROLES
