package ar.com.ingenieriaalimentacion.data.survey.local

import ar.com.ingenieriaalimentacion.data.db.CachedSurveyQueries
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDbString
import ar.com.ingenieriaalimentacion.data.survey.mapper.toDomainSurvey
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed [SurveyLocalStore]. No `ON CONFLICT DO UPDATE` support in this project's
 * SQLDelight dialect (see `CachedSurvey.sq`), so [upsertSummaries] reads any existing `detailJson`
 * before a full-row `insertOrReplace` — otherwise a list refresh would null out a previously cached
 * detail.
 */
class DefaultSurveyLocalStore(
    private val queries: CachedSurveyQueries,
    private val json: Json,
    private val now: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SurveyLocalStore {

    override suspend fun findDetail(surveyId: String): SurveyDetail? = withContext(dispatcher) {
        queries.selectById(surveyId).executeAsOneOrNull()?.detailJson?.let { raw ->
            runCatching { json.decodeFromString<SurveyDetail>(raw) }.getOrNull()
        }
    }

    override suspend fun upsertSummaries(surveys: List<Survey>): Unit = withContext(dispatcher) {
        val nowMs = now()
        // One transaction, not one per survey: batches the reads+writes and — since SQLDelight
        // serializes transactions against other statements on the same connection — closes the
        // TOCTOU window against a concurrent upsertDetail() reverting a just-cached detailJson.
        queries.transaction {
            surveys.forEach { survey ->
                val existingDetailJson = queries.selectById(survey.id).executeAsOneOrNull()?.detailJson
                queries.insertOrReplace(
                    id = survey.id,
                    name = survey.name,
                    responseTarget = survey.responseTarget.toDbString(),
                    isActive = if (survey.isActive) 1L else 0L,
                    questionCount = survey.questionCount.toLong(),
                    detailJson = existingDetailJson,
                    updatedAt = nowMs,
                )
            }
        }
    }

    override suspend fun upsertDetail(detail: SurveyDetail): Unit = withContext(dispatcher) {
        queries.insertOrReplace(
            id = detail.id,
            name = detail.name,
            responseTarget = detail.responseTarget.toDbString(),
            isActive = if (detail.isActive) 1L else 0L,
            questionCount = detail.questions.size.toLong(),
            detailJson = json.encodeToString(detail),
            updatedAt = now(),
        )
    }

    override suspend fun getSummaries(): List<Survey> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomainSurvey() }
    }

    override suspend fun replaceAllSummaries(surveys: List<Survey>): Unit = withContext(dispatcher) {
        val nowMs = now()
        queries.transaction {
            // Snapshot existing detailJson before wiping so the replace never loses a cached detail.
            val existingDetailJson = queries.selectAll().executeAsList()
                .filter { it.detailJson != null }
                .associate { it.id to it.detailJson }
            queries.deleteAll()
            surveys.forEach { survey ->
                queries.insertOrReplace(
                    id = survey.id,
                    name = survey.name,
                    responseTarget = survey.responseTarget.toDbString(),
                    isActive = if (survey.isActive) 1L else 0L,
                    questionCount = survey.questionCount.toLong(),
                    detailJson = existingDetailJson[survey.id],
                    updatedAt = nowMs,
                )
            }
        }
    }

    override suspend fun idsMissingDetail(): List<String> = withContext(dispatcher) {
        queries.selectIdsMissingDetail().executeAsList()
    }
}
