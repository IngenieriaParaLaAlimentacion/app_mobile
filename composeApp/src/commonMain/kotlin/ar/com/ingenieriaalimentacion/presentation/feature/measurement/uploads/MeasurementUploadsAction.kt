package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

sealed interface MeasurementUploadsAction {
    data class Retry(val localId: String) : MeasurementUploadsAction
    data class Delete(val localId: String) : MeasurementUploadsAction
}
