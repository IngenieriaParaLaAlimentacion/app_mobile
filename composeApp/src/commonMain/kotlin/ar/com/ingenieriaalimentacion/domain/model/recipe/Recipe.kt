package ar.com.ingenieriaalimentacion.domain.model.recipe

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit

enum class MealType(val wireValue: String, val label: String) {
    Breakfast("breakfast", "Desayuno"),
    Lunch("lunch", "Almuerzo"),
    Snack("snack", "Merienda"),
    Dinner("dinner", "Cena"),
}

data class RecipeIngredient(
    val foodId: String,
    val name: String,
    val quantity: String,
    val unit: FoodUnit,
)

data class RecipeShortfall(
    val kind: String,
    val subject: String,
    val amount: Double,
    val unit: String,
)

data class RecipeProposal(
    val id: String,
    val name: String,
    val portions: Int,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val nutrientAdequacyPercent: Map<String, Double>,
    val macronutrientPercent: Map<String, Double>,
    val gapaGroups: List<String>,
    val unmetConstraints: List<RecipeShortfall>,
    val warnings: List<String>,
    val targetEnergyKcal: Double,
    val energyKcal: Double,
    val culturalIdentity: String?,
)

data class RecipeSelection(val proposalId: String, val outflowId: String)

data class RecipeRequest(
    val id: String,
    val establishmentId: String,
    val childrenCount: Int,
    val mealType: String,
    val createdAt: String,
    val proposals: List<RecipeProposal>,
    val selection: RecipeSelection?,
)

data class RecipeHistoryPage(val items: List<RecipeRequest>, val hasMore: Boolean)

sealed interface RecipeError {
    data object NoUsableStock : RecipeError

    data object AccessDenied : RecipeError

    data object Unauthorized : RecipeError

    data object NotFound : RecipeError

    data object AlreadyChosen : RecipeError

    data object IdempotencyConflict : RecipeError

    data object InvalidInput : RecipeError

    data object GenerationFailed : RecipeError

    data object AiQuotaExhausted : RecipeError

    data object AiUnavailable : RecipeError

    data object RateLimited : RecipeError

    data object Network : RecipeError

    data class InsufficientStock(val foodId: String, val requested: String, val available: String) :
        RecipeError
}

class RecipeFailure(val error: RecipeError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
