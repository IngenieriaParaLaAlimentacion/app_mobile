package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary

data class SurveyResponseHistoryState(
    val navigateToResponseId: String? = null,
    val items: List<SurveyResponseSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
) {
    val canLoadMore: Boolean get() = hasMore && !isLoading && !isLoadingMore
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
