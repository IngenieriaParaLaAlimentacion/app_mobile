package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentStatus
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementAssessment
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementIndicator
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.pow

internal val sapReferenceJson = Json { ignoreUnknownKeys = true }
internal const val INFANT_AGE_LIMIT_MONTHS = 24

internal fun completedMonthsAt(birth: LocalDate, measured: LocalDate): Int? {
    if (measured < birth) return null
    return (measured.year - birth.year) * 12 + measured.month.number - birth.month.number -
        if (measured.day < birth.day) 1 else 0
}

@Serializable
internal data class SapRuleSet(
    @SerialName("schema_version") val schemaVersion: Int,
    val version: String,
    @SerialName("pediatric_max_month") val pediatricMaxMonth: Int,
    val references: List<SapReference>,
)

@Serializable
internal data class SapReference(
    val indicator: String,
    @SerialName("reference_version") val referenceVersion: String,
    @SerialName("first_month") val firstMonth: Int,
    @SerialName("last_month") val lastMonth: Int,
    @SerialName("age_unit") val ageUnit: String,
    val formula: String,
    @SerialName("stature_method") val statureMethod: String? = null,
    @SerialName("alternate_method_offset_cm") val alternateMethodOffsetCm: Double? = null,
    val bands: List<SapBand>,
    val rows: List<SapRow>,
) {
    private val rowsByAge = rows.associateBy { it.sex to it.age }

    fun row(sex: Sex, age: Long): SapRow? = rowsByAge[sex.name.lowercase() to age]
}

@Serializable
internal data class SapRow(val sex: String, val age: Long, val lambda: Double, val median: Double, val sigma: Double)

@Serializable
internal data class SapBand(
    val lower: Double?,
    val upper: Double?,
    @SerialName("include_lower") val includeLower: Boolean,
    @SerialName("include_upper") val includeUpper: Boolean,
    val classification: String,
    val severity: String?,
) {
    fun contains(z: Double): Boolean =
        (lower == null || z > lower || includeLower && z == lower) &&
            (upper == null || z < upper || includeUpper && z == upper)
}

internal class SapAssessmentEvaluator(private val ruleSet: SapRuleSet) {
    fun evaluate(measurement: NewMeasurement, measuredDate: LocalDate): List<MeasurementAssessment> {
        val birthDate = measurement.subject.birthDate
        val months = birthDate?.let { completedMonthsAt(it, measuredDate) }
        return MeasurementIndicator.entries.filter { it != MeasurementIndicator.Unknown }.map { indicator ->
            val applicable = when {
                months == null -> return@map indicator.insufficient()
                months < INFANT_AGE_LIMIT_MONTHS ->
                    indicator == MeasurementIndicator.LengthForAge || indicator == MeasurementIndicator.WeightForAge
                months <= ruleSet.pediatricMaxMonth ->
                    indicator == MeasurementIndicator.BmiForAge || indicator == MeasurementIndicator.HeightForAge
                else -> indicator == MeasurementIndicator.AdultBmi
            }
            if (!applicable) {
                MeasurementAssessment(indicator, AssessmentStatus.NotApplicable, null, null, null, null)
            } else if (indicator == MeasurementIndicator.AdultBmi) {
                adultAssessment(measurement)
            } else {
                pediatricAssessment(indicator, measurement, checkNotNull(birthDate), measuredDate, months)
            }
        }
    }

    private fun pediatricAssessment(
        indicator: MeasurementIndicator,
        measurement: NewMeasurement,
        birth: LocalDate,
        measured: LocalDate,
        months: Int,
    ): MeasurementAssessment {
        val ref = ruleSet.references.firstOrNull { it.indicator == indicator.wire && months in it.firstMonth..it.lastMonth }
            ?: return indicator.insufficient()
        val age = if (ref.ageUnit == "days") measured.toEpochDays() - birth.toEpochDays() else months.toLong()
        val row = ref.row(measurement.subject.sex, age) ?: return indicator.insufficient(ref.referenceVersion)
        val value = when (indicator) {
            MeasurementIndicator.WeightForAge -> measurement.weightKg
            MeasurementIndicator.BmiForAge -> if (measurement.heightCm > 0.0) measurement.bmi() else Double.NaN
            else -> statureValue(measurement, indicator, ref) ?: return indicator.insufficient(ref.referenceVersion)
        }
        if (!value.isFinite() || value <= 0.0) return indicator.insufficient(ref.referenceVersion)
        val z = score(value, row, ref.formula)
        val band = ref.bands.firstOrNull { z.isFinite() && it.contains(z) }
            ?: return indicator.insufficient(ref.referenceVersion)
        return MeasurementAssessment(
            indicator, AssessmentStatus.Evaluated, band.classification, z, ref.referenceVersion,
            band.severity?.let { AssessmentAlert(it, band.classification) },
        )
    }

    private fun statureValue(
        measurement: NewMeasurement,
        indicator: MeasurementIndicator,
        reference: SapReference,
    ): Double? {
        val method = measurement.statureMethod?.wire
        if (indicator == MeasurementIndicator.LengthForAge && method == null) return null
        val recorded = method ?: "standing"
        if (reference.statureMethod == null || recorded == reference.statureMethod) return measurement.heightCm
        return reference.alternateMethodOffsetCm?.let { measurement.heightCm + it }
    }

    private fun adultAssessment(measurement: NewMeasurement): MeasurementAssessment {
        val bmi = measurement.bmi()
        if (measurement.heightCm <= 0 || !bmi.isFinite() || bmi <= 0) return MeasurementIndicator.AdultBmi.insufficient()
        val (classification, severity) = when {
            bmi < 16.0 -> "severe_underweight" to "critical"
            bmi < 18.5 -> "underweight" to "high"
            bmi >= 30.0 -> "obese" to "high"
            bmi >= 25.0 -> "overweight" to "medium"
            else -> "normal" to null
        }
        return MeasurementAssessment(
            MeasurementIndicator.AdultBmi, AssessmentStatus.Evaluated,
            classification, null, "who-adult-bmi", severity?.let { AssessmentAlert(it, classification) },
        )
    }
}

private fun MeasurementIndicator.insufficient(referenceVersion: String? = null): MeasurementAssessment =
    MeasurementAssessment(this, AssessmentStatus.InsufficientData, null, null, referenceVersion, null)

internal fun NewMeasurement.bmi(): Double = weightKg / (heightCm / 100.0).pow(2)

private fun score(value: Double, row: SapRow, formula: String): Double {
    if (formula == "standard") return (value - row.median) / row.sigma
    val logRatio = ln(value / row.median)
    val z = if (abs(row.lambda) < 1e-10) logRatio / row.sigma else expm1(row.lambda * logRatio) / (row.lambda * row.sigma)
    if (formula != "lms_extended" || abs(z) <= 3.0) return z
    val bound = if (z < 0) -3.0 else 3.0
    val adjacent = if (z < 0) -2.0 else 2.0
    val boundaryValue = row.valueAtZ(bound)
    return bound + (value - boundaryValue) / abs(boundaryValue - row.valueAtZ(adjacent))
}

private fun SapRow.valueAtZ(z: Double): Double =
    median * if (abs(lambda) < 1e-10) exp(sigma * z) else exp(ln1p(lambda * sigma * z) / lambda)
