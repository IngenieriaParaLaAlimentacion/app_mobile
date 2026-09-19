package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowFailure
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow
import ar.com.ingenieriaalimentacion.domain.repository.FoodOutflowRepository
import kotlin.time.Instant

class FoodOutflowRepositoryImpl(private val api: FoodOutflowApi) : FoodOutflowRepository {
    override suspend fun createOutflow(outflow: NewFoodOutflow): Result<Unit> {
        val occurredAt = Instant.fromEpochMilliseconds(outflow.occurredAtEpochMillis).toString()
        return when (val outcome = api.create(outflow.toRequest(occurredAt))) {
            is FoodOutflowUploadOutcome.Created -> Result.success(Unit)
            is FoodOutflowUploadOutcome.InsufficientStock -> fail(
                FoodOutflowError.InsufficientStock(
                    outcome.foodId,
                    outcome.requested,
                    outcome.available,
                ),
            )
            FoodOutflowUploadOutcome.EstablishmentAccessDenied ->
                fail(FoodOutflowError.EstablishmentAccessDenied)
            FoodOutflowUploadOutcome.EstablishmentNotFound ->
                fail(FoodOutflowError.EstablishmentNotFound)
            FoodOutflowUploadOutcome.FoodNotFound -> fail(FoodOutflowError.FoodNotFound)
            FoodOutflowUploadOutcome.InvalidData -> fail(FoodOutflowError.InvalidData)
            FoodOutflowUploadOutcome.Unauthorized -> fail(FoodOutflowError.Unauthorized)
            is FoodOutflowUploadOutcome.Retryable ->
                fail(FoodOutflowError.Network, outcome.cause)
        }
    }

    private fun fail(error: FoodOutflowError, cause: Throwable? = null): Result<Unit> =
        Result.failure(FoodOutflowFailure(error, cause))
}
