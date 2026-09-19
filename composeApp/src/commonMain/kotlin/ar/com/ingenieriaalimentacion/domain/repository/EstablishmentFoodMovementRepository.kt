package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage

/**
 * Remote-only establishment-wide history. There is no cache or offline fallback.
 * This is intentionally separate from the caller-scoped [FoodMovementHistoryRepository].
 */
interface EstablishmentFoodMovementRepository {
    suspend fun getPage(
        establishmentId: String,
        filters: EstablishmentFoodMovementFilters,
        page: Int,
        pageSize: Int,
    ): Result<EstablishmentFoodMovementPage>

    suspend fun getDetail(
        establishmentId: String,
        movementId: String,
    ): Result<EstablishmentFoodMovementDetail>
}
