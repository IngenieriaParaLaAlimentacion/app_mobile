package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.ESTABLISHMENT_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FOOD_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowProblem
import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowResponse
import ar.com.ingenieriaalimentacion.data.food.dto.INSUFFICIENT_STOCK_TYPE
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class FoodOutflowApi(private val client: HttpClient) {
    suspend fun create(body: FoodOutflowRequest): FoodOutflowUploadOutcome = runCatching {
        val response = client.post("api/v1/food-outflows") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when (response.status.value) {
            200, 201 -> FoodOutflowUploadOutcome.Created(response.body())
            400, 422 -> FoodOutflowUploadOutcome.InvalidData
            401 -> FoodOutflowUploadOutcome.Unauthorized
            403 -> FoodOutflowUploadOutcome.EstablishmentAccessDenied
            404 -> when (response.problem()?.type) {
                FOOD_NOT_FOUND_TYPE -> FoodOutflowUploadOutcome.FoodNotFound
                ESTABLISHMENT_NOT_FOUND_TYPE -> FoodOutflowUploadOutcome.EstablishmentNotFound
                else -> FoodOutflowUploadOutcome.InvalidData
            }
            409 -> {
                val problem = response.problem()
                if (
                    problem?.type == INSUFFICIENT_STOCK_TYPE &&
                    problem.foodId != null && problem.requested != null && problem.available != null
                ) {
                    FoodOutflowUploadOutcome.InsufficientStock(
                        foodId = problem.foodId,
                        requested = problem.requested,
                        available = problem.available,
                    )
                } else {
                    FoodOutflowUploadOutcome.InvalidData
                }
            }
            else -> FoodOutflowUploadOutcome.Retryable(null)
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        FoodOutflowUploadOutcome.Retryable(throwable)
    }

    private suspend fun HttpResponse.problem(): FoodOutflowProblem? =
        runCatching { body<FoodOutflowProblem>() }.getOrNull()
}

sealed interface FoodOutflowUploadOutcome {
    data class Created(val response: FoodOutflowResponse) : FoodOutflowUploadOutcome
    data class InsufficientStock(
        val foodId: String,
        val requested: String,
        val available: String,
    ) : FoodOutflowUploadOutcome

    data object EstablishmentAccessDenied : FoodOutflowUploadOutcome
    data object EstablishmentNotFound : FoodOutflowUploadOutcome
    data object FoodNotFound : FoodOutflowUploadOutcome
    data object InvalidData : FoodOutflowUploadOutcome
    data object Unauthorized : FoodOutflowUploadOutcome
    data class Retryable(val cause: Throwable?) : FoodOutflowUploadOutcome
}
