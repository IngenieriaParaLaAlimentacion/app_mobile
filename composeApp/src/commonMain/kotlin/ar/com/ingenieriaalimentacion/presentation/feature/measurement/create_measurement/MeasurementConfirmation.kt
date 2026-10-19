package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.PreparedMeasurement

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
        return "$values\n\n$instruction"
    }
}
