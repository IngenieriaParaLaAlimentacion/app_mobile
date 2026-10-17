package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Evaluates whether a measurement matches an existing nutritional alert threshold. */
class MeasurementAlertEvaluator {
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
        val months = (measuredDate.year - birthDate.year) * 12 +
            measuredDate.monthNumber - birthDate.monthNumber -
            if (measuredDate.dayOfMonth < birthDate.dayOfMonth) 1 else 0
        return months.coerceAtLeast(0)
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
