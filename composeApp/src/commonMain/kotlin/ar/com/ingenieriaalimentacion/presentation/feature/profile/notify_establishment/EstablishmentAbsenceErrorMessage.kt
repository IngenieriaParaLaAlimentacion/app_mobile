package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceError

internal fun EstablishmentAbsenceError.toUserMessage(): String = when (this) {
    EstablishmentAbsenceError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    EstablishmentAbsenceError.Forbidden -> "No tenés permisos para enviar este aviso."
    is EstablishmentAbsenceError.Validation ->
        "Alguno de los datos no es válido. Revisalos e intentá nuevamente."
    EstablishmentAbsenceError.Network ->
        "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
    is EstablishmentAbsenceError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
