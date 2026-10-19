package ar.com.ingenieriaalimentacion.domain.model.establishment

import kotlin.test.Test
import kotlin.test.assertEquals

class EstablishmentCharacterizationTest {

    @Test
    fun `an empty profile is pending and any answer makes it complete`() {
        assertEquals(CharacterizationStatus.Pending, EstablishmentCharacterization.EMPTY.status)
        assertEquals(CharacterizationStatus.Complete, EstablishmentCharacterization(cooksPerService = 0).status)
    }

    @Test
    fun `a blank-only profile normalizes back to pending`() {
        val blank = EstablishmentCharacterization(district = "  ", contactEmail = "")

        assertEquals(CharacterizationStatus.Pending, blank.normalized().status)
    }

    @Test
    fun `Other text is kept for multi-select fields that include Other`() {
        val normalized = EstablishmentCharacterization(
            kitchenEquipment = setOf(KitchenEquipment.Oven, KitchenEquipment.Other),
            kitchenEquipmentOther = "Anafe",
            culturalIdentities = setOf(CulturalIdentity.Migrant),
            culturalIdentitiesOther = "Boliviana",
        ).normalized()

        assertEquals("Anafe", normalized.kitchenEquipmentOther)
        assertEquals(null, normalized.culturalIdentitiesOther)
    }
}
