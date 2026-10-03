package ar.com.ingenieriaalimentacion.data.food.mapper

import ar.com.ingenieriaalimentacion.data.food.dto.ReceiptProposalDto
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptIntakeMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun preservesPrintedDecimalAndMatchedId() {
        val dto = json.decodeFromString<ReceiptProposalDto>("""{
          "establishment_id":"e1", "lines":[{
            "description":"Arroz 0.500 g", "food":{"id":"f1","name":"Arroz"},
            "quantity":null, "unit":null, "raw_quantity":0.500,
            "raw_unit_text":"g", "needs_review":true
          }], "message":null
        }""")
        val line = dto.toDomain().lines.single()
        assertEquals("f1", line.matchedFoodId)
        assertEquals("0.500", line.rawQuantity)
        assertNull(line.proposedQuantity)
        assertEquals("g", line.rawUnitText)
    }

    @Test fun unknownNormalizedUnitNeverBecomesKilograms() {
        val dto = json.decodeFromString<ReceiptProposalDto>("""{
          "establishment_id":"e1", "lines":[{
            "description":"Aceite", "quantity":1.250, "unit":"cajas",
            "raw_quantity":null, "raw_unit_text":null, "needs_review":true
          }]
        }""")
        val line = dto.toDomain().lines.single()
        assertEquals("1.250", line.proposedQuantity)
        assertNull(line.proposedUnit)
    }

    @Test fun mapsKnownUnit() {
        val dto = json.decodeFromString<ReceiptProposalDto>("""{
          "establishment_id":"e1", "lines":[{
            "description":"Leche", "unit":"litros", "needs_review":true
          }]
        }""")
        assertEquals(FoodUnit.Litros, dto.toDomain().lines.single().proposedUnit)
    }
}
