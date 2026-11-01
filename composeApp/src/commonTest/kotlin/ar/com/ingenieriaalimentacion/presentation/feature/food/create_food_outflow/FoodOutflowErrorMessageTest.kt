package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowError
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodOutflowErrorMessageTest {
    @Test
    fun `typed failures have distinct user messages`() {
        val cases = mapOf(
            FoodOutflowError.InvalidData to "Revisá los datos del egreso e intentá nuevamente.",
            FoodOutflowError.Unauthorized to "Tu sesión expiró. Iniciá sesión nuevamente.",
            FoodOutflowError.EstablishmentAccessDenied to "No tenés acceso a ese establecimiento.",
            FoodOutflowError.EstablishmentNotFound to "El establecimiento no existe.",
            FoodOutflowError.FoodNotFound to "Un alimento del egreso ya no está en el catálogo.",
            FoodOutflowError.Network to "No se pudo enviar. Revisá tu conexión.",
            FoodOutflowError.Unknown(null) to "Ocurrió un error inesperado. Intentá nuevamente.",
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toUserMessage(emptyList()))
        }
    }
}
