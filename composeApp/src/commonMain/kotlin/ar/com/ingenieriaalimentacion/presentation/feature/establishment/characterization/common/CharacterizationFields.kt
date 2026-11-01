package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common

import ar.com.ingenieriaalimentacion.domain.model.establishment.Affiliation
import ar.com.ingenieriaalimentacion.domain.model.establishment.AttendeeAge
import ar.com.ingenieriaalimentacion.domain.model.establishment.CareTask
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationOption
import ar.com.ingenieriaalimentacion.domain.model.establishment.CulturalIdentity
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.ServiceType
import ar.com.ingenieriaalimentacion.domain.model.establishment.SpaceType
import ar.com.ingenieriaalimentacion.domain.model.establishment.Utility

/**
 * Fields of the ficha form. [wireName] is the backend's snake_case body field, which is also what a
 * 422's `field` extension names, so a server error lands on the matching input. Copy mirrors the
 * backoffice (`ESTABLISHMENT_PROFILE` in `constants/establishments.ts`).
 */
enum class CharacterizationTextField(val wireName: String, val label: String) {
    District("district", "Distrito / partido"),
    Neighborhood("neighborhood", "Barrio"),
    CrossStreets("cross_streets", "Entrecalles"),
    ContactPhone("contact_phone", "Teléfono de contacto"),
    ContactEmail("contact_email", "Mail de contacto"),
    DailyAttendance("daily_attendance", "Personas que recibe en un día normal"),
    CooksPerService("cooks_per_service", "Personas que cocinan en un servicio normal"),

    // "Otro" free texts; their label is the field they belong to.
    SpaceTypesOther("space_types_other", "Otro tipo de espacio"),
    CareTasksOther("care_tasks_other", "Otra tarea de cuidado"),
    PremisesOther("premises_other", "Otro lugar"),
    BathroomOther("bathroom_other", "Otro tipo de baño"),
    AffiliationsOther("affiliations_other", "Otra organización"),
    ServiceTypesOther("service_types_other", "Otra modalidad"),
    KitchenEquipmentOther("kitchen_equipment_other", "Otro equipamiento"),
    CulturalIdentitiesOther("cultural_identities_other", "Otra identidad cultural");

    val isNumeric: Boolean get() = this == DailyAttendance || this == CooksPerService
}

enum class CharacterizationMultiField(
    val wireName: String,
    val label: String,
    val options: List<CharacterizationOption>,
    val other: CharacterizationTextField? = null,
) {
    SpaceTypes("space_types", "Tipo de espacio", SpaceType.entries, CharacterizationTextField.SpaceTypesOther),
    CareTasks(
        "care_tasks", "Tareas de cuidado que se realizan", CareTask.entries,
        CharacterizationTextField.CareTasksOther,
    ),
    Utilities("utilities", "Servicios con los que cuenta", Utility.entries),
    Affiliations("affiliations", "Organización", Affiliation.entries, CharacterizationTextField.AffiliationsOther),
    AttendeeAges("attendee_ages", "Edades de quienes asisten", AttendeeAge.entries),
    Meals("meals", "Comidas que se sirven", Meal.entries),
    ServiceTypes(
        "service_types", "Modalidad del servicio de comida", ServiceType.entries,
        CharacterizationTextField.ServiceTypesOther,
    ),
    KitchenEquipment(
        "kitchen_equipment", "Equipamiento de cocina",
        ar.com.ingenieriaalimentacion.domain.model.establishment.KitchenEquipment.entries,
        CharacterizationTextField.KitchenEquipmentOther,
    ),
    CulturalIdentities(
        "cultural_identities", "Identidad cultural de la comunidad", CulturalIdentity.entries,
        CharacterizationTextField.CulturalIdentitiesOther,
    ),
}

enum class CharacterizationSingleField(
    val wireName: String,
    val label: String,
    val options: List<CharacterizationOption>,
    val other: CharacterizationTextField? = null,
) {
    Premises(
        "premises", "Dónde funciona",
        ar.com.ingenieriaalimentacion.domain.model.establishment.Premises.entries,
        CharacterizationTextField.PremisesOther,
    ),
    Bathroom(
        "bathroom", "Baño",
        ar.com.ingenieriaalimentacion.domain.model.establishment.Bathroom.entries,
        CharacterizationTextField.BathroomOther,
    ),
    FoodReceptionFrequency(
        "food_reception_frequency", "Frecuencia de recepción de alimentos",
        ar.com.ingenieriaalimentacion.domain.model.establishment.FoodReceptionFrequency.entries,
    ),
}

internal object CharacterizationCopy {
    const val FORM_HINT =
        "Todos los campos son opcionales: completá lo que sepas y dejá el resto para después."
    const val PRIVATE_HINT = "No se publica: solo lo ve el equipo"
    const val OTHER_PLACEHOLDER = "¿Cuál? (opcional)"
    const val CROSS_STREETS_PLACEHOLDER = "Ej.: Belgrano y Moreno"
    const val SERVER_INVALID = "Este valor no es válido. Revisalo y volvé a guardar."

    const val SECTION_LOCATION = "Ubicación y contacto"
    const val SECTION_SPACE = "El espacio"
    const val SECTION_ATTENDEES = "Quiénes asisten"
    const val SECTION_FOOD = "Alimentación"
    const val SECTION_COMMUNITY = "Comunidad"

    const val OPENING_HOURS = "Días y horarios de funcionamiento"
    const val OPENING_HOURS_HINT =
        "Si los horarios cambian según el día, agregá un horario por cada franja."
    const val ADD_SLOT = "Agregar horario"
    const val REMOVE_SLOT = "Quitar horario"
    const val OPENS_AT = "Desde"
    const val CLOSES_AT = "Hasta"
    const val TIME_PLACEHOLDER = "HH:MM"
    fun slotLabel(n: Int) = "Horario $n"

    const val TEXT_MAX_LENGTH = "Máximo 100 caracteres"
    const val INVALID_EMAIL = "Ingresá un mail válido"
    const val INVALID_PHONE = "Usá solo números, espacios, guiones, paréntesis o +"
    const val PHONE_MAX_LENGTH = "Máximo 30 caracteres"
    const val INVALID_TIME = "Ingresá una hora válida, por ejemplo 09:30"
    const val DAYS_REQUIRED = "Elegí al menos un día"
    const val TIMES_REQUIRED = "Completá el horario de apertura y de cierre"
    const val CLOSES_BEFORE_OPENS = "El cierre tiene que ser después de la apertura"
    fun wholeNumberUpTo(max: String) = "Ingresá un número entero entre 0 y $max"
}
