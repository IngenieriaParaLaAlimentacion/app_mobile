package ar.com.ingenieriaalimentacion.data.establishment.characterization.mapper

import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.EstablishmentCharacterizationDto
import ar.com.ingenieriaalimentacion.data.establishment.characterization.dto.OpeningHoursSlotDto
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationOption
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.OpeningHoursSlot
import ar.com.ingenieriaalimentacion.domain.model.establishment.Weekday
import ar.com.ingenieriaalimentacion.domain.model.establishment.fromWireValue

/**
 * Wire values this build does not know are dropped. Since a save replaces the whole profile, an
 * option added on the backend before the app learns it is lost on the next save — keep the catalogs
 * in `CharacterizationOptions.kt` in step with the backend.
 */
internal fun EstablishmentCharacterizationDto.toDomain(): EstablishmentCharacterization =
    EstablishmentCharacterization(
        district = district,
        neighborhood = neighborhood,
        crossStreets = crossStreets,
        contactPhone = contactPhone,
        contactEmail = contactEmail,
        openingHours = openingHours.orEmpty().mapNotNull { it.toDomain() },
        spaceTypes = spaceTypes.toOptions(),
        spaceTypesOther = spaceTypesOther,
        careTasks = careTasks.toOptions(),
        careTasksOther = careTasksOther,
        premises = fromWireValue(premises),
        premisesOther = premisesOther,
        bathroom = fromWireValue(bathroom),
        bathroomOther = bathroomOther,
        utilities = utilities.toOptions(),
        affiliations = affiliations.toOptions(),
        affiliationsOther = affiliationsOther,
        dailyAttendance = dailyAttendance,
        attendeeAges = attendeeAges.toOptions(),
        meals = meals.toOptions(),
        serviceTypes = serviceTypes.toOptions(),
        serviceTypesOther = serviceTypesOther,
        foodReceptionFrequency = fromWireValue(foodReceptionFrequency),
        cooksPerService = cooksPerService,
        kitchenEquipment = kitchenEquipment.toOptions(),
        kitchenEquipmentOther = kitchenEquipmentOther,
        culturalIdentities = culturalIdentities.toOptions(),
        culturalIdentitiesOther = culturalIdentitiesOther,
    )

internal fun EstablishmentCharacterization.toDto(): EstablishmentCharacterizationDto =
    EstablishmentCharacterizationDto(
        district = district,
        neighborhood = neighborhood,
        crossStreets = crossStreets,
        contactPhone = contactPhone,
        contactEmail = contactEmail,
        openingHours = openingHours.map { it.toDto() },
        spaceTypes = spaceTypes.toWire(),
        spaceTypesOther = spaceTypesOther,
        careTasks = careTasks.toWire(),
        careTasksOther = careTasksOther,
        premises = premises?.wireValue,
        premisesOther = premisesOther,
        bathroom = bathroom?.wireValue,
        bathroomOther = bathroomOther,
        utilities = utilities.toWire(),
        affiliations = affiliations.toWire(),
        affiliationsOther = affiliationsOther,
        dailyAttendance = dailyAttendance,
        attendeeAges = attendeeAges.toWire(),
        meals = meals.toWire(),
        serviceTypes = serviceTypes.toWire(),
        serviceTypesOther = serviceTypesOther,
        foodReceptionFrequency = foodReceptionFrequency?.wireValue,
        cooksPerService = cooksPerService,
        kitchenEquipment = kitchenEquipment.toWire(),
        kitchenEquipmentOther = kitchenEquipmentOther,
        culturalIdentities = culturalIdentities.toWire(),
        culturalIdentitiesOther = culturalIdentitiesOther,
    )

/** `null` for a missing or unrecognized value, so older backends and new values show no badge. */
internal fun String?.toCharacterizationStatus(): CharacterizationStatus? = when (this) {
    "pending" -> CharacterizationStatus.Pending
    "complete" -> CharacterizationStatus.Complete
    else -> null
}

private fun OpeningHoursSlotDto.toDomain(): OpeningHoursSlot? {
    val days = days.toOptions<Weekday>()
    // A slot whose every day is unknown can't be shown or saved back.
    return if (days.isEmpty()) null else OpeningHoursSlot(days, opensAt, closesAt)
}

// Days go out in week order, whatever order they were picked in.
private fun OpeningHoursSlot.toDto(): OpeningHoursSlotDto =
    OpeningHoursSlotDto(days = days.sortedBy { it.ordinal }.map { it.wireValue }, opensAt = opensAt, closesAt = closesAt)

private inline fun <reified T> List<String>?.toOptions(): Set<T>
    where T : Enum<T>, T : CharacterizationOption =
    orEmpty().mapNotNullTo(linkedSetOf()) { fromWireValue<T>(it) }

// Catalog order keeps the body stable regardless of the order options were tapped in.
private fun <T> Set<T>.toWire(): List<String> where T : Enum<T>, T : CharacterizationOption =
    sortedBy { it.ordinal }.map { it.wireValue }
