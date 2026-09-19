package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowError
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantity

internal fun FoodOutflowError.toUserMessage(lines: List<FoodOutflowLine>): String = when (this) {
    is FoodOutflowError.InsufficientStock -> {
        val name = lines.firstOrNull { it.stock.foodId == foodId }?.stock?.foodName ?: "Ese alimento"
        "$name ya no tiene stock suficiente. Disponible: ${formatQuantity(available)}."
    }
    FoodOutflowError.EstablishmentAccessDenied -> "No tenés acceso a ese establecimiento."
    FoodOutflowError.EstablishmentNotFound -> "El establecimiento no existe."
    FoodOutflowError.FoodNotFound -> "Un alimento del egreso ya no está en el catálogo."
    FoodOutflowError.InvalidData -> "Revisá los datos del egreso e intentá nuevamente."
    FoodOutflowError.Network -> "No se pudo enviar. Revisá tu conexión."
    FoodOutflowError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    is FoodOutflowError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
