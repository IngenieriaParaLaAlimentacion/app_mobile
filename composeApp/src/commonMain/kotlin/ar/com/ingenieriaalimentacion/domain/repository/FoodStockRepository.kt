package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockPage

/**
 * Online-only read of an establishment's materialized stock balance. There is no local cache and no
 * offline queue — same posture as [FoodCatalogRepository], and deliberate: offline, the
 * establishment still resolves from SQLDelight but the list fails into the screen's retry surface,
 * so the user is never shown an empty stock that reads as "no hay nada".
 *
 * A failed [Result] carries a
 * [ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure] with a typed
 * [ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError].
 */
interface FoodStockRepository {
    suspend fun getStockPage(
        establishmentId: String,
        filters: FoodStockFilters,
        page: Int,
        pageSize: Int,
    ): Result<FoodStockPage>
}
