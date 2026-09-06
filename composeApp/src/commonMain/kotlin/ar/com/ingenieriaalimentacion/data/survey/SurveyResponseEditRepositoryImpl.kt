package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseEditLocalStore
import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseLocalStore
import ar.com.ingenieriaalimentacion.data.survey.mapper.toAnswersJson
import ar.com.ingenieriaalimentacion.data.survey.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.MAX_SYNC_ATTEMPTS
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditStatus
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.isEligibleAt
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The FK-gated edit engine: a second, independent queue from [SurveyResponseRepositoryImpl],
 * structurally ported from [ar.com.ingenieriaalimentacion.data.measurement.MeasurementRepositoryImpl]'s
 * subject-gate, gating on the parent response's [SyncState] instead of a subject's.
 */
class SurveyResponseEditRepositoryImpl(
    private val api: SurveyResponseApi,
    private val local: SurveyResponseEditLocalStore,
    private val responses: SurveyResponseLocalStore,
    private val idGenerator: IdGenerator,
    private val json: Json,
    private val now: () -> Long,
) : SurveyResponseEditRepository {

    override suspend fun enqueueEdit(
        responseId: String,
        surveyId: String,
        surveyName: String,
        establishmentName: String,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit> = runCatching {
        val nowMs = now()
        val eventAt = Instant.fromEpochMilliseconds(eventAtMs).toString()
        val answersJson = json.encodeToString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            answers.toAnswersJson(),
        )
        val existing = local.findPendingForResponse(responseId)
        if (existing != null) {
            // Not-yet-transmitted edit for this response: overwrite in place. Safe — this id was
            // never sent, so there's no already-applied server-side attempt to collide with.
            local.updatePending(existing.editId, surveyName, establishmentName, answersJson, eventAt, nowMs)
        } else {
            local.insert(
                SurveyResponseEditUpload(
                    editId = idGenerator.newId(),
                    responseId = responseId,
                    surveyId = surveyId,
                    surveyName = surveyName,
                    establishmentName = establishmentName,
                    answersJson = answersJson,
                    eventAt = eventAt,
                    syncState = SyncState.Pending,
                    errorMessage = null,
                    attemptCount = 0,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                ),
            )
        }
    }

    override suspend fun syncPending(): SyncSummary {
        var synced = 0
        var failed = 0
        var pending = 0
        local.syncable().filter { it.isEligibleAt(now()) }.forEach { edit ->
            // Gate on the parent response's own queue state (no schema JOIN, same pattern as
            // MeasurementRepositoryImpl's subject gate). No local row → this device never created
            // it (fetched from the server, e.g. cross-device edit) → assume synced, attempt directly.
            val parent = responses.find(edit.responseId)
            when {
                parent == null || parent.syncState == SyncState.Synced -> Unit
                parent.syncState == SyncState.Failed -> {
                    local.markFailed(edit.editId, "La respuesta original no se pudo sincronizar.")
                    failed++
                    return@forEach
                }
                else -> {
                    pending++
                    return@forEach
                }
            }
            local.markSyncing(edit.editId)
            when (val outcome = api.edit(edit.surveyId, edit.responseId, edit.toRequest(json))) {
                is SurveyResponseEditOutcome.Applied -> {
                    local.markSynced(edit.editId)
                    synced++
                }
                SurveyResponseEditOutcome.InvalidData -> {
                    local.markFailed(edit.editId, "Las respuestas no son válidas.")
                    failed++
                }
                SurveyResponseEditOutcome.Forbidden -> {
                    local.markFailed(edit.editId, "No tenés permisos para editar esta respuesta.")
                    failed++
                }
                SurveyResponseEditOutcome.NotFound -> {
                    local.markFailed(edit.editId, "La respuesta original no existe en el servidor.")
                    failed++
                }
                SurveyResponseEditOutcome.Unauthorized ->
                    if (retryOrDeadLetter(edit, "Sesión expirada. Reintentando…")) pending++ else failed++
                is SurveyResponseEditOutcome.Retryable ->
                    if (retryOrDeadLetter(edit, "Sin conexión. Reintentando…")) pending++ else failed++
            }
        }
        return SyncSummary(synced = synced, merged = 0, failed = failed, stillPending = pending)
    }

    private suspend fun retryOrDeadLetter(edit: SurveyResponseEditUpload, msg: String): Boolean =
        if (edit.attemptCount + 1 >= MAX_SYNC_ATTEMPTS) {
            local.markFailed(edit.editId, "No se pudo sincronizar tras varios intentos.")
            false
        } else {
            local.bumpRetry(edit.editId, msg)
            true
        }

    override suspend fun retry(editId: String): Result<Unit> = runCatching { local.resetForRetry(editId) }

    override fun observeEditStatus(responseId: String): Flow<SurveyResponseEditStatus?> =
        local.observeLatestForResponse(responseId).map { edit ->
            edit?.let { SurveyResponseEditStatus(it.editId, it.syncState, it.errorMessage) }
        }

    override suspend fun resetStuckSyncing() {
        local.resetStuckSyncing()
    }

    override suspend fun pruneTerminal(olderThan: Duration) {
        local.pruneTerminalOlderThan(now() - olderThan.inWholeMilliseconds)
    }
}
