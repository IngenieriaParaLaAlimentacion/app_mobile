package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodMovementActivityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * `/me/activity?type=food_intake` and `?type=food_outflow` — same URL as
 * [ar.com.ingenieriaalimentacion.data.activity.ActivityApi.list], deserialized into the independent
 * [PaginatedFoodMovementActivityResponse] shape so neither parsing path needs to know about the
 * other's item types.
 *
 * Two calls rather than one unfiltered call on purpose: an unfiltered feed would force this DTO to
 * model subject/measurement/survey_response items too, and — more importantly — the section needs
 * each direction's own `total_pages` to merge the two streams without gaps (see the repository).
 */
class FoodMovementActivityApi(private val client: HttpClient) {

    suspend fun listIntakes(page: Int, pageSize: Int) = list(page, pageSize, TYPE_INTAKE)

    suspend fun listOutflows(page: Int, pageSize: Int) = list(page, pageSize, TYPE_OUTFLOW)

    private suspend fun list(
        page: Int,
        pageSize: Int,
        type: String,
    ): PaginatedFoodMovementActivityResponse {
        val response = client.get("api/v1/users/me/activity") {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("type", type)
        }
        if (!response.status.isSuccess()) {
            error("food movement activity list failed ($type): ${response.status.value}")
        }
        return response.body()
    }

    private companion object {
        const val TYPE_INTAKE = "food_intake"
        const val TYPE_OUTFLOW = "food_outflow"
    }
}
