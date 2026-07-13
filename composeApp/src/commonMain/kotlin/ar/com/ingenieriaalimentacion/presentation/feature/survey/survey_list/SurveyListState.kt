package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_list

import ar.com.ingenieriaalimentacion.domain.model.survey.Survey

data class SurveyListState(
    val destination: SurveyListDestination? = null,
    val surveys: List<Survey> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadError: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
    val fromCache: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = page < totalPages && !isLoading && !isLoadingMore && !loadError
}
