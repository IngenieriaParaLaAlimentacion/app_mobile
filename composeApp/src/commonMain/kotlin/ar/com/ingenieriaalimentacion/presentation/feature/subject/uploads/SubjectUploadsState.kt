package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

data class SubjectUploadsState(
    val destination: SubjectUploadsDestination? = null,
    val uploads: List<SubjectUploadUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && uploads.isEmpty()
}
