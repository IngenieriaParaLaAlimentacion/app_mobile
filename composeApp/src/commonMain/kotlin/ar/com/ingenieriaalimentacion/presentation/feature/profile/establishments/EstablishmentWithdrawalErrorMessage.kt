package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithdrawalError

internal fun EstablishmentWithdrawalError.toUserMessage(): String = when (this) {
    EstablishmentWithdrawalError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    EstablishmentWithdrawalError.Forbidden -> "No tenés permisos para darte de baja."
    EstablishmentWithdrawalError.EstablishmentNotFound,
    EstablishmentWithdrawalError.NotWithdrawable,
    -> "Ya no tenés acceso a ese establecimiento. Actualizamos la lista."
    EstablishmentWithdrawalError.Network ->
        "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
    is EstablishmentWithdrawalError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
