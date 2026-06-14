package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

/** Localizes the backend role string to a user-facing Spanish label. */
internal fun roleLabel(role: String): String = when (role) {
    "superadmin" -> "Superadministrador"
    "admin" -> "Administrador"
    "professional" -> "Profesional"
    "establishment_manager" -> "Encargado de establecimiento"
    else -> role
}
