package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeFailure
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.repository.FoodIntakeRepository
import kotlin.time.Instant

class FoodIntakeRepositoryImpl(private val api: FoodIntakeApi) : FoodIntakeRepository {

    override suspend fun createIntake(intake: NewFoodIntake): Result<Unit> {
        // ISO 8601 UTC with zone (…Z) — same stamping as MeasurementRepositoryImpl.enqueue.
        val occurredAt = Instant.fromEpochMilliseconds(intake.receivedAtEpochMillis).toString()
        return when (val outcome = api.create(intake.toRequest(occurredAt))) {
            is FoodIntakeUploadOutcome.Created -> Result.success(Unit)
            FoodIntakeUploadOutcome.EstablishmentAccessDenied ->
                fail(FoodIntakeError.EstablishmentAccessDenied)

            FoodIntakeUploadOutcome.EstablishmentNotFound -> fail(FoodIntakeError.EstablishmentNotFound)
            FoodIntakeUploadOutcome.FoodNotFound -> fail(FoodIntakeError.FoodNotFound)
            FoodIntakeUploadOutcome.InvalidData -> fail(FoodIntakeError.InvalidData)
            FoodIntakeUploadOutcome.Unauthorized -> fail(FoodIntakeError.Unauthorized)
            is FoodIntakeUploadOutcome.Retryable -> fail(FoodIntakeError.Network, outcome.cause)
        }
    }

    private fun fail(error: FoodIntakeError, cause: Throwable? = null): Result<Unit> =
        Result.failure(FoodIntakeFailure(error, cause))
}
