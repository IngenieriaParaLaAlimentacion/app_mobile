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
}
