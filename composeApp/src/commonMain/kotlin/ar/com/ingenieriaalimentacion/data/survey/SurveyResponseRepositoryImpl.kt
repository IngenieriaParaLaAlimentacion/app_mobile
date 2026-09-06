package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.subject.local.SubjectLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toAnswersJson
import ar.com.ingenieriaalimentacion.data.survey.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflictKind
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureError
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyFeatureFailure
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
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
 * [ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl]. There is no MERGED state:
 * a per-survey+target uniqueness conflict is a terminal `AlreadyAnswered` row carrying who already
 * answered, not a merge.
 */
class SurveyResponseRepositoryImpl(
    private val api: SurveyResponseApi,
    private val local: SurveyResponseLocalStore,
    private val subjects: SubjectLocalStore,
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
                target = newResponse.target,
                answersJson = answersJson,
                eventAt = eventAt,
                syncState = SyncState.Pending,
                canonicalId = null,
                errorMessage = null,
                conflict = null,
                attemptCount = 0,
                createdAt = nowMs,
                updatedAt = nowMs,
                responseNumber = newResponse.responseNumber,
            ),
        )
    }

    override fun observeUploads(): Flow<List<SurveyResponseUpload>> = local.observeAll()

    override suspend fun syncPending(): SyncSummary {
        var synced = 0
        var failed = 0
        var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { upload ->
            // A subject target may point at a subject this device queued offline. Resolve it to
            // the server's canonical id (set when that subject 409'd on DNI) and hold the response
            // back while the parent is still in flight — same gate as MeasurementRepositoryImpl.
            // Ordering is already guaranteed: SubjectRepository runs before this engine in the
            // SyncCoordinator's list, so within a single sweep the subject lands first.
            val target = upload.target
            val resolvedSubjectId: String? = if (target is ResponseTargetRef.Subject) {
                val subject = subjects.find(target.id)
                when {
                    // Pre-existing server subject (no local row) → POST as-is.
                    subject == null -> target.id
                    subject.syncState == SyncState.Synced || subject.syncState == SyncState.Merged ->
                        subject.canonicalId ?: target.id
                    // Subject dead-lettered → the FK can never resolve; dead-letter the response too.
                    subject.syncState == SyncState.Failed -> {
                        local.markFailed(upload.localId, "El sujeto no se pudo sincronizar.")
                        failed++
                        return@forEach
                    }
                    // Subject still PENDING/SYNCING → hold; a later pass retries once it lands.
                    else -> {
                        pending++
                        return@forEach
                    }
                }
            } else {
                null
            }

            val responseNumber = upload.responseNumber
            if (responseNumber == null) {
                local.markFailed(upload.localId, MISSING_RESPONSE_NUMBER_MESSAGE)
                failed++
                return@forEach
            }
            local.markSyncing(upload.localId)
            when (val outcome = api.create(upload.surveyId, upload.toRequest(json, responseNumber, resolvedSubjectId))) {
                is SurveyResponseUploadOutcome.Created -> {
                    local.markSynced(upload.localId, outcome.response.id)
                    synced++
                }
                is SurveyResponseUploadOutcome.AlreadyAnswered -> {
                    local.markConflict(
                        upload.localId,
                        ResponseConflict(
                            submittedByName = outcome.submittedByName ?: "otro relevador",
                            eventAt = outcome.eventAt.orEmpty(),
                            kind = ResponseConflictKind.Target,
                        ),
                    )
                    failed++
                }
                is SurveyResponseUploadOutcome.NumberTaken -> {
                    local.markConflict(
                        upload.localId,
                        ResponseConflict(
                            outcome.submittedByName ?: "otro relevador",
                            outcome.eventAt.orEmpty(),
                            ResponseConflictKind.ResponseNumber,
                            outcome.existingResponseNumber,
                        ),
                    )
                    failed++
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
                    local.markFailed(upload.localId, "La encuesta o el destinatario no existen.")
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

    override suspend fun updateResponseNumber(localId: String, responseNumber: Int): Result<Unit> = runCatching {
        val row = local.find(localId) ?: error("No queued response $localId")
        if (local.findBySurveyAndNumber(row.surveyId, responseNumber, localId) != null) {
            throw SurveyFeatureFailure(SurveyFeatureError.Conflict(responseNumber, null), null)
        }
        local.updateResponseNumber(localId, responseNumber)
    }

    override suspend fun findQueued(localId: String): SurveyResponseUpload? = local.find(localId)

    override suspend fun delete(localId: String): Result<Unit> = runCatching { local.delete(localId) }

    override suspend fun resetStuckSyncing() {
        local.resetStuckSyncing()
    }

    override suspend fun pruneTerminal(olderThan: Duration) {
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
    }
}

internal const val MISSING_RESPONSE_NUMBER_MESSAGE = "Falta el número de respuesta."
