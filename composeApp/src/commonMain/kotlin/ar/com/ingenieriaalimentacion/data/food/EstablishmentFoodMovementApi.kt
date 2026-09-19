package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.ESTABLISHMENT_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.EstablishmentFoodMovementDetailDto
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeProblem
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedEstablishmentFoodMovementsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

class EstablishmentFoodMovementApi(private val client: HttpClient) {
    suspend fun list(
        establishmentId: String,
        direction: String?,
        occurredFrom: String?,
        occurredTo: String?,
        foodId: String?,
        page: Int,
        pageSize: Int,
    ): FoodMovementPageOutcome = runCatching {
        val response = client.get("api/v1/establishments/$establishmentId/food-movements") {
            parameter("page", page)
            parameter("page_size", pageSize)
            direction?.takeIf(String::isNotBlank)?.let { parameter("direction", it) }
            occurredFrom?.takeIf(String::isNotBlank)?.let { parameter("occurred_from", it) }
            occurredTo?.takeIf(String::isNotBlank)?.let { parameter("occurred_to", it) }
            foodId?.takeIf(String::isNotBlank)?.let { parameter("food_id", it) }
        }
        when (response.status.value) {
            200 -> FoodMovementPageOutcome.Loaded(response.body())
            400, 422 -> FoodMovementPageOutcome.InvalidRequest
            401 -> FoodMovementPageOutcome.Unauthorized
            403 -> FoodMovementPageOutcome.EstablishmentAccessDenied
            404 -> if (response.problemType() == ESTABLISHMENT_NOT_FOUND_TYPE) {
                FoodMovementPageOutcome.EstablishmentNotFound
            } else {
                FoodMovementPageOutcome.InvalidRequest
            }
            else -> FoodMovementPageOutcome.Retryable(null)
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        FoodMovementPageOutcome.Retryable(throwable)
    }

    suspend fun detail(
        establishmentId: String,
        movementId: String,
    ): FoodMovementDetailOutcome = runCatching {
        val response = client.get(
            "api/v1/establishments/$establishmentId/food-movements/$movementId",
        )
        when (response.status.value) {
            200 -> FoodMovementDetailOutcome.Loaded(response.body())
            400, 422 -> FoodMovementDetailOutcome.InvalidRequest
            401 -> FoodMovementDetailOutcome.Unauthorized
            403 -> FoodMovementDetailOutcome.EstablishmentAccessDenied
            404 -> when (response.problemType()) {
                ESTABLISHMENT_NOT_FOUND_TYPE -> FoodMovementDetailOutcome.EstablishmentNotFound
                else -> FoodMovementDetailOutcome.MovementNotFound
            }
            else -> FoodMovementDetailOutcome.Retryable(null)
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        FoodMovementDetailOutcome.Retryable(throwable)
    }

    private suspend fun HttpResponse.problemType(): String? =
        runCatching { body<FoodIntakeProblem>() }.getOrNull()?.type
}

sealed interface FoodMovementPageOutcome {
    data class Loaded(
        val response: PaginatedEstablishmentFoodMovementsResponse,
    ) : FoodMovementPageOutcome
    data object EstablishmentAccessDenied : FoodMovementPageOutcome
    data object EstablishmentNotFound : FoodMovementPageOutcome
    data object InvalidRequest : FoodMovementPageOutcome
    data object Unauthorized : FoodMovementPageOutcome
    data class Retryable(val cause: Throwable?) : FoodMovementPageOutcome
}

sealed interface FoodMovementDetailOutcome {
    data class Loaded(val response: EstablishmentFoodMovementDetailDto) : FoodMovementDetailOutcome
    data object EstablishmentAccessDenied : FoodMovementDetailOutcome
    data object EstablishmentNotFound : FoodMovementDetailOutcome
    data object MovementNotFound : FoodMovementDetailOutcome
    data object InvalidRequest : FoodMovementDetailOutcome
    data object Unauthorized : FoodMovementDetailOutcome
    data class Retryable(val cause: Throwable?) : FoodMovementDetailOutcome
}
