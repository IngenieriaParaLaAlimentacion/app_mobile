package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

data class SubjectUploadsState(
    val uploads: List<SubjectUploadUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
}
