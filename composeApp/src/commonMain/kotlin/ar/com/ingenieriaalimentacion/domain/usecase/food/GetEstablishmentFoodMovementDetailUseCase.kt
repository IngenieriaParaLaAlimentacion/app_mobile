package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentFoodMovementRepository

class GetEstablishmentFoodMovementDetailUseCase(
    private val repository: EstablishmentFoodMovementRepository,
) {
    suspend operator fun invoke(
        establishmentId: String,
        movementId: String,
    ): Result<EstablishmentFoodMovementDetail> = repository.getDetail(establishmentId, movementId)
}
