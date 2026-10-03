package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.food.mapper.toRequest
import ar.com.ingenieriaalimentacion.data.food.mapper.wireValue
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptProposal
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource
import ar.com.ingenieriaalimentacion.domain.repository.ReceiptIntakeRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

class ReceiptIntakeRepositoryImpl(private val api: ReceiptIntakeApi) : ReceiptIntakeRepository {
    override suspend fun propose(establishmentId: String, photo: ReceiptPhoto): Result<ReceiptProposal> =
        catchingReceipt { api.propose(establishmentId, photo).toDomain() }

    override suspend fun confirm(intake: NewFoodIntake, source: ReceiptSource, photo: ReceiptPhoto): Result<Unit> =
        catchingReceipt {
            val iso = Instant.fromEpochMilliseconds(intake.receivedAtEpochMillis).toString()
            api.confirm(intake.toRequest(iso).copy(source = source.wireValue()), photo)
        }

    private suspend inline fun <T> catchingReceipt(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReceiptFailure) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ReceiptFailure(ReceiptError.Retryable, e))
        }
}
