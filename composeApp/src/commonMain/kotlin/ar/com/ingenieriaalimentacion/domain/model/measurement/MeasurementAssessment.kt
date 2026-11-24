package ar.com.ingenieriaalimentacion.domain.model.measurement

import kotlinx.serialization.Serializable

@Serializable
enum class StatureMethod(val wire: String) {
    Recumbent("recumbent"),
    Standing("standing"),
}

@Serializable
enum class MeasurementIndicator(val wire: String) {
    LengthForAge("length_for_age"),
    WeightForAge("weight_for_age"),
    BmiForAge("bmi_for_age"),
    HeightForAge("height_for_age"),
    AdultBmi("adult_bmi"),
    Unknown("unknown"),
}

@Serializable
enum class AssessmentStatus(val wire: String) {
    Evaluated("evaluated"),
    InsufficientData("insufficient_data"),
    NotApplicable("not_applicable"),
    Unknown("unknown"),
}

@Serializable
data class MeasurementAssessment(
    val indicator: MeasurementIndicator,
    val status: AssessmentStatus,
    val classification: String?,
    val zScore: Double?,
    val referenceVersion: String?,
    val alert: AssessmentAlert?,
)

@Serializable
data class AssessmentAlert(val severity: String, val detail: String)
