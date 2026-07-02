package ar.com.ingenieriaalimentacion.domain.usecase.activity

import ar.com.ingenieriaalimentacion.domain.repository.MeasurementHistoryRepository
import ar.com.ingenieriaalimentacion.domain.repository.SubjectHistoryRepository

/**
 * Best-effort prefetch of page 1 for both entity types, so Home has fresh data even before the
 * user opens History this session. Mirrors
 * [ar.com.ingenieriaalimentacion.domain.usecase.profile.RefreshMyEstablishmentsUseCase].
 */
class WarmActivityCacheUseCase(
    private val subjects: SubjectHistoryRepository,
    private val measurements: MeasurementHistoryRepository,
) {
    suspend operator fun invoke() {
        subjects.loadPage(page = 1, pageSize = PAGE_SIZE)
        measurements.loadPage(page = 1, pageSize = PAGE_SIZE)
    }
    private companion object { const val PAGE_SIZE = 20 }
}
