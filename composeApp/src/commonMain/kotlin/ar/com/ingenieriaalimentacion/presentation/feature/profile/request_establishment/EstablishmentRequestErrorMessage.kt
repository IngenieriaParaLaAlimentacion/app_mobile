package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError

internal fun EstablishmentRequestError.toUserMessage(): String = when (this) {
    EstablishmentRequestError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    EstablishmentRequestError.Forbidden -> "No tenés permisos para solicitar acceso."
    EstablishmentRequestError.EstablishmentNotFound ->
        "Alguno de los establecimientos ya no está disponible."
    EstablishmentRequestError.Network ->
        "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
    is EstablishmentRequestError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
