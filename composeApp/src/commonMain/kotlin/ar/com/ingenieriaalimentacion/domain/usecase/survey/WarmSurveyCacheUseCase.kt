package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

/**
 * Best-effort prefetch of the full survey snapshot + question sets, so the survey list and
 * response form work fully offline later. Mirrors
 * [ar.com.ingenieriaalimentacion.domain.usecase.activity.WarmActivityCacheUseCase].
 */
class WarmSurveyCacheUseCase(private val surveys: SurveyRepository) {
    suspend operator fun invoke() = surveys.warmAvailableSurveys()
}
