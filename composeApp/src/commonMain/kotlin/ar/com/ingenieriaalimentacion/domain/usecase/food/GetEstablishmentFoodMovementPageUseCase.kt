package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentFoodMovementRepository

class GetEstablishmentFoodMovementPageUseCase(
    private val repository: EstablishmentFoodMovementRepository,
) {
    suspend operator fun invoke(
        establishmentId: String,
        filters: EstablishmentFoodMovementFilters,
        page: Int,
        pageSize: Int,
    ): Result<EstablishmentFoodMovementPage> =
        repository.getPage(establishmentId, filters, page, pageSize)
}
