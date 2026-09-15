package ar.com.ingenieriaalimentacion.domain.usecase.activity

import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository

/**
 * Best-effort prefetch of page 1 for every entity type, so Home has fresh data even before the
 * user opens History this session. Mirrors
 * [ar.com.ingenieriaalimentacion.domain.usecase.profile.RefreshMyEstablishmentsUseCase].
 */
class WarmActivityCacheUseCase(
    private val subjects: SubjectHistoryRepository,
    private val measurements: MeasurementHistoryRepository,
    private val surveyResponses: SurveyResponseHistoryRepository,
    private val foodMovements: FoodMovementHistoryRepository,
) {
    suspend operator fun invoke() {
        subjects.loadPage(page = 1, pageSize = PAGE_SIZE)
        measurements.loadPage(page = 1, pageSize = PAGE_SIZE)
        surveyResponses.getPage(page = 1, pageSize = PAGE_SIZE)
        // One request covering both directions: the repository asks for food_intake +
        // food_outflow in a single call and the server returns them merged.
        foodMovements.getPage(page = 1, pageSize = PAGE_SIZE)
    }
    private companion object { const val PAGE_SIZE = 20 }
}
