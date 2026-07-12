package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toAnswerValues
import ar.com.ingenieriaalimentacion.data.survey.mapper.toSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.repository.SurveyRepository
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseHistoryRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Unions the local survey-response queue with `/me/activity?type=survey_response` for [getPage]
 * (mirrors [ar.com.ingenieriaalimentacion.data.subject.SubjectQueryRepositoryImpl]'s page-1-only
 * union rule, with no persistent activity cache — the local queue is the only offline fallback).
 * [getDetail] always prefers a live fetch (last-write-wins by `event_at` means a cached copy can go
 * silently stale); falls back to this device's own local row only when offline.
 */
class SurveyResponseHistoryRepositoryImpl(
    private val api: SurveyResponseApi,
    private val activityApi: SurveyResponseActivityApi,
    private val local: SurveyResponseLocalStore,
    private val surveys: SurveyRepository,
    private val json: Json,
) : SurveyResponseHistoryRepository {

    override suspend fun getPage(page: Int, pageSize: Int): Result<List<SurveyResponseSummary>> {
        val remote = runCatching { activityApi.list(page, pageSize) }

        if (page != 1) {
            return remote.map { response -> response.items.mapNotNull { it.surveyResponse?.toSummary() } }
        }

        val localSummaries = local.all().map { it.toSummary() }

        return remote.fold(
            onSuccess = { response ->
                val remoteSummaries = response.items.mapNotNull { it.surveyResponse?.toSummary() }
                val remoteIds = remoteSummaries.mapTo(mutableSetOf()) { it.id }
                Result.success(localSummaries.filterNot { it.id in remoteIds } + remoteSummaries)
            },
            onFailure = { error ->
                if (localSummaries.isNotEmpty()) Result.success(localSummaries) else Result.failure(error)
            },
        )
    }

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
