package ar.com.ingenieriaalimentacion.data.recipe

import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import ar.com.ingenieriaalimentacion.domain.repository.RecipeRepository

class RecipeRepositoryImpl(private val api: RecipeApi) : RecipeRepository {
    override suspend fun generate(establishmentId: String, childrenCount: Int, mealType: MealType) =
        api.generate(GenerateRecipeDto(establishmentId, childrenCount, mealType.wireValue))
            .mapCatching { it.toDomain() }

    override suspend fun history(establishmentId: String, page: Int) =
        api.history(establishmentId, page).mapCatching {
            RecipeHistoryPage(
                it.items.map { request -> request.toDomain() },
                it.page < it.totalPages,
            )
        }

    override suspend fun choose(requestId: String, proposalId: String) =
        api.choose(requestId, proposalId).mapCatching { it.toDomain() }

    override suspend fun previewReuse(requestId: String, proposalId: String, childrenCount: Int) =
        api.previewReuse(requestId, proposalId, childrenCount).mapCatching { it.toDomain() }

    override suspend fun reuse(
        requestId: String,
        proposalId: String,
        id: String,
        childrenCount: Int,
    ) =
        api.reuse(requestId, proposalId, ReuseRecipeDto(id, childrenCount)).mapCatching {
            it.toDomain()
        }
}

internal fun RecipeRequestDto.toDomain() =
    RecipeRequest(
        id,
        establishmentId,
        childrenCount,
        mealType,
        createdAt,
        proposals.map { it.toDomain() },
        selection?.toDomain(),
    )

private fun RecipeSelectionDto.toDomain() = RecipeSelection(proposalId, outflowId)

internal fun RecipeProposalDto.toDomain() =
    RecipeProposal(
        id,
        name,
        portions,
        ingredients.map { ingredient ->
            RecipeIngredient(
                ingredient.foodId,
                ingredient.name,
                ingredient.quantity,
                when (ingredient.unit) {
                    "kilogramos" -> FoodUnit.Kilogramos
                    "litros" -> FoodUnit.Litros
                    "unidades" -> FoodUnit.Unidades
                    else -> error("Unknown recipe unit: ${ingredient.unit}")
                },
            )
        },
        steps,
        nutrientAdequacyPercent,
        macronutrientPercent,
        gapaGroups,
        unmetConstraints.map { RecipeShortfall(it.kind, it.subject, it.amount, it.unit) },
        warnings,
        targetEnergyKcal,
        energyKcal,
        culturalIdentity,
    )
