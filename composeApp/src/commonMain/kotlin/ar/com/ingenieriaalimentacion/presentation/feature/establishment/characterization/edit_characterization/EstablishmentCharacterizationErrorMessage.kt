package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError

internal fun CharacterizationError.toUserMessage(): String = when (this) {
    CharacterizationError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    CharacterizationError.Forbidden -> "No tenés acceso aprobado a este establecimiento."
    CharacterizationError.NotFound -> "Este establecimiento ya no existe."
    is CharacterizationError.InvalidField -> INVALID_FIELDS_MESSAGE
    CharacterizationError.Network -> "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
    is CharacterizationError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}

internal const val INVALID_FIELDS_MESSAGE = "Hay datos para revisar. Corregí los campos marcados y volvé a guardar."
