package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.PreparedMeasurement
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.INFANT_AGE_LIMIT_MONTHS
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.completedMonthsAt
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.assessmentIndicatorLabel
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.PrepareMeasurementUseCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime

data class MeasurementConfirmation(
    val token: Long,
    val prepared: PreparedMeasurement,
    val weightText: String,
    val heightText: String,
) {
    val description: String get() {
        val dateTime = prepared.measurement.measuredAt
            .toLocalDateTime(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE)
        val months = prepared.measurement.subject.birthDate?.let { completedMonthsAt(it, dateTime.date) }
        val statureLabel = when {
            months == null -> "Longitud o talla"
            months < INFANT_AGE_LIMIT_MONTHS -> "Longitud"
            else -> "Talla"
        }
        val values = "Peso: ${weightText.replace('.', ',')} kg\n$statureLabel: ${heightText.replace('.', ',')} cm"
        val review = "Verificá que los valores sean correctos antes de guardarlos."
        val instruction = when {
            prepared.reviewIndicators.isNotEmpty() ->
                prepared.reviewIndicators.joinToString(prefix = "Revisá los indicadores: ", postfix = ".\n$review") {
                    assessmentIndicatorLabel(it)
                }
            prepared.demographicsKnown -> "Estas medidas generarían una alerta. $review"
            else -> review
        }
        return "$values\nFecha: ${formatMeasurementDate(dateTime.date)}\nHora: ${formatMeasurementTime(dateTime.time)} (Argentina)\n\n$instruction"
    }
}

internal fun formatMeasurementDate(value: LocalDate): String =
    "${value.dayOfMonth.toString().padStart(2, '0')}/${value.monthNumber.toString().padStart(2, '0')}/${value.year.toString().padStart(4, '0')}"

internal fun formatMeasurementTime(value: LocalTime): String =
    "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
