package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

/** Localizes the backend role string to a user-facing Spanish label. */
internal fun roleLabel(role: String): String = when (role) {
    "superadmin" -> "Superadministrador"
    "admin" -> "Administrador"
    "analyst" -> "Analista"
    "field_worker" -> "Trabajador de campo"
    else -> role
}
