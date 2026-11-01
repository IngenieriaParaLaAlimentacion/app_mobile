package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary

/** Both ids are needed to open a response — its endpoints are nested under the survey. */
data class SurveyResponseTargetRoute(val surveyId: String, val responseId: String)
data class SurveyResponseNumberFixRoute(val localId: String, val surveyName: String)

data class SurveyResponseHistoryState(
    val navigateToResponse: SurveyResponseTargetRoute? = null,
    val navigateToNumberFix: SurveyResponseNumberFixRoute? = null,
    val items: List<SurveyResponseSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
) {
    val canLoadMore: Boolean get() = hasMore && !isLoading && !isLoadingMore
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
