package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodMovementActivityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * `/me/activity?type=food_intake&type=food_outflow` — **one** request returning both directions
 * already merged and ordered by the server. Same URL as
 * [ar.com.ingenieriaalimentacion.data.activity.ActivityApi.list], deserialized into the independent
 * [PaginatedFoodMovementActivityResponse] shape so neither parsing path needs to know about the
 * other's item types.
 *
 * Filtered rather than unfiltered on purpose: an unfiltered feed would force this DTO to model
 * subject/measurement/survey_response items too. The repeated `type` is not an unfiltered feed — it
 * is an exact two-type filter, so the envelope's `total`/`total_pages` describe the merged food feed
 * and nothing else.
 *
 * `parameter(name, value)` is `url.parameters.append(...)`: calling it twice **appends**, it does not
 * overwrite. That is what produces the repeated form the backend expects.
 *
 * Requires a backend that accepts a repeated `type`. An older backend keeps only the LAST value and
 * would silently return salidas only — see the rollout note in the plan.
 */
class FoodMovementActivityApi(private val client: HttpClient) {

    suspend fun list(page: Int, pageSize: Int): PaginatedFoodMovementActivityResponse {
        val response = client.get("api/v1/users/me/activity") {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("type", TYPE_INTAKE)
            parameter("type", TYPE_OUTFLOW)
        }
        if (!response.status.isSuccess()) {
            error("food movement activity list failed: ${response.status.value}")
        }
        return response.body()
    }

    private companion object {
        const val TYPE_INTAKE = "food_intake"
        const val TYPE_OUTFLOW = "food_outflow"
    }
}
