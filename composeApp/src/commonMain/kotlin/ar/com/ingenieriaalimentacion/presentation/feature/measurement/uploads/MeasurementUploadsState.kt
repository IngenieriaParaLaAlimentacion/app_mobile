package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

data class MeasurementUploadsState(
    val uploads: List<MeasurementUploadUi> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
    val canLoadMore: Boolean get() = page < totalPages && !isLoading && !isLoadingMore
}
