package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError

internal fun ProfileError.toUserMessage(): String = when (this) {
    ProfileError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    ProfileError.UserNotFound -> "No se encontró el usuario."
    ProfileError.ValidationError -> "Revisá los datos ingresados."
    ProfileError.Network -> "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
    is ProfileError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
