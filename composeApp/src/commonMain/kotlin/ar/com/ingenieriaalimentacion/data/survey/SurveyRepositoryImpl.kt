package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.repository.PagedSurveys
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository

/**
 * [getAvailable] is network-first — availability is time-sensitive, so a live page always wins —
 * with a cache fallback: on a page-1 network failure the CachedSurvey snapshot (last warm or
 * last successful fetch) is served as a single self-contained page, active surveys only, flagged
 * [PagedSurveys.fromCache]. Deeper pages never fall back (the cache is unpaged). [getDetail] is
 * cache-first: any survey warmed or opened once online is answerable fully offline.
 */
class SurveyRepositoryImpl(
    private val api: SurveyApi,
    private val local: SurveyLocalStore,
) : SurveyRepository {

    override suspend fun getAvailable(page: Int, pageSize: Int): Result<PagedSurveys> {
        val remote = runCatching {
            val response = api.available(page, pageSize)
            local.upsertSummaries(response.items.map { it.toDomain() })
            response.toDomain()
        }
        if (remote.isSuccess || page != 1) return remote

        // Offline fallback: serve the cached snapshot as one self-contained page (totalPages = 1),
        // same shape as SubjectQueryRepositoryImpl's fallback. Active-only: the server never returns
        // inactive surveys from /available, so cached inactive rows must not resurface offline.
        val cached = local.getSummaries().filter { it.isActive }
        return if (cached.isNotEmpty()) {
            Result.success(PagedSurveys(items = cached, page = 1, totalPages = 1, fromCache = true))
        } else {
            remote
        }
    }

    override suspend fun getDetail(surveyId: String): Result<SurveyDetail> {
        local.findDetail(surveyId)?.let { return Result.success(it) }
        return when (val outcome = api.detail(surveyId)) {
            is SurveyDetailOutcome.Found -> {
                val detail = outcome.response.toDomain()
                local.upsertDetail(detail)
                Result.success(detail)
            }
            SurveyDetailOutcome.Forbidden -> Result.failure(SurveyFeatureFailure(SurveyFeatureError.Forbidden, null))
            SurveyDetailOutcome.NotFound -> Result.failure(SurveyFeatureFailure(SurveyFeatureError.NotFound, null))
            is SurveyDetailOutcome.Retryable ->
                Result.failure(SurveyFeatureFailure(SurveyFeatureError.Network, outcome.cause))
        }
    }

    override suspend fun warmAvailableSurveys() {
        // 1. Full snapshot: page through /available. Any page failing aborts the replace (a partial
        //    snapshot must not delete surveys that live on unfetched pages) but keeps upsert progress
        //    already written by getAvailable's write-through.
        val all = mutableListOf<Survey>()
        var page = 1
        var totalPages: Int
        do {
            val result = runCatching { api.available(page, WARM_PAGE_SIZE) }.getOrNull() ?: return
            all += result.items.map { it.toDomain() }
            totalPages = result.totalPages
            page++
        } while (page <= totalPages && page <= MAX_WARM_PAGES)
        if (page <= totalPages) {
            // Snapshot truncated by the cap: fall back to upsert-only so uncached tail pages'
            // rows (from an earlier, larger warm) aren't deleted.
            local.upsertSummaries(all)
        } else {
            local.replaceAllSummaries(all)
        }

        // 2. Detail prefetch, one by one, best-effort: getDetail is cache-first so only missing
        //    details hit the network, and each success is cached immediately.
        local.idsMissingDetail().forEach { id -> getDetail(id) }
    }

    private companion object {
        const val WARM_PAGE_SIZE = 50
        const val MAX_WARM_PAGES = 5 // 250 surveys — far above any realistic tenant
    }
}
