package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.PreparedMeasurement
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
        val values = "Peso: ${weightText.replace('.', ',')} kg\nTalla: ${heightText.replace('.', ',')} cm"
        val instruction = if (prepared.demographicsKnown) {
            "Estas medidas generarían una alerta. Verificá que los valores sean correctos antes de guardarlos."
        } else {
            "Verificá que los valores sean correctos antes de guardarlos."
        }
        val dateTime = prepared.measurement.measuredAt
            .toLocalDateTime(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE)
        return "$values\nFecha: ${formatMeasurementDate(dateTime.date)}\nHora: ${formatMeasurementTime(dateTime.time)} (Argentina)\n\n$instruction"
    }
}

internal fun formatMeasurementDate(value: LocalDate): String =
    "${value.dayOfMonth.toString().padStart(2, '0')}/${value.monthNumber.toString().padStart(2, '0')}/${value.year.toString().padStart(4, '0')}"

internal fun formatMeasurementTime(value: LocalTime): String =
    "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
