package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.FoodCategory
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult

/** Online-only read of the merged food catalog. There is no local cache: the typeahead is a live
 *  call, and the screen refuses to work offline. */
interface FoodCatalogRepository {
    suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult>
    suspend fun getFood(id: String): Result<Food>

    /** The 24 SARA 2 food groups, unpaginated — the endpoint returns a bare JSON array, not the
     *  house envelope. Best-effort for callers: a failure disables a filter, it never blocks a list. */
    suspend fun getCategories(): Result<List<FoodCategory>>
}
