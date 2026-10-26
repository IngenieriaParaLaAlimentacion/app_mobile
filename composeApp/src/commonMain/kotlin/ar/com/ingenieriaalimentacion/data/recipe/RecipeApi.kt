package ar.com.ingenieriaalimentacion.data.recipe

import ar.com.ingenieriaalimentacion.data.food.dto.FoodOutflowProblem
import ar.com.ingenieriaalimentacion.data.food.dto.INSUFFICIENT_STOCK_TYPE
import ar.com.ingenieriaalimentacion.data.network.AI_QUOTA_EXHAUSTED_TYPE
import ar.com.ingenieriaalimentacion.data.network.AI_UNAVAILABLE_TYPE
import ar.com.ingenieriaalimentacion.data.network.RATE_LIMITED_TYPE
import ar.com.ingenieriaalimentacion.domain.model.recipe.RecipeError
import ar.com.ingenieriaalimentacion.domain.model.recipe.RecipeFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class RecipeApi(private val client: HttpClient) {
    suspend fun generate(body: GenerateRecipeDto): Result<RecipeRequestDto> = execute {
        client.post("api/v1/recipe-recommendations") {
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = 300_000
                socketTimeoutMillis = 300_000
            }
            setBody(body)
        }
    }

    suspend fun history(establishmentId: String, page: Int): Result<RecipeHistoryDto> = execute {
        client.get("api/v1/establishments/$establishmentId/recipe-recommendations") {
            parameter("chosen_only", true)
            parameter("page", page)
            parameter("page_size", 20)
        }
    }

    suspend fun choose(requestId: String, proposalId: String): Result<RecipeSelectionDto> =
        execute {
            client.post("${proposalPath(requestId, proposalId)}/choose")
        }

    suspend fun previewReuse(
        requestId: String,
        proposalId: String,
        count: Int,
    ): Result<RecipeProposalDto> = execute {
        client.post("${proposalPath(requestId, proposalId)}/reuse-preview") {
            contentType(ContentType.Application.Json)
            setBody(PreviewRecipeDto(count))
        }
    }

    suspend fun reuse(
        requestId: String,
        proposalId: String,
        body: ReuseRecipeDto,
    ): Result<RecipeRequestDto> = execute {
        client.post("${proposalPath(requestId, proposalId)}/reuse") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private fun proposalPath(request: String, proposal: String) =
        "api/v1/recipe-recommendations/$request/proposals/$proposal"

    private suspend inline fun <reified T> execute(block: () -> HttpResponse): Result<T> =
        try {
            val response = block()
            if (response.status.value in 200..299) Result.success(response.body<T>())
            else {
                val problem = runCatching {
                    response.body<FoodOutflowProblem>()
                }
                    .getOrElse {
                        if (it is CancellationException) throw it
                        null
                    }
                val error =
                    when (response.status.value) {
                        429 -> when (problem?.type) {
                            AI_QUOTA_EXHAUSTED_TYPE -> RecipeError.AiQuotaExhausted
                            RATE_LIMITED_TYPE -> RecipeError.RateLimited
                            else -> RecipeError.Network
                        }
                        503 -> if (problem?.type == AI_UNAVAILABLE_TYPE) RecipeError.AiUnavailable
                            else RecipeError.Network
                        401 -> RecipeError.Unauthorized
                        403 -> RecipeError.AccessDenied
                        404 -> RecipeError.NotFound
                        400,
                        422 -> RecipeError.InvalidInput
                        409 ->
                            when (problem?.type) {
                                "/errors/recipes/no-usable-stock" -> RecipeError.NoUsableStock
                                "/errors/recipes/already-chosen" -> RecipeError.AlreadyChosen
                                "/errors/recipes/idempotency-conflict" ->
                                    RecipeError.IdempotencyConflict
                                INSUFFICIENT_STOCK_TYPE ->
                                    if (
                                        problem.foodId != null &&
                                            problem.requested != null &&
                                            problem.available != null
                                    ) {
                                        RecipeError.InsufficientStock(
                                            problem.foodId,
                                            problem.requested,
                                            problem.available,
                                        )
                                    } else RecipeError.InvalidInput
                                else -> RecipeError.InvalidInput
                            }
                        502 ->
                            if (problem?.type == "/errors/recipes/generation-failed")
                                RecipeError.GenerationFailed
                            else RecipeError.Network
                        else -> RecipeError.Network
                    }
                Result.failure(RecipeFailure(error))
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(RecipeFailure(RecipeError.Network, error))
        }
}
