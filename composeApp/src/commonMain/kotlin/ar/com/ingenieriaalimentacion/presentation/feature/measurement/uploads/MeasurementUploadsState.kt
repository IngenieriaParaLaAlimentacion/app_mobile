package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

data class MeasurementUploadsState(
    val uploads: List<MeasurementUploadUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
}
