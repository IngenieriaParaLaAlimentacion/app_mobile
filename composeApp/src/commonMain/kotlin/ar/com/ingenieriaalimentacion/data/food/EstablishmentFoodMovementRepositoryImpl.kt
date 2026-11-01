package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentFoodMovementRepository

class EstablishmentFoodMovementRepositoryImpl(
    private val api: EstablishmentFoodMovementApi,
) : EstablishmentFoodMovementRepository {
    override suspend fun getPage(
        establishmentId: String,
        filters: EstablishmentFoodMovementFilters,
        page: Int,
        pageSize: Int,
    ): Result<EstablishmentFoodMovementPage> = when (
        val outcome = api.list(
            establishmentId,
            filters.direction?.wireValue,
            filters.occurredFromIso,
            filters.occurredToIso,
            filters.foodId,
            page,
            pageSize,
        )
    ) {
        is FoodMovementPageOutcome.Loaded -> runCatching { outcome.response.toDomain() }
            .recoverCatching {
                throw EstablishmentFoodMovementFailure(
                    EstablishmentFoodMovementError.InvalidRequest,
                    it,
                )
            }
        FoodMovementPageOutcome.EstablishmentAccessDenied ->
            fail(EstablishmentFoodMovementError.EstablishmentAccessDenied)
        FoodMovementPageOutcome.EstablishmentNotFound ->
            fail(EstablishmentFoodMovementError.EstablishmentNotFound)
        FoodMovementPageOutcome.InvalidRequest -> fail(EstablishmentFoodMovementError.InvalidRequest)
        FoodMovementPageOutcome.Unauthorized -> fail(EstablishmentFoodMovementError.Unauthorized)
        is FoodMovementPageOutcome.Retryable ->
            fail(EstablishmentFoodMovementError.Network, outcome.cause)
    }

    override suspend fun getDetail(
        establishmentId: String,
        movementId: String,
    ): Result<EstablishmentFoodMovementDetail> = when (val outcome = api.detail(establishmentId, movementId)) {
        is FoodMovementDetailOutcome.Loaded -> runCatching { outcome.response.toDomain() }
            .recoverCatching {
                throw EstablishmentFoodMovementFailure(
                    EstablishmentFoodMovementError.InvalidRequest,
                    it,
                )
            }
        FoodMovementDetailOutcome.EstablishmentAccessDenied ->
            fail(EstablishmentFoodMovementError.EstablishmentAccessDenied)
        FoodMovementDetailOutcome.EstablishmentNotFound ->
            fail(EstablishmentFoodMovementError.EstablishmentNotFound)
        FoodMovementDetailOutcome.MovementNotFound ->
            fail(EstablishmentFoodMovementError.MovementNotFound)
        FoodMovementDetailOutcome.InvalidRequest -> fail(EstablishmentFoodMovementError.InvalidRequest)
        FoodMovementDetailOutcome.Unauthorized -> fail(EstablishmentFoodMovementError.Unauthorized)
        is FoodMovementDetailOutcome.Retryable ->
            fail(EstablishmentFoodMovementError.Network, outcome.cause)
    }

    private fun <T> fail(
        error: EstablishmentFoodMovementError,
        cause: Throwable? = null,
    ): Result<T> = Result.failure(EstablishmentFoodMovementFailure(error, cause))
}
