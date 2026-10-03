package ar.com.ingenieriaalimentacion.domain.usecase.food

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource
import ar.com.ingenieriaalimentacion.domain.repository.ReceiptIntakeRepository

class ConfirmReceiptIntakeUseCase(private val repository: ReceiptIntakeRepository) {
    suspend operator fun invoke(intake: NewFoodIntake, source: ReceiptSource, photo: ReceiptPhoto) =
        repository.confirm(intake, source, photo)
}
