package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toAnswersJson
import ar.com.ingenieriaalimentacion.data.survey.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.isEligibleAt
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Offline-first survey-response repository: the local DB is the source of truth. [enqueue] writes a
 * PENDING row instantly; [syncPending] pushes eligible rows and reconciles each outcome into a local
 * state transition. A direct structural port of
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl], with no MERGED branch — no
 * per-survey uniqueness exists server-side — and a NotFound branch (stale survey/establishment id).
 */
class SurveyResponseRepositoryImpl(
    private val api: SurveyResponseApi,
    private val local: SurveyResponseLocalStore,
    private val idGenerator: IdGenerator,
    private val json: Json,
    private val now: () -> Long,
) : SurveyResponseRepository {

    override suspend fun enqueue(newResponse: NewSurveyResponse): Result<Unit> = runCatching {
        val nowMs = now()
        val eventAt = Instant.fromEpochMilliseconds(newResponse.eventAtMs).toString()
        val answersJson = json.encodeToString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            newResponse.answers.toAnswersJson(),
        )
        local.insert(
            SurveyResponseUpload(
                localId = idGenerator.newId(),
                surveyId = newResponse.surveyId,
                surveyName = newResponse.surveyName,
                establishmentId = newResponse.establishmentId,
                establishmentName = newResponse.establishmentName,
                answersJson = answersJson,
                eventAt = eventAt,
                syncState = SyncState.Pending,
                canonicalId = null,
                errorMessage = null,
                attemptCount = 0,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }

    override fun observeUploads(): Flow<List<SurveyResponseUpload>> = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0
        var failed = 0
        var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.toRequest(json))) {
                is SurveyResponseUploadOutcome.Created -> {
                    local.markSynced(upload.localId, outcome.response.id)
                    synced++
                }
                SurveyResponseUploadOutcome.InvalidData -> {
                    local.markFailed(upload.localId, "Las respuestas no son válidas.")
                    failed++
                }
                SurveyResponseUploadOutcome.Forbidden -> {
                    local.markFailed(upload.localId, "No tenés permisos para responder esta encuesta.")
                    failed++
                }
                SurveyResponseUploadOutcome.NotFound -> {
                    local.markFailed(upload.localId, "La encuesta o el establecimiento no existen.")
                    failed++
                }
                SurveyResponseUploadOutcome.Unauthorized ->
                    if (retryOrDeadLetter(upload, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SurveyResponseUploadOutcome.Retryable ->
                    if (retryOrDeadLetter(upload, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    private suspend fun retryOrDeadLetter(upload: SurveyResponseUpload, msg: String): Boolean =
        if (upload.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(upload.localId, "No se pudo sincronizar tras varios intentos.")
            false
        } else {
            local.bumpRetry(upload.localId, msg)
            true
        }

    override suspend fun retry(localId: String): Result<Unit> = runCatching { local.resetForRetry(localId) }

    override suspend fun resetStuckSyncing() {
        local.resetStuckSyncing()
    }

    override suspend fun pruneTerminal(olderThan: Duration) {
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
    }
}
