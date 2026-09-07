package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeError

internal fun FoodIntakeError.toUserMessage(): String = when (this) {
    FoodIntakeError.EstablishmentAccessDenied -> "No tenés acceso a ese establecimiento."
    FoodIntakeError.EstablishmentNotFound -> "El establecimiento no existe."
    FoodIntakeError.FoodNotFound -> "Un alimento de la carga ya no está en el catálogo."
    FoodIntakeError.InvalidData -> "Revisá los datos de la carga e intentá nuevamente."
    FoodIntakeError.Network -> "No se pudo enviar. Revisá tu conexión."
    // 401 is handled by the session machinery (the authed client's refreshTokens path); this copy
    // only shows if the user somehow still sees the screen afterwards.
    FoodIntakeError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    is FoodIntakeError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
