package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import ar.com.ingenieriaalimentacion.domain.model.establishment.Bathroom
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.FoodReceptionFrequency
import ar.com.ingenieriaalimentacion.domain.model.establishment.KitchenEquipment
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.OpeningHoursSlot
import ar.com.ingenieriaalimentacion.domain.model.establishment.Premises
import ar.com.ingenieriaalimentacion.domain.model.establishment.SpaceType
import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormAction as A
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationMultiField as Multi
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationSingleField as Single
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationTextField as TextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterizationFormReducerTest {

    private fun CharacterizationFormState.apply(vararg actions: A) =
        actions.fold(this) { state, action -> CharacterizationFormReducer.reduce(state, action) }

    private fun typed(field: TextField, value: String) =
        arrayOf(A.TextFocusChanged(field, true), A.TextChanged(field, value), A.TextFocusChanged(field, false))

    @Test
    fun `an untouched form is valid and builds an empty profile`() {
        val state = CharacterizationFormState()

        assertTrue(state.isValid)
        assertEquals(EstablishmentCharacterization.EMPTY, CharacterizationFormReducer.toCharacterization(state))
    }

    @Test
    fun `errors stay hidden while typing and show after blur`() {
        val editing = CharacterizationFormState().apply(
            A.TextFocusChanged(TextField.ContactEmail, true),
            A.TextChanged(TextField.ContactEmail, "ana@"),
        )
        assertNull(editing.displayError(TextField.ContactEmail))
        assertFalse(editing.isValid)

        val blurred = editing.apply(A.TextFocusChanged(TextField.ContactEmail, false))
        assertEquals(CharacterizationCopy.INVALID_EMAIL, blurred.displayError(TextField.ContactEmail))
    }

    @Test
    fun `phone, email and text length follow the backend rules`() {
        val ok = CharacterizationFormState().apply(
            *typed(TextField.ContactPhone, "+54 (11) 1234-5678"),
            *typed(TextField.ContactEmail, "comedor@example.com"),
            *typed(TextField.District, "a".repeat(100)),
        )
        assertTrue(ok.isValid)

        assertEquals(
            CharacterizationCopy.INVALID_PHONE,
            ok.apply(*typed(TextField.ContactPhone, "11-1234 int. 5")).displayError(TextField.ContactPhone),
        )
        assertEquals(
            CharacterizationCopy.TEXT_MAX_LENGTH,
            ok.apply(*typed(TextField.District, "a".repeat(101))).displayError(TextField.District),
        )
    }

    @Test
    fun `numeric fields keep digits only and enforce their range`() {
        val state = CharacterizationFormState().apply(
            *typed(TextField.DailyAttendance, "1.200"),
            *typed(TextField.CooksPerService, "1001"),
        )

        assertEquals("1200", state.text(TextField.DailyAttendance).value)
        assertNull(state.displayError(TextField.DailyAttendance))
        assertEquals(CharacterizationCopy.wholeNumberUpTo("1.000"), state.displayError(TextField.CooksPerService))
    }

    @Test
    fun `tapping the selected single option clears it`() {
        val selected = CharacterizationFormState().apply(A.OptionSelected(Single.Premises, "street"))
        assertEquals("street", selected.selected(Single.Premises))

        val cleared = selected.apply(A.OptionSelected(Single.Premises, "street"))
        assertNull(cleared.selected(Single.Premises))
    }

    @Test
    fun `Other text shows only with Other chosen and is kept across toggles`() {
        val withOther = CharacterizationFormState().apply(
            A.OptionToggled(Multi.KitchenEquipment, "other"),
            *typed(TextField.KitchenEquipmentOther, "Anafe"),
        )
        assertTrue(withOther.showsOther(Multi.KitchenEquipment))

        val toggledOff = withOther.apply(A.OptionToggled(Multi.KitchenEquipment, "other"))
        assertFalse(toggledOff.showsOther(Multi.KitchenEquipment))

        val back = toggledOff.apply(A.OptionToggled(Multi.KitchenEquipment, "other"))
        assertEquals("Anafe", back.text(TextField.KitchenEquipmentOther).value)
    }

    @Test
    fun `an invalid hidden Other text does not block the save`() {
        val state = CharacterizationFormState().apply(
            A.OptionSelected(Single.Bathroom, "other"),
            *typed(TextField.BathroomOther, "a".repeat(101)),
            A.OptionSelected(Single.Bathroom, "none"),
        )

        assertTrue(state.isValid)
    }

    @Test
    fun `a slot needs days and both times, closing after opening`() {
        val added = CharacterizationFormState().apply(A.SlotAdded)
        val id = added.openingHours.single().id
        assertTrue(added.isValid) // untouched rows are dropped, not rejected

        val noDays = added.apply(A.SlotTimeChanged(id, SlotBound.Opens, "0900"))
        assertEquals(CharacterizationCopy.DAYS_REQUIRED, noDays.openingHours.single().error)

        val backwards = noDays.apply(
            A.SlotDayToggled(id, Weekday.Monday),
            A.SlotTimeChanged(id, SlotBound.Closes, "0800"),
        )
        assertEquals(CharacterizationCopy.CLOSES_BEFORE_OPENS, backwards.openingHours.single().error)
        assertFalse(backwards.isValid)

        val bad = backwards.apply(A.SlotTimeChanged(id, SlotBound.Closes, "2460"))
        assertEquals(CharacterizationCopy.INVALID_TIME, bad.openingHours.single().closesAt.error)
    }

    @Test
    fun `slot errors wait for submit or both times blurred`() {
        val state = CharacterizationFormState().apply(A.SlotAdded, A.SlotTimeChanged(0, SlotBound.Opens, "0900"))
        assertNull(state.openingHours.single().displayError)

        val submitted = CharacterizationFormReducer.showAllErrors(state)
        assertEquals(CharacterizationCopy.DAYS_REQUIRED, submitted.openingHours.single().displayError)
    }

    @Test
    fun `removing a slot keeps the other slots' ids`() {
        val state = CharacterizationFormState().apply(A.SlotAdded, A.SlotAdded, A.SlotAdded, A.SlotRemoved(1))

        assertEquals(listOf(0, 2), state.openingHours.map { it.id })
        assertEquals(3, state.apply(A.SlotAdded).openingHours.last().id)
    }

    @Test
    fun `server field error flags the field and clears once it changes`() {
        val flagged = CharacterizationFormReducer.withServerError(
            CharacterizationFormState(),
            "characterization.premises_other",
        )
        assertEquals(CharacterizationCopy.SERVER_INVALID, flagged.displayError(TextField.PremisesOther))

        val edited = flagged.apply(A.TextChanged(TextField.PremisesOther, "Galpón"))
        assertNull(edited.displayError(TextField.PremisesOther))
    }

    @Test
    fun `server error on an option group clears when the group changes`() {
        val flagged = CharacterizationFormReducer.withServerError(CharacterizationFormState(), "meals")
        assertEquals(CharacterizationCopy.SERVER_INVALID, flagged.serverError("meals"))

        assertNull(flagged.apply(A.OptionToggled(Multi.Meals, "lunch")).serverError("meals"))
    }

    @Test
    fun `prefill then build returns the same profile`() {
        val profile = EstablishmentCharacterization(
            district = "La Matanza",
            contactPhone = "11 1234-5678",
            openingHours = listOf(OpeningHoursSlot(setOf(Weekday.Monday, Weekday.Friday), "11:30", "14:00")),
            spaceTypes = setOf(SpaceType.CommunityKitchen, SpaceType.Other),
            spaceTypesOther = "Biblioteca",
            premises = Premises.RentedSpace,
            bathroom = Bathroom.Exclusive,
            dailyAttendance = 80,
            meals = setOf(Meal.Breakfast, Meal.Dinner),
            foodReceptionFrequency = FoodReceptionFrequency.Monthly,
            cooksPerService = 0,
            kitchenEquipment = setOf(KitchenEquipment.Freezer),
        )

        val state = CharacterizationFormReducer.from(profile)

        assertTrue(state.isValid)
        assertEquals("1130", state.openingHours.single().opensAt.value)
        assertEquals(profile, CharacterizationFormReducer.toCharacterization(state))
    }
}
