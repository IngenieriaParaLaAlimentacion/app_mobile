package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.recipe.*

interface RecipeRepository {
    suspend fun generate(
        establishmentId: String,
        childrenCount: Int,
        mealType: MealType,
    ): Result<RecipeRequest>

    suspend fun history(establishmentId: String, page: Int): Result<RecipeHistoryPage>

    suspend fun choose(requestId: String, proposalId: String): Result<RecipeSelection>

    suspend fun previewReuse(
        requestId: String,
        proposalId: String,
        childrenCount: Int,
    ): Result<RecipeProposal>

    suspend fun reuse(
        requestId: String,
        proposalId: String,
        id: String,
        childrenCount: Int,
    ): Result<RecipeRequest>
}
