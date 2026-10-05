package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.ReceiptProposalDto
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptLine
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptProposal
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource

internal fun ReceiptProposalDto.toDomain() = ReceiptProposal(
    establishmentId = establishmentId,
    lines = lines.map { line ->
        ReceiptLine(
            description = line.description,
            matchedFoodId = line.food?.id,
            proposedQuantity = line.quantity?.content,
            proposedUnit = when (line.unit) {
                "unidades" -> FoodUnit.Unidades
                "kilogramos" -> FoodUnit.Kilogramos
                "litros" -> FoodUnit.Litros
                else -> null
            },
            rawQuantity = line.rawQuantity?.content,
            rawUnitText = line.rawUnitText,
            needsReview = line.needsReview,
        )
    },
    message = message,
)

internal fun ReceiptSource.wireValue() = when (this) {
    ReceiptSource.Photo -> "photo"
    ReceiptSource.ManualFallback -> "manual"
}
