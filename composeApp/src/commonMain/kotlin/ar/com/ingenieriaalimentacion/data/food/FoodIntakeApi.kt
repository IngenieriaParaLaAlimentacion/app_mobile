package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.ESTABLISHMENT_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FOOD_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeProblem
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeRequest
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Food-intake write endpoint. Same status-branching convention as
 * [ar.com.ingenieriaalimentacion.data.survey.SurveyResponseApi]: the authed client does not set
 * `expectSuccess`, so Ktor never throws on a non-2xx and we branch on the status explicitly.
 */
class FoodIntakeApi(private val client: HttpClient) {

    suspend fun create(body: FoodIntakeRequest): FoodIntakeUploadOutcome = runCatching {
        val response = client.post("api/v1/food-intakes") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when (response.status.value) {
            // 201 = created, 200 = idempotent replay of the same id. Same meaning to the app:
            // the intake is registered and the stock was moved exactly once.
            200, 201 -> FoodIntakeUploadOutcome.Created(response.body())
            400, 422 -> FoodIntakeUploadOutcome.InvalidData
            401 -> FoodIntakeUploadOutcome.Unauthorized
            403 -> FoodIntakeUploadOutcome.EstablishmentAccessDenied
            404 -> when (response.problemType()) {
                FOOD_NOT_FOUND_TYPE -> FoodIntakeUploadOutcome.FoodNotFound
                ESTABLISHMENT_NOT_FOUND_TYPE -> FoodIntakeUploadOutcome.EstablishmentNotFound
                // A 404 this build doesn't recognize is still terminal — retrying the same body
                // can only 404 again.
                else -> FoodIntakeUploadOutcome.InvalidData
            }
            else -> FoodIntakeUploadOutcome.Retryable(null)   // 5xx
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        FoodIntakeUploadOutcome.Retryable(t)                  // transport failures
    }

    private suspend fun HttpResponse.problemType(): String? =
        runCatching { body<FoodIntakeProblem>() }.getOrNull()?.type
}

sealed interface FoodIntakeUploadOutcome {
    data class Created(val response: FoodIntakeResponse) : FoodIntakeUploadOutcome
    data object EstablishmentAccessDenied : FoodIntakeUploadOutcome
    data object EstablishmentNotFound : FoodIntakeUploadOutcome
    data object FoodNotFound : FoodIntakeUploadOutcome
    data object InvalidData : FoodIntakeUploadOutcome
    data object Unauthorized : FoodIntakeUploadOutcome
    data class Retryable(val cause: Throwable?) : FoodIntakeUploadOutcome
}
