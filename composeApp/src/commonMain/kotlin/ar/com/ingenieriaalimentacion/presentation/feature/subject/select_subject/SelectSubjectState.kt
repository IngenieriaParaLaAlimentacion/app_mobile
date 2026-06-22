package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

data class SelectSubjectState(
    val destination: SelectSubjectDestination? = null,
    val query: String = "",
    val results: List<SubjectSummary> = emptyList(),
    val isLoading: Boolean = true,       // carga inicial / nueva búsqueda
    val isLoadingMore: Boolean = false,  // anexando página siguiente
    val loadError: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val canLoadMore: Boolean
        get() = page < totalPages && !isLoading && !isLoadingMore && !loadError
}
