package ar.com.ingenieriaalimentacion.data.food

import ar.com.ingenieriaalimentacion.data.food.dto.PaginatedFoodsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

/**
 * Read-only food-catalog endpoint, on the default authed [HttpClient] (no Koin qualifier).
 *
 * Always requests page 1 — the typeahead does not paginate. `search` is `ILIKE %term%` on the name
 * server-side with no `unaccent` and no relevance ranking, which is why the caller shows the total
 * and asks for a narrower query instead of a "load more".
 */
class FoodCatalogApi(private val client: HttpClient) {

    suspend fun searchFoods(search: String?, pageSize: Int): PaginatedFoodsResponse {
        val response = client.get("api/v1/food-catalog/foods") {
            parameter("page", 1)
            parameter("page_size", pageSize)
            parameter("sort_order", "name_asc")
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
        }
        if (!response.status.isSuccess()) error("food search failed: ${response.status.value}")
        return response.body()
    }
}
