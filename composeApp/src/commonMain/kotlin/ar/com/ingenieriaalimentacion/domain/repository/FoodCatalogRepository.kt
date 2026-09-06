package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult

/** Online-only read of the merged food catalog. There is no local cache: the typeahead is a live
 *  call, and the screen refuses to work offline. */
interface FoodCatalogRepository {
    suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult>
}
