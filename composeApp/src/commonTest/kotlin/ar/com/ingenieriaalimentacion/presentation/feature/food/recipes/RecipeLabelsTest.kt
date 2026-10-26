package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.recipe.RecipeError
import ar.com.ingenieriaalimentacion.domain.model.recipe.RecipeIngredient
import ar.com.ingenieriaalimentacion.domain.model.recipe.RecipeShortfall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone

class RecipeLabelsTest {
    @Test
    fun `stock error shows the food and exact base unit quantities`() {
        val message =
            recipeErrorMessage(
                RecipeError.InsufficientStock("rice", "2.500", "1.234"),
                listOf(RecipeIngredient("rice", "Arroz", "2.500", FoodUnit.Kilogramos)),
            )
        assertTrue(message.contains("Arroz"))
        assertTrue(message.contains("1,234 kg"))
        assertTrue(message.contains("2,5 kg"))
    }

    @Test
    fun `small positive shortfall is never described as zero`() {
        assertTrue(
            shortfallMessage(RecipeShortfall("macro_above_max", "proteinas", 0.001, "kcal"))
                .contains("menos de 0,1")
        )
    }

    @Test
    fun `history dates use local time across UTC midnight`() {
        assertEquals(
            "2026-10-17",
            recipeDate("2026-10-18T02:00:00Z", TimeZone.of("America/Argentina/Buenos_Aires")),
        )
    }

    @Test
    fun `availability warnings are not mislabeled as nutrient data gaps`() {
        val messages =
            recipeWarningMessages(
                listOf("recipes/cultural-unavailable", "recipes/fewer-alternatives")
            )
        assertEquals(2, messages.size)
        assertTrue(messages.any { it.contains("inspiración cultural") })
        assertTrue(messages.any { it.contains("menos alternativas") })
        assertTrue(messages.none { it.contains("composición") })
    }

    @Test
    fun `partial availability retains real nutrition warnings`() {
        val messages =
            recipeWarningMessages(
                listOf(
                    "recipes/fewer-alternatives",
                    "1 food(s) have no value for folato_efd, counted as 0",
                    "no conversion to grams",
                )
            )
        assertEquals(3, messages.size)
        assertTrue(messages.any { it.contains("equivalencia") })
        assertTrue(messages.any { it.contains("composición") })
    }

    @Test
    fun `no warnings produces no warning banners`() {
        assertEquals(emptyList(), recipeWarningMessages(emptyList()))
    }

    @Test
    fun `AI failures have distinct Spanish messages`() {
        assertEquals(
            "Se alcanzó el límite del servicio de IA. Intentá generar las recetas más tarde. Podés seguir usando el historial.",
            recipeErrorMessage(RecipeError.AiQuotaExhausted, emptyList()),
        )
        assertEquals(
            "El servicio de IA no está disponible. Intentá generar las recetas más tarde. Podés seguir usando el historial.",
            recipeErrorMessage(RecipeError.AiUnavailable, emptyList()),
        )
        assertEquals(
            "Alcanzaste el límite de solicitudes. Esperá antes de volver a generar recetas.",
            recipeErrorMessage(RecipeError.RateLimited, emptyList()),
        )
    }

}
