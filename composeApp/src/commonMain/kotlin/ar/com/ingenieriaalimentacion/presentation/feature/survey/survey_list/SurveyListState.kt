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
    /** The survey whose picker is currently on top of this screen. The list's ViewModel survives
     *  because the picker is *pushed above* it, so this is enough to resume once a result lands. */
    val pendingSurveyId: String? = null,
) {
    val canLoadMore: Boolean
        get() = page < totalPages && !isLoading && !isLoadingMore && !loadError
}
