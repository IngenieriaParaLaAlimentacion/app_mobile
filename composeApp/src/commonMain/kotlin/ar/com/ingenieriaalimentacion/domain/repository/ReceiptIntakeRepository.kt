package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptProposal
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource

interface ReceiptIntakeRepository {
    suspend fun propose(establishmentId: String, photo: ReceiptPhoto): Result<ReceiptProposal>
    suspend fun confirm(intake: NewFoodIntake, source: ReceiptSource, photo: ReceiptPhoto): Result<Unit>
}
