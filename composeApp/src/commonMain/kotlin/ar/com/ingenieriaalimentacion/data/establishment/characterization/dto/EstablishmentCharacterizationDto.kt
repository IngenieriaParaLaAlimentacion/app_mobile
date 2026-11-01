package ar.com.ingenieriaalimentacion.data.establishment.characterization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of the characterization profile, shared by `PUT /establishments/{id}/characterization`,
 * the dashboard and the creation request. Options travel as their raw wire values; the mapper turns
 * them into the domain enums. Every field is always sent (the client encodes defaults) because the
 * backend replaces the profile as a whole.
 */
@Serializable
data class EstablishmentCharacterizationDto(
    val district: String? = null,
    val neighborhood: String? = null,
    @SerialName("cross_streets") val crossStreets: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("opening_hours") val openingHours: List<OpeningHoursSlotDto>? = null,

    @SerialName("space_types") val spaceTypes: List<String>? = null,
    @SerialName("space_types_other") val spaceTypesOther: String? = null,
    @SerialName("care_tasks") val careTasks: List<String>? = null,
    @SerialName("care_tasks_other") val careTasksOther: String? = null,
    val premises: String? = null,
    @SerialName("premises_other") val premisesOther: String? = null,
    val bathroom: String? = null,
    @SerialName("bathroom_other") val bathroomOther: String? = null,
    val utilities: List<String>? = null,
    val affiliations: List<String>? = null,
    @SerialName("affiliations_other") val affiliationsOther: String? = null,

    @SerialName("daily_attendance") val dailyAttendance: Int? = null,
    @SerialName("attendee_ages") val attendeeAges: List<String>? = null,

    val meals: List<String>? = null,
    @SerialName("service_types") val serviceTypes: List<String>? = null,
    @SerialName("service_types_other") val serviceTypesOther: String? = null,
    @SerialName("food_reception_frequency") val foodReceptionFrequency: String? = null,
    @SerialName("cooks_per_service") val cooksPerService: Int? = null,
    @SerialName("kitchen_equipment") val kitchenEquipment: List<String>? = null,
    @SerialName("kitchen_equipment_other") val kitchenEquipmentOther: String? = null,

    @SerialName("cultural_identities") val culturalIdentities: List<String>? = null,
    @SerialName("cultural_identities_other") val culturalIdentitiesOther: String? = null,
)

@Serializable
data class OpeningHoursSlotDto(
    val days: List<String>,
    @SerialName("opens_at") val opensAt: String,
    @SerialName("closes_at") val closesAt: String,
)

/** `GET /establishments/{id}/dashboard`, read only for its profile; metrics are ignored. */
@Serializable
data class CharacterizationDashboardResponse(
    val characterization: EstablishmentCharacterizationDto? = null,
)

/** `PUT /establishments/{id}/characterization` response. */
@Serializable
data class CharacterizationReplaceResponse(
    @SerialName("characterization_status") val characterizationStatus: String,
)

/**
 * Focused view of the 422 problem body. Routes that opt in to `x-invalid-field-extension` add
 * [field], the dotted body path of the first field that failed — never its value.
 */
@Serializable
data class ValidationProblem(
    val type: String? = null,
    val field: String? = null,
)
