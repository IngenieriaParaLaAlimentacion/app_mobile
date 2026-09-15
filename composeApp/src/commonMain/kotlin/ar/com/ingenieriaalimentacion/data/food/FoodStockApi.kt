package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.ESTABLISHMENT_NOT_FOUND_TYPE
import ar.com.ingenieriaalimentacion.data.food.dto.FoodIntakeProblem
import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodStockResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

/**
 * `GET /api/v1/establishments/{id}/stock` — the materialized balance listing.
 *
 * Same status-branching convention as [FoodIntakeApi]: the authed client does not set
 * `expectSuccess`, so Ktor never throws on a non-2xx and the status is branched explicitly. The
 * richer tier (problem+json `type` discrimination) is used rather than [FoodCatalogApi]'s thin
 * `error()`, because 403 and 404 drive different UI here — one re-opens the establishment picker,
 * the other is terminal — and both must stay distinct from a retryable transport failure.
 *
 * Query params are omitted when absent rather than sent empty: the backend's `only_available`
 * default is `false` and its `category_id` default is "no filter", and an empty string would be a
 * 422, not a no-op.
 *
 * `FoodIntakeProblem` is reused as the problem+json view — it is nothing but `{type}` and the
 * `/errors/establishment-not-found` constant is shared. (Renaming it to `FoodProblem` is a tidy-up
 * for another PR; it would touch `FoodIntakeApi` for no behaviour change.)
 */
class FoodStockApi(private val client: HttpClient) {

    suspend fun listStock(
        establishmentId: String,
        onlyAvailable: Boolean,
        categoryId: String?,
        sortOrder: String,
        page: Int,
        pageSize: Int,
    ): FoodStockPageOutcome = runCatching {
        val response = client.get("api/v1/establishments/$establishmentId/stock") {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("sort_order", sortOrder)
            if (onlyAvailable) parameter("only_available", true)
            categoryId?.takeIf { it.isNotBlank() }?.let { parameter("category_id", it) }
        }
        when (response.status.value) {
            200 -> FoodStockPageOutcome.Loaded(response.body<PaginatedFoodStockResponse>())
            400, 422 -> FoodStockPageOutcome.InvalidRequest
            401 -> FoodStockPageOutcome.Unauthorized
            403 -> FoodStockPageOutcome.EstablishmentAccessDenied
            404 -> when (response.problemType()) {
                ESTABLISHMENT_NOT_FOUND_TYPE -> FoodStockPageOutcome.EstablishmentNotFound
                // A 404 this build doesn't recognize is still terminal for this URL.
                else -> FoodStockPageOutcome.InvalidRequest
            }
            // 5xx, and 503 `/errors/concurrent-update` (which ships a Retry-After) — the user's
            // retry button is the backoff.
            else -> FoodStockPageOutcome.Retryable(null)
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        FoodStockPageOutcome.Retryable(t)
    }

    private suspend fun HttpResponse.problemType(): String? =
        runCatching { body<FoodIntakeProblem>() }.getOrNull()?.type
}

sealed interface FoodStockPageOutcome {
    data class Loaded(val response: PaginatedFoodStockResponse) : FoodStockPageOutcome
    data object EstablishmentAccessDenied : FoodStockPageOutcome
    data object EstablishmentNotFound : FoodStockPageOutcome
    data object InvalidRequest : FoodStockPageOutcome
    data object Unauthorized : FoodStockPageOutcome
    data class Retryable(val cause: Throwable?) : FoodStockPageOutcome
}
