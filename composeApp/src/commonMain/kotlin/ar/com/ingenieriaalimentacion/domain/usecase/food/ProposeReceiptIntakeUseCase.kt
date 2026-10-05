package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.repository.ReceiptIntakeRepository

class ProposeReceiptIntakeUseCase(private val repository: ReceiptIntakeRepository) {
    suspend operator fun invoke(establishmentId: String, photo: ReceiptPhoto) =
        repository.propose(establishmentId, photo)
}
