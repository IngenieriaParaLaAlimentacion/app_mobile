package ar.com.ingenieriaalimentacion.domain.usecase.activity

import ar.com.ingenieriaalimentacion.domain.repository.FoodMovementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    // Concurrently: the four prefetches are independent, each swallows its own failure, and this
    // runs on entry to the authed shell — serialized, it would put four round trips on the path
    // between login and the first useful frame.
    suspend operator fun invoke() = coroutineScope {
        listOf(
            async { subjects.loadPage(page = 1, pageSize = PAGE_SIZE) },
            async { measurements.loadPage(page = 1, pageSize = PAGE_SIZE) },
            async { surveyResponses.getPage(page = 1, pageSize = PAGE_SIZE) },
            // One request covering both directions: the repository asks for food_intake +
            // food_outflow in a single call and the server returns them merged.
            async { foodMovements.getPage(page = 1, pageSize = PAGE_SIZE) },
        ).awaitAll()
        Unit
    }
    private companion object { const val PAGE_SIZE = 20 }
}
