package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary

data class SurveyResponseHistoryState(
    val destination: SurveyResponseHistoryDestination? = null,
    val items: List<SurveyResponseSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadError: Boolean = false,
    val page: Int = 1,
    // getPage has no total-pages signal (it unions two sources with different shapes) — a
    // short page is the only "no more" signal available.
    val hasMore: Boolean = true,
) {
    val canLoadMore: Boolean
        get() = hasMore && !isLoading && !isLoadingMore && !loadError
}
