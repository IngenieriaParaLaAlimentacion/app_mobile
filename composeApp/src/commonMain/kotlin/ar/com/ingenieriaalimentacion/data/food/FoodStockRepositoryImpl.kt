package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage
import ar.com.ingenieriaalimentacion.domain.repository.FoodStockRepository

/**
 * Remote-only. No cache half, no queue half (see [FoodStockRepository]'s KDoc) — which is why this
 * class is a pure outcome→Result translation with no fallback branch, unlike
 * [FoodMovementHistoryRepositoryImpl].
 */
class FoodStockRepositoryImpl(private val api: FoodStockApi) : FoodStockRepository {

    override suspend fun getStockPage(
        establishmentId: String,
        filters: FoodStockFilters,
        page: Int,
        pageSize: Int,
    ): Result<FoodStockPage> {
        val outcome = api.listStock(
            establishmentId = establishmentId,
            onlyAvailable = filters.onlyAvailable,
            categoryId = filters.categoryId,
            // The one place the domain sort becomes a wire token.
            sortOrder = filters.sortOrder.wireValue,
            page = page,
            pageSize = pageSize,
        )
        return when (outcome) {
            is FoodStockPageOutcome.Loaded -> Result.success(outcome.response.toDomain())
            FoodStockPageOutcome.EstablishmentAccessDenied ->
                fail(FoodStockError.EstablishmentAccessDenied)
            FoodStockPageOutcome.EstablishmentNotFound -> fail(FoodStockError.EstablishmentNotFound)
            FoodStockPageOutcome.InvalidRequest -> fail(FoodStockError.InvalidRequest)
            FoodStockPageOutcome.Unauthorized -> fail(FoodStockError.Unauthorized)
            is FoodStockPageOutcome.Retryable -> fail(FoodStockError.Network, outcome.cause)
        }
    }

    private fun fail(error: FoodStockError, cause: Throwable? = null): Result<FoodStockPage> =
        Result.failure(FoodStockFailure(error, cause))
}
