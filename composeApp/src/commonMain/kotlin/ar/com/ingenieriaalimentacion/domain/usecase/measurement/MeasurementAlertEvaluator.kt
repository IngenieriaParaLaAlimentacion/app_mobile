package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementAssessment
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

private val bundledSapEvaluator by lazy {
    BundledSapReference.rulesetJson?.let { SapAssessmentEvaluator(sapReferenceJson.decodeFromString<SapRuleSet>(it)) }
}

/** Evaluates the bundled reference while retaining the legacy preview on older bundles. */
class MeasurementAlertEvaluator internal constructor(private val sap: SapAssessmentEvaluator?) {
    constructor() : this(bundledSapEvaluator)

    internal fun evaluate(measurement: NewMeasurement, measuredDate: LocalDate): List<MeasurementAssessment>? =
        sap?.evaluate(measurement, measuredDate)

    fun requiresConfirmation(
        bmi: Double,
        sex: Sex,
        birthDate: LocalDate,
        measuredDate: LocalDate,
    ): Boolean {
        require(bmi.isFinite() && bmi > 0.0)
        val months = completedMonths(birthDate, measuredDate)
        if (months > 228) return bmi < 18.5 || bmi >= 25.0
        val lms = WhoBmiLms.get(sex, months) ?: return false
        return requiresConfirmationForZ(zScore(bmi, lms))
    }

    internal fun completedMonths(birthDate: LocalDate, measuredDate: LocalDate): Int {
        return completedMonthsAt(birthDate, measuredDate) ?: 0
    }
}

internal fun requiresConfirmationForZ(z: Double): Boolean = z < -2.0 || z > 2.0

internal fun zScore(bmi: Double, lms: WhoBmiLms.Lms): Double {
    val ratio = bmi / lms.median
    return if (abs(lms.lambda) < 1e-10) {
        ln(ratio) / lms.sigma
    } else {
        (ratio.pow(lms.lambda) - 1.0) / (lms.lambda * lms.sigma)
    }
}
