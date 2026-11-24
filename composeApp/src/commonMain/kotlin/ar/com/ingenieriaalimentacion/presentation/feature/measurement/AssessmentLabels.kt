package ar.com.ingenieriaalimentacion.presentation.feature.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentStatus
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementAssessment
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementIndicator

internal fun assessmentIndicatorLabel(indicator: MeasurementIndicator): String = when (indicator) {
    MeasurementIndicator.LengthForAge -> "Longitud para la edad"
    MeasurementIndicator.WeightForAge -> "Peso para la edad"
    MeasurementIndicator.BmiForAge -> "IMC para la edad"
    MeasurementIndicator.HeightForAge -> "Talla para la edad"
    MeasurementIndicator.AdultBmi -> "IMC"
    MeasurementIndicator.Unknown -> "Indicador no disponible"
}

internal fun MeasurementAssessment.resultLabel(): String = when (status) {
    AssessmentStatus.InsufficientData -> "Sin datos suficientes"
    AssessmentStatus.NotApplicable -> "No aplica"
    AssessmentStatus.Unknown -> "Resultado no disponible"
    AssessmentStatus.Evaluated -> when (classification) {
        null -> "Resultado no disponible"
        "normal" -> "Normal"
        "low_weight", "low_weight_for_age" -> "Bajo peso"
        "very_low_weight", "severe_low_weight_for_age" -> "Bajo peso grave"
        "short_stature", "low_height_for_age", "low_length_for_age" -> "Talla o longitud baja"
        "severe_short_stature", "severe_low_height_for_age", "severe_low_length_for_age" -> "Talla o longitud muy baja"
        "high_weight", "high_weight_for_age" -> "Peso alto"
        "high_stature", "high_height_for_age", "high_length_for_age" -> "Talla o longitud alta"
        else -> alertTypeLabel(classification)
    }
}

internal fun List<MeasurementAssessment>.hasApplicableBmi(): Boolean = any {
    (it.indicator == MeasurementIndicator.BmiForAge || it.indicator == MeasurementIndicator.AdultBmi) &&
        it.status != AssessmentStatus.NotApplicable
}

/** Canonical backend `alert_type` (lowercase enum .value) → Spanish clinical label. */
internal fun alertTypeLabel(raw: String): String = when (raw.trim().lowercase()) {
    "severely_wasted" -> "Emaciación grave"
    "wasted" -> "Emaciación"
    "underweight" -> "Bajo peso"
    "severe_underweight" -> "Bajo peso grave"
    "overweight" -> "Sobrepeso"
    "obese" -> "Obesidad"
    // Defensive: future/unknown codes render readably instead of leaking raw enums.
    else -> raw.trim().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
