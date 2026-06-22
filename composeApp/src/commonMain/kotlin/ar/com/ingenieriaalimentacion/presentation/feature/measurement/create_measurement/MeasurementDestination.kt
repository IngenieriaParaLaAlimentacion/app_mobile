package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

sealed class MeasurementDestination {
    data object Back : MeasurementDestination()
    data object NavigateToSelectSubject : MeasurementDestination()
}
