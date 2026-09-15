package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import kotlinx.coroutines.flow.Flow

/** The reactive floor for both the Alimentos tab and Home's "Actividad reciente". */
class ObserveFoodMovementsUseCase(private val repository: FoodMovementHistoryRepository) {
    operator fun invoke(): Flow<List<FoodMovement>> = repository.observeLocalMerged()
}
