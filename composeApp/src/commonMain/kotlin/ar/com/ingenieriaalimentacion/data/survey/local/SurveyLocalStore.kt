package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail

/**
 * Read-through cache for survey definitions. The concrete [DefaultSurveyLocalStore] wraps
 * SQLDelight; tests use a fake.
 */
interface SurveyLocalStore {
    /** Cached full question/option set for [surveyId], or null if never fetched. */
    suspend fun findDetail(surveyId: String): SurveyDetail?

    /** From the `/available` list. Preserves any existing `detailJson` for a survey already
     *  fetched via [upsertDetail] — this store reads the current row before writing so a
     *  list refresh never blows away a cached detail (see `insertOrReplace` in `CachedSurvey.sq`). */
    suspend fun upsertSummaries(surveys: List<Survey>)

    /** From `GET /surveys/{id}`. */
    suspend fun upsertDetail(detail: SurveyDetail)
}
