package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationOption
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.OpeningHoursSlot
import ar.com.ingenieriaalimentacion.domain.model.establishment.fromWireValue
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationMultiField as Multi
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationSingleField as Single
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationTextField as TextField

/**
 * Pure reducer of the ficha form, shared by every screen that embeds it. Validators mirror the
 * backend's (`EstablishmentCharacterization` in backend#196), so a well-formed form never 422s.
 */
object CharacterizationFormReducer {

    private const val TEXT_MAX = 100
    private const val PHONE_MAX = 30
    private const val NUMBER_MAX_DIGITS = 6
    private val PHONE = Regex("^[0-9+\\-\\s()]+$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val TIME = Regex("^([01]\\d|2[0-3])[0-5]\\d$")

    private val textRule = Rules.maxLength(TEXT_MAX, CharacterizationCopy.TEXT_MAX_LENGTH)
    private val timeValidator: Validator = Rules.matches(TIME, CharacterizationCopy.INVALID_TIME)

    private fun validator(field: TextField): Validator = when (field) {
        TextField.ContactPhone -> Rules.of(
            Rules.maxLength(PHONE_MAX, CharacterizationCopy.PHONE_MAX_LENGTH),
            Rules.matches(PHONE, CharacterizationCopy.INVALID_PHONE),
        )
        TextField.ContactEmail -> Rules.matches(EMAIL, CharacterizationCopy.INVALID_EMAIL)
        TextField.DailyAttendance ->
            Rules.digitsInRange(0..100_000, CharacterizationCopy.wholeNumberUpTo("100.000"))
        TextField.CooksPerService ->
            Rules.digitsInRange(0..1_000, CharacterizationCopy.wholeNumberUpTo("1.000"))
        else -> textRule
    }

    fun reduce(state: CharacterizationFormState, action: CharacterizationFormAction): CharacterizationFormState =
        when (action) {
            is CharacterizationFormAction.TextChanged -> {
                val value = if (action.field.isNumeric) {
                    action.value.filter(Char::isDigit).take(NUMBER_MAX_DIGITS)
                } else {
                    action.value
                }
                state.updateText(action.field) { it.onChange(value, validator(action.field)) }
                    .clearServerError(action.field.wireName)
            }

            is CharacterizationFormAction.TextFocusChanged ->
                state.updateText(action.field) { it.onFocus(action.focused, validator(action.field)) }

            is CharacterizationFormAction.OptionToggled -> {
                val current = state.selected(action.field)
                val next = if (action.wireValue in current) current - action.wireValue else current + action.wireValue
                state.copy(multi = state.multi + (action.field to next))
                    .clearServerError(action.field.wireName)
                    .clearServerError(action.field.other?.wireName)
            }

            is CharacterizationFormAction.OptionSelected -> {
                val single = if (state.selected(action.field) == action.wireValue) {
                    state.single - action.field
                } else {
                    state.single + (action.field to action.wireValue)
                }
                state.copy(single = single)
                    .clearServerError(action.field.wireName)
                    .clearServerError(action.field.other?.wireName)
            }

            CharacterizationFormAction.SlotAdded -> state.copy(
                openingHours = state.openingHours + OpeningHoursSlotState(id = state.nextSlotId),
                nextSlotId = state.nextSlotId + 1,
            )

            is CharacterizationFormAction.SlotRemoved -> state.copy(
                openingHours = state.openingHours.filterNot { it.id == action.slotId },
            ).clearServerError(OPENING_HOURS)

            is CharacterizationFormAction.SlotDayToggled -> state.updateSlot(action.slotId) { slot ->
                val days = if (action.day in slot.days) slot.days - action.day else slot.days + action.day
                slot.copy(days = days)
            }.clearServerError(OPENING_HOURS)

            is CharacterizationFormAction.SlotTimeChanged -> state.updateSlot(action.slotId) { slot ->
                val digits = action.value.filter(Char::isDigit).take(4)
                slot.updateTime(action.bound) { it.onChange(digits, timeValidator) }
            }.clearServerError(OPENING_HOURS)

            is CharacterizationFormAction.SlotTimeFocusChanged -> state.updateSlot(action.slotId) { slot ->
                slot.updateTime(action.bound) { it.onFocus(action.focused, timeValidator) }
            }
        }

    /** Submit: re-validate and force every error to show. */
    fun showAllErrors(state: CharacterizationFormState): CharacterizationFormState = state.copy(
        texts = state.texts.mapValues { (field, value) -> value.showError(validator(field)) },
        openingHours = state.openingHours.map { slot ->
            slot.copy(
                opensAt = slot.opensAt.showError(timeValidator),
                closesAt = slot.closesAt.showError(timeValidator),
                forceShow = true,
            )
        },
    )

    /**
     * Flags the field a 422 named. Accepts the request flow's nested path
     * (`characterization.premises_other`) as well as the bare one.
     */
    fun withServerError(state: CharacterizationFormState, field: String?): CharacterizationFormState =
        state.copy(serverInvalidField = field?.removePrefix(NESTED_PREFIX))

    /** Prefills the form from a stored profile. */
    fun from(characterization: EstablishmentCharacterization): CharacterizationFormState {
        val c = characterization
        val texts = mapOf(
            TextField.District to c.district,
            TextField.Neighborhood to c.neighborhood,
            TextField.CrossStreets to c.crossStreets,
            TextField.ContactPhone to c.contactPhone,
            TextField.ContactEmail to c.contactEmail,
            TextField.DailyAttendance to c.dailyAttendance?.toString(),
            TextField.CooksPerService to c.cooksPerService?.toString(),
            TextField.SpaceTypesOther to c.spaceTypesOther,
            TextField.CareTasksOther to c.careTasksOther,
            TextField.PremisesOther to c.premisesOther,
            TextField.BathroomOther to c.bathroomOther,
            TextField.AffiliationsOther to c.affiliationsOther,
            TextField.ServiceTypesOther to c.serviceTypesOther,
            TextField.KitchenEquipmentOther to c.kitchenEquipmentOther,
            TextField.CulturalIdentitiesOther to c.culturalIdentitiesOther,
        ).mapNotNull { (field, value) ->
            value?.let { field to TextFieldState(value = it, error = validator(field)(it)) }
        }.toMap()
        val multi = mapOf(
            Multi.SpaceTypes to c.spaceTypes,
            Multi.CareTasks to c.careTasks,
            Multi.Utilities to c.utilities,
            Multi.Affiliations to c.affiliations,
            Multi.AttendeeAges to c.attendeeAges,
            Multi.Meals to c.meals,
            Multi.ServiceTypes to c.serviceTypes,
            Multi.KitchenEquipment to c.kitchenEquipment,
            Multi.CulturalIdentities to c.culturalIdentities,
        ).mapValues { (_, options) -> options.mapTo(linkedSetOf()) { it.wireValue } }
            .filterValues { it.isNotEmpty() }
        val single = listOfNotNull(
            c.premises?.let { Single.Premises to it.wireValue },
            c.bathroom?.let { Single.Bathroom to it.wireValue },
            c.foodReceptionFrequency?.let { Single.FoodReceptionFrequency to it.wireValue },
        ).toMap()
        val slots = c.openingHours.mapIndexed { index, slot ->
            OpeningHoursSlotState(
                id = index,
                days = slot.days,
                opensAt = slot.opensAt.toTimeField(),
                closesAt = slot.closesAt.toTimeField(),
            )
        }
        return CharacterizationFormState(
            texts = texts,
            multi = multi,
            single = single,
            openingHours = slots,
            nextSlotId = slots.size,
        )
    }

    /**
     * Builds the profile to save. Call only once [CharacterizationFormState.isValid]; untouched
     * hour rows are dropped. Trimming and dropping orphan "Otro" texts is left to
     * [EstablishmentCharacterization.normalized], which the save use cases apply.
     */
    fun toCharacterization(state: CharacterizationFormState): EstablishmentCharacterization {
        fun text(field: TextField): String? = state.text(field).value.takeIf { it.isNotBlank() }
        return EstablishmentCharacterization(
            district = text(TextField.District),
            neighborhood = text(TextField.Neighborhood),
            crossStreets = text(TextField.CrossStreets),
            contactPhone = text(TextField.ContactPhone),
            contactEmail = text(TextField.ContactEmail),
            openingHours = state.openingHours.filterNot { it.isBlank }.map { slot ->
                OpeningHoursSlot(slot.days, slot.opensAt.value.toWireTime(), slot.closesAt.value.toWireTime())
            },
            spaceTypes = state.typed(Multi.SpaceTypes),
            spaceTypesOther = text(TextField.SpaceTypesOther),
            careTasks = state.typed(Multi.CareTasks),
            careTasksOther = text(TextField.CareTasksOther),
            premises = fromWireValue(state.selected(Single.Premises)),
            premisesOther = text(TextField.PremisesOther),
            bathroom = fromWireValue(state.selected(Single.Bathroom)),
            bathroomOther = text(TextField.BathroomOther),
            utilities = state.typed(Multi.Utilities),
            affiliations = state.typed(Multi.Affiliations),
            affiliationsOther = text(TextField.AffiliationsOther),
            dailyAttendance = text(TextField.DailyAttendance)?.toIntOrNull(),
            attendeeAges = state.typed(Multi.AttendeeAges),
            meals = state.typed(Multi.Meals),
            serviceTypes = state.typed(Multi.ServiceTypes),
            serviceTypesOther = text(TextField.ServiceTypesOther),
            foodReceptionFrequency = fromWireValue(state.selected(Single.FoodReceptionFrequency)),
            cooksPerService = text(TextField.CooksPerService)?.toIntOrNull(),
            kitchenEquipment = state.typed(Multi.KitchenEquipment),
            kitchenEquipmentOther = text(TextField.KitchenEquipmentOther),
            culturalIdentities = state.typed(Multi.CulturalIdentities),
            culturalIdentitiesOther = text(TextField.CulturalIdentitiesOther),
        )
    }

    const val OPENING_HOURS = "opening_hours"
    private const val NESTED_PREFIX = "characterization."

    private inline fun <reified T> CharacterizationFormState.typed(field: Multi): Set<T>
        where T : Enum<T>, T : CharacterizationOption =
        selected(field).mapNotNullTo(linkedSetOf()) { fromWireValue<T>(it) }

    private fun CharacterizationFormState.updateText(
        field: TextField,
        transform: (TextFieldState) -> TextFieldState,
    ) = copy(texts = texts + (field to transform(text(field))))

    private fun CharacterizationFormState.updateSlot(
        id: Int,
        transform: (OpeningHoursSlotState) -> OpeningHoursSlotState,
    ) = copy(openingHours = openingHours.map { if (it.id == id) transform(it) else it })

    private fun CharacterizationFormState.clearServerError(wireName: String?) =
        if (wireName != null && serverInvalidField == wireName) copy(serverInvalidField = null) else this

    private fun OpeningHoursSlotState.updateTime(
        bound: SlotBound,
        transform: (TextFieldState) -> TextFieldState,
    ) = when (bound) {
        SlotBound.Opens -> copy(opensAt = transform(opensAt))
        SlotBound.Closes -> copy(closesAt = transform(closesAt))
    }

    private fun String.toTimeField(): TextFieldState {
        val digits = filter(Char::isDigit)
        return TextFieldState(value = digits, error = timeValidator(digits))
    }

    private fun String.toWireTime(): String = "${take(2)}:${drop(2)}"
}
