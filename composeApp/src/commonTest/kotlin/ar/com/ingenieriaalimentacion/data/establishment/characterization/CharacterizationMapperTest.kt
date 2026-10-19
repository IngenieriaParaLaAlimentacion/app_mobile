package ar.com.ingenieriaalimentacion.data.establishment.characterization

import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.EstablishmentCharacterizationDto
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.OpeningHoursSlotDto
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toCharacterizationStatus
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper.toDto
import ar.com.ingenieriaalimentacion.domain.model.establishment.Bathroom
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.FoodReceptionFrequency
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.OpeningHoursSlot
import ar.com.ingenieriaalimentacion.domain.model.establishment.Premises
import ar.com.ingenieriaalimentacion.domain.model.establishment.SpaceType
import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterizationMapperTest {

    private val full = EstablishmentCharacterization(
        district = "La Matanza",
        neighborhood = "San Justo",
        crossStreets = "Belgrano y Moreno",
        contactPhone = "+54 11 1234-5678",
        contactEmail = "comedor@example.com",
        openingHours = listOf(
            OpeningHoursSlot(setOf(Weekday.Monday, Weekday.Wednesday), "11:30", "14:00"),
        ),
        spaceTypes = setOf(SpaceType.CommunityKitchen, SpaceType.Other),
        spaceTypesOther = "Biblioteca popular",
        premises = Premises.LentSpace,
        bathroom = Bathroom.None,
        dailyAttendance = 120,
        meals = setOf(Meal.Lunch, Meal.Snack),
        foodReceptionFrequency = FoodReceptionFrequency.Weekly,
        cooksPerService = 4,
    )

    @Test
    fun `round trip keeps every answered field`() {
        assertEquals(full, full.toDto().toDomain())
    }

    @Test
    fun `options go out as wire values in catalog order`() {
        val dto = full.copy(meals = linkedSetOf(Meal.Snack, Meal.Lunch)).toDto()

        assertEquals(listOf("lunch", "snack"), dto.meals)
        assertEquals(listOf("community_kitchen", "other"), dto.spaceTypes)
        assertEquals("lent_space", dto.premises)
        assertEquals("none", dto.bathroom)
        assertEquals("weekly", dto.foodReceptionFrequency)
    }

    @Test
    fun `slot days go out in week order`() {
        val slot = OpeningHoursSlot(linkedSetOf(Weekday.Friday, Weekday.Monday), "09:00", "12:00")

        val dto = EstablishmentCharacterization(openingHours = listOf(slot)).toDto()

        assertEquals(listOf(OpeningHoursSlotDto(listOf("monday", "friday"), "09:00", "12:00")), dto.openingHours)
    }

    @Test
    fun `an empty profile still sends every list field as empty`() {
        val dto = EstablishmentCharacterization.EMPTY.toDto()

        assertEquals(emptyList(), dto.meals)
        assertEquals(emptyList(), dto.openingHours)
        assertNull(dto.premises)
    }

    @Test
    fun `null lists read as unanswered`() {
        assertEquals(EstablishmentCharacterization.EMPTY, EstablishmentCharacterizationDto().toDomain())
    }

    @Test
    fun `unknown wire values are dropped`() {
        val dto = EstablishmentCharacterizationDto(
            meals = listOf("lunch", "brunch"),
            premises = "boat",
            openingHours = listOf(OpeningHoursSlotDto(listOf("someday"), "09:00", "10:00")),
        )

        val domain = dto.toDomain()

        assertEquals(setOf(Meal.Lunch), domain.meals)
        assertNull(domain.premises)
        assertEquals(emptyList(), domain.openingHours)
    }

    @Test
    fun `status reads pending, complete and unknown`() {
        assertEquals(CharacterizationStatus.Pending, "pending".toCharacterizationStatus())
        assertEquals(CharacterizationStatus.Complete, "complete".toCharacterizationStatus())
        assertNull("partial".toCharacterizationStatus())
        assertNull(null.toCharacterizationStatus())
    }
}
