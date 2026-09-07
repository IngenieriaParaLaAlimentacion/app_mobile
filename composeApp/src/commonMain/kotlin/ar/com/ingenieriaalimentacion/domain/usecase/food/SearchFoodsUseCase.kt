package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository

/**
 * Live catalog search for the typeahead. The page size is fixed here rather than at the call site:
 * the dropdown never paginates, so "how many suggestions" is a property of the feature, not of the
 * screen's scroll position.
 */
class SearchFoodsUseCase(private val repository: FoodCatalogRepository) {
    suspend operator fun invoke(search: String?): Result<FoodSearchResult> =
        repository.searchFoods(search, pageSize = SUGGESTION_PAGE_SIZE)

    companion object {
        const val SUGGESTION_PAGE_SIZE = 8
    }
}
