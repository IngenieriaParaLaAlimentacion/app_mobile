package ar.com.ingenieriaalimentacion.domain.model.establishment

/**
 * An establishment's characterization profile ("ficha"). Every field is optional: `null` or an
 * empty collection means unanswered. Each `*Other` text belongs to the field before it and is only
 * meaningful while that field holds the "Otro" option — [normalized] drops it otherwise.
 *
 * The backend replaces the profile as a whole, so a save must always carry every field.
 */
data class EstablishmentCharacterization(
    val district: String? = null,
    val neighborhood: String? = null,
    val crossStreets: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val openingHours: List<OpeningHoursSlot> = emptyList(),

    val spaceTypes: Set<SpaceType> = emptySet(),
    val spaceTypesOther: String? = null,
    val careTasks: Set<CareTask> = emptySet(),
    val careTasksOther: String? = null,
    val premises: Premises? = null,
    val premisesOther: String? = null,
    val bathroom: Bathroom? = null,
    val bathroomOther: String? = null,
    val utilities: Set<Utility> = emptySet(),
    val affiliations: Set<Affiliation> = emptySet(),
    val affiliationsOther: String? = null,

    val dailyAttendance: Int? = null,
    val attendeeAges: Set<AttendeeAge> = emptySet(),

    val meals: Set<Meal> = emptySet(),
    val serviceTypes: Set<ServiceType> = emptySet(),
    val serviceTypesOther: String? = null,
    val foodReceptionFrequency: FoodReceptionFrequency? = null,
    val cooksPerService: Int? = null,
    val kitchenEquipment: Set<KitchenEquipment> = emptySet(),
    val kitchenEquipmentOther: String? = null,

    val culturalIdentities: Set<CulturalIdentity> = emptySet(),
    val culturalIdentitiesOther: String? = null,
) {
    /** Same rule as the backend's computed `characterization_status`. */
    val status: CharacterizationStatus
        get() = if (this == EMPTY) CharacterizationStatus.Pending else CharacterizationStatus.Complete

    /**
     * Trims every text, reads blank text as unanswered and drops each `*Other` text whose field
     * does not hold "Otro" — the backend rejects that combination with a 422.
     */
    fun normalized(): EstablishmentCharacterization = copy(
        district = district.clean(),
        neighborhood = neighborhood.clean(),
        crossStreets = crossStreets.clean(),
        contactPhone = contactPhone.clean(),
        contactEmail = contactEmail.clean(),
        spaceTypesOther = spaceTypesOther.cleanOther(spaceTypes),
        careTasksOther = careTasksOther.cleanOther(careTasks),
        premisesOther = premisesOther.cleanOther(setOfNotNull(premises)),
        bathroomOther = bathroomOther.cleanOther(setOfNotNull(bathroom)),
        affiliationsOther = affiliationsOther.cleanOther(affiliations),
        serviceTypesOther = serviceTypesOther.cleanOther(serviceTypes),
        kitchenEquipmentOther = kitchenEquipmentOther.cleanOther(kitchenEquipment),
        culturalIdentitiesOther = culturalIdentitiesOther.cleanOther(culturalIdentities),
    )

    companion object {
        val EMPTY = EstablishmentCharacterization()
    }
}

/** Days of the week sharing one opening and closing time, as local `HH:MM`. */
data class OpeningHoursSlot(
    val days: Set<Weekday>,
    val opensAt: String,
    val closesAt: String,
)

enum class CharacterizationStatus { Pending, Complete }

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.cleanOther(chosen: Set<CharacterizationOption>): String? =
    if (chosen.any { it.isOther }) clean() else null
