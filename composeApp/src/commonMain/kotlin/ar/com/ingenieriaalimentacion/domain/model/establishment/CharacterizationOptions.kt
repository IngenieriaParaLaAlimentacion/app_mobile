package ar.com.ingenieriaalimentacion.domain.model.establishment

/**
 * Answer options of the establishment characterization ("ficha"). Wire values match
 * `app/domain/establishment_characterization.py` in the backend, and labels match the backoffice
 * (`constants/establishments.ts`), so the three surfaces read the same. The catalogs are still
 * pending sign-off with nutritionists and referents: adjust them here only.
 */
sealed interface CharacterizationOption {
    val wireValue: String
    val label: String

    /** True for the "Otro" option, whose field then accepts a free `*_other` text. */
    val isOther: Boolean get() = wireValue == OTHER_WIRE_VALUE
}

const val OTHER_WIRE_VALUE = "other"

internal inline fun <reified T> fromWireValue(value: String?): T?
    where T : Enum<T>, T : CharacterizationOption =
    enumValues<T>().firstOrNull { it.wireValue == value }

enum class Weekday(override val wireValue: String, override val label: String) : CharacterizationOption {
    Monday("monday", "Lunes"),
    Tuesday("tuesday", "Martes"),
    Wednesday("wednesday", "Miércoles"),
    Thursday("thursday", "Jueves"),
    Friday("friday", "Viernes"),
    Saturday("saturday", "Sábado"),
    Sunday("sunday", "Domingo"),
}

enum class SpaceType(override val wireValue: String, override val label: String) : CharacterizationOption {
    CommunityKitchen("community_kitchen", "Olla popular / merendero / comedor / copa de leche"),
    SchoolSupport("school_support", "Espacio comunitario de apoyo escolar"),
    CommunityHealth("community_health", "Posta sanitaria / salud comunitaria / promoción de la salud"),
    Childcare("childcare", "Espacio de cuidado comunitario de las infancias"),
    IveCounseling("ive_counseling", "Consejería en interrupción voluntaria del embarazo"),
    GenderViolenceCounseling("gender_violence_counseling", "Consejería en situaciones de violencia de género"),
    AddictionSupport("addiction_support", "CAAC / acompañamiento en consumo problemático"),
    HomelessSupport("homeless_support", "Acompañamiento a personas en situación de calle"),
    OlderAdultsCenter("older_adults_center", "Centro de jubilados y personas mayores"),
    NeighborhoodAssociation("neighborhood_association", "Sociedad de fomento"),
    ArtsCulture("arts_culture", "Espacio artístico / cultural"),
    DisabilityCare("disability_care", "Centro de cuidados para personas con discapacidad"),
    Fines("fines", "FinES"),
    Environmental("environmental", "Promoción del ambiente / ecologismo popular"),
    PopularEconomy("popular_economy", "Espacio productivo de la economía popular"),
    SportsClub("sports_club", "Club social / deportivo"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class CareTask(override val wireValue: String, override val label: String) : CharacterizationOption {
    FoodAssistance("food_assistance", "Asistencia alimentaria"),
    SchoolSupport("school_support", "Apoyo escolar"),
    PrimaryHealth("primary_health", "Atención primaria / promoción y prevención de la salud"),
    Childcare("childcare", "Cuidado comunitario de las infancias"),
    IveCounseling("ive_counseling", "Asesoramiento en interrupción voluntaria del embarazo"),
    GenderViolenceSupport("gender_violence_support", "Atención a situaciones de violencia de género"),
    AddictionSupport("addiction_support", "Acompañamiento en consumo problemático"),
    HomelessSupport("homeless_support", "Acompañamiento a personas en situación de calle"),
    Sports("sports", "Actividades deportivas"),
    OlderAdults("older_adults", "Actividades y acompañamiento de personas mayores"),
    Cultural("cultural", "Actividades culturales"),
    Disability("disability", "Actividades y acompañamiento para personas con discapacidad"),
    FinesTutoring("fines_tutoring", "Tutorías y acompañamiento de FinES"),
    Environmental("environmental", "Promoción ambiental"),
    PopularEconomy("popular_economy", "Elaboración de productos de la economía popular"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class Premises(override val wireValue: String, override val label: String) : CharacterizationOption {
    Street("street", "En la calle"),
    WorkerHome("worker_home", "En la casa de una persona trabajadora"),
    LentSpace("lent_space", "En un espacio prestado"),
    RentedSpace("rented_space", "En un espacio alquilado"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class Bathroom(override val wireValue: String, override val label: String) : CharacterizationOption {
    Exclusive("exclusive", "Sí, de uso exclusivo del espacio"),
    SharedWithHousehold("shared_with_household", "Sí, compartido con quienes viven ahí"),
    None("none", "No tiene"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class Utility(override val wireValue: String, override val label: String) : CharacterizationOption {
    RunningWater("running_water", "Red de agua corriente"),
    MotorPumpWell("motor_pump_well", "Perforación con bomba a motor"),
    Electricity("electricity", "Red eléctrica"),
    NaturalGas("natural_gas", "Red de gas"),
    GasCylinder("gas_cylinder", "Garrafa"),
    Sewer("sewer", "Cloaca"),
    SepticTank("septic_tank", "Pozo ciego"),
    Internet("internet", "Internet"),
}

enum class Affiliation(override val wireValue: String, override val label: String) : CharacterizationOption {
    Ngo("ngo", "Pertenece a una ONG"),
    SocialMovement("social_movement", "Pertenece a un movimiento social"),
    LegalStatus("legal_status", "Tiene personería o está reconocido como institución"),
    Independent("independent", "Trabaja de forma independiente"),
    LocalGovernment("local_government", "Trabaja articuladamente con el gobierno local"),
    Church("church", "Pertenece a una iglesia"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class AttendeeAge(override val wireValue: String, override val label: String) : CharacterizationOption {
    EarlyChildhood("early_childhood", "Primera infancia (0 a 5 años)"),
    Children("children", "Niñas y niños (6 a 14 años)"),
    Adolescents("adolescents", "Adolescentes (15 a 21 años)"),
    Adults("adults", "Personas adultas"),
    OlderAdults("older_adults", "Personas mayores"),
}

enum class Meal(override val wireValue: String, override val label: String) : CharacterizationOption {
    Breakfast("breakfast", "Desayuno"),
    Lunch("lunch", "Almuerzo"),
    Snack("snack", "Merienda"),
    Dinner("dinner", "Cena"),
}

enum class ServiceType(override val wireValue: String, override val label: String) : CharacterizationOption {
    OnSite("on_site", "Se come en el lugar"),
    Takeaway("takeaway", "Vianda para llevar"),
    GroceryBag("grocery_bag", "Bolsón de mercadería"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

enum class FoodReceptionFrequency(override val wireValue: String, override val label: String) :
    CharacterizationOption {
    Daily("daily", "Diaria"),
    Weekly("weekly", "Semanal"),
    Biweekly("biweekly", "Quincenal"),
    Monthly("monthly", "Mensual"),
    Irregular("irregular", "Irregular"),
}

enum class KitchenEquipment(override val wireValue: String, override val label: String) : CharacterizationOption {
    Stove("stove", "Cocina / hornallas"),
    Oven("oven", "Horno"),
    Fridge("fridge", "Heladera"),
    Freezer("freezer", "Freezer"),
    LargePots("large_pots", "Ollas de gran capacidad"),
    Blender("blender", "Procesadora o licuadora"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}

/** Provisional catalog, pending validation with community referents. */
enum class CulturalIdentity(override val wireValue: String, override val label: String) : CharacterizationOption {
    Indigenous("indigenous", "Pueblos originarios"),
    Andean("andean", "Andina / del norte"),
    Guarani("guarani", "Guaraní / litoraleña"),
    Migrant("migrant", "Comunidad migrante"),
    Criollo("criollo", "Criolla / rural"),
    Other(OTHER_WIRE_VALUE, "Otro"),
}
