package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.activity.local.ActivityItemType
import ar.com.ingenieriaalimentacion.data.activity.local.ActivityLocalStore
import ar.com.ingenieriaalimentacion.data.activity.local.CachedActivityRow
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toAnswerValues
import ar.com.ingenieriaalimentacion.data.survey.mapper.toSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Reactive floor (mirrors [ar.com.ingenieriaalimentacion.data.subject.SubjectHistoryRepositoryImpl]):
 * unions the local survey-response queue with a persistent [ActivityItemType.SURVEY_RESPONSE] cache
 * of `/me/activity?type=survey_response`, so [getPage] degrades gracefully offline on every page,
 * not just page 1.
 * [getDetail] always prefers a live fetch (last-write-wins by `event_at` means a cached copy can go
 * silently stale); falls back to this device's own local row only when offline.
 */
class SurveyResponseHistoryRepositoryImpl(
    private val api: SurveyResponseApi,
    private val activityApi: SurveyResponseActivityApi,
    private val local: SurveyResponseLocalStore,
    private val cache: ActivityLocalStore,
    private val surveys: SurveyRepository,
    private val json: Json,
) : SurveyResponseHistoryRepository {

    override fun observeLocalMerged(): Flow<List<SurveyResponseSummary>> =
        combine(local.observeAll(), cache.observe(ActivityItemType.SURVEY_RESPONSE)) { queued, cached ->
            merge(queued.map { it.toSummary() }, cached.map { it.toSummary() })
        }

    override suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> {
        val remote = runCatching { activityApi.list(page, pageSize) }
        return remote.fold(
            onSuccess = { response ->
                val remoteSummaries = response.items.mapNotNull { it.surveyResponse?.toSummary() }
                cache.upsertPage(ActivityItemType.SURVEY_RESPONSE, remoteSummaries.map { it.toCachedRow() })
                if (page == 1) {
                    Result.success(merge(local.all().map { it.toSummary() }, remoteSummaries))
                } else {
                    Result.success(remoteSummaries)
                }
            },
            onFailure = { error ->
                // Offline: serve queue ∪ cache as a single self-contained page (page 1) or the
                // requested cached page (page > 1) instead of failing.
                val fallback = if (page == 1) {
                    merge(local.all().map { it.toSummary() }, cachedPage(1, pageSize))
                } else {
                    cachedPage(page, pageSize)
                }
                if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(error)
            },
        )
    }

    /** Local wins on id collision (ADR-009), sorted by event time — same shape as
     *  [ar.com.ingenieriaalimentacion.data.subject.SubjectHistoryRepositoryImpl.merge], just keyed
     *  on eventAt instead of updatedAt since [SurveyResponseSummary] has no updatedAt field. */
    private fun merge(local: List<SurveyResponseSummary>, remote: List<SurveyResponseSummary>): List<SurveyResponseSummary> {
        val localIds = local.mapTo(mutableSetOf()) { it.id }
        return (local + remote.filterNot { it.id in localIds }).sortedByDescending { it.eventAtMillis() }
    }

    private suspend fun cachedPage(page: Int, pageSize: Int): List<SurveyResponseSummary> =
        cache.getPage(ActivityItemType.SURVEY_RESPONSE, limit = pageSize, offset = (page - 1) * pageSize)
            .map { it.toSummary() }

    private fun SurveyResponseSummary.toCachedRow(): CachedActivityRow =
        CachedActivityRow(id = id, occurredAtMillis = eventAtMillis(), payloadJson = json.encodeToString(this))

    private fun CachedActivityRow.toSummary(): SurveyResponseSummary = json.decodeFromString(payloadJson)

    private fun SurveyResponseSummary.eventAtMillis(): Long =
        runCatching { Instant.parse(eventAt).toEpochMilliseconds() }.getOrDefault(0L)

    override suspend fun getDetail(responseId: String): Result<SurveyResponseDetail> =
        when (val outcome = api.detail(responseId)) {
            is SurveyResponseDetailOutcome.Found -> {
                val response = outcome.response
                decodeDetail(
                    surveyId = response.surveyId,
                    id = response.id,
                    establishmentId = response.establishmentId,
                    submittedById = response.submittedById,
                    answers = response.answers,
                    eventAt = response.eventAt,
                )
            }
            SurveyResponseDetailOutcome.Forbidden ->
                Result.failure(SurveyFeatureFailure(SurveyFeatureError.Forbidden, null))
            SurveyResponseDetailOutcome.NotFound ->
                Result.failure(SurveyFeatureFailure(SurveyFeatureError.NotFound, null))
            is SurveyResponseDetailOutcome.Retryable ->
                fallbackToLocal(responseId)
                    ?: Result.failure(SurveyFeatureFailure(SurveyFeatureError.Network, outcome.cause))
        }

    /** This device's own not-yet-pruned submission — the only local copy that can exist, since a
     *  response fetched from another device is never persisted here. Null if no such row exists. */
    private suspend fun fallbackToLocal(responseId: String): Result<SurveyResponseDetail>? {
        val upload = local.find(responseId) ?: return null
        val answers = json.decodeFromString<Map<String, JsonElement>>(upload.answersJson)
        return decodeDetail(
            surveyId = upload.surveyId,
            id = upload.canonicalId ?: upload.localId,
            establishmentId = upload.establishmentId,
            // null → "this device's own submission" per SurveyResponseDetail's contract.
            submittedById = null,
            answers = answers,
            eventAt = upload.eventAt,
        ).getOrNull()?.let { Result.success(it) }
    }

    private suspend fun decodeDetail(
        surveyId: String,
        id: String,
        establishmentId: String,
        submittedById: String?,
        answers: Map<String, JsonElement>,
        eventAt: String,
    ): Result<SurveyResponseDetail> {
        val questions = surveys.getDetail(surveyId).getOrElse { return Result.failure(it) }.questions
        // answers.toAnswerValues/Instant.parse can throw on malformed external data (e.g. a
        // question's response_type changed server-side since this device last cached it) —
        // never trust it blindly; surface as a typed failure like every other read here.
        return runCatching {
            SurveyResponseDetail(
                id = id,
                surveyId = surveyId,
                establishmentId = establishmentId,
                submittedById = submittedById,
                answers = answers.toAnswerValues(questions),
                eventAtMs = Instant.parse(eventAt).toEpochMilliseconds(),
            )
        }
    }
}
