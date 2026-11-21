package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyResponseEditLocalStore
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SurveyResponseEditLocalStore] that mirrors the SQL semantics of
 * [ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyResponseEditLocalStore].
 */
class FakeSurveyResponseEditLocalStore(private val now: () -> Long = { 0L }) : SurveyResponseEditLocalStore {

    private val sentIds = mutableSetOf<String>()
    private val rows = MutableStateFlow<List<SurveyResponseEditUpload>>(emptyList())

    fun current(editId: String): SurveyResponseEditUpload? = rows.value.firstOrNull { it.editId == editId }

    fun snapshot(): List<SurveyResponseEditUpload> = rows.value

    private fun mutate(editId: String, transform: (SurveyResponseEditUpload) -> SurveyResponseEditUpload) {
        rows.value = rows.value.map { if (it.editId == editId) transform(it) else it }
    }

    override fun observeLatestForResponse(responseId: String): Flow<SurveyResponseEditUpload?> =
        rows.map { list ->
            list.filter { it.responseId == responseId }.maxByOrNull { it.createdAt }
        }

    override suspend fun enqueue(edit: SurveyResponseEditUpload) {
        val unsent = rows.value.firstOrNull {
            it.responseId == edit.responseId && it.syncState == SyncState.Pending && it.editId !in sentIds
        }
        if (unsent == null) {
            insert(edit)
        } else {
            mutate(unsent.editId) {
                it.copy(
                    surveyName = edit.surveyName,
                    establishmentName = edit.establishmentName,
                    answersJson = edit.answersJson,
                    eventAt = edit.eventAt,
                    responseNumber = edit.responseNumber,
                    updatedAt = edit.updatedAt,
                )
            }
        }
    }

    suspend fun insert(edit: SurveyResponseEditUpload) {
        rows.value = rows.value + edit
    }

    override suspend fun syncable(): List<SurveyResponseEditUpload> =
        rows.value.filter { it.syncState == SyncState.Pending }.sortedBy { it.createdAt }

    override suspend fun awaitingNumberPolicy(): List<SurveyResponseEditUpload> =
        rows.value.filter {
            it.syncState == SyncState.Failed && it.responseNumber == null &&
                it.errorMessage == MISSING_REQUIRED_RESPONSE_NUMBER_MESSAGE
        }

    override suspend fun claimForSync(edit: SurveyResponseEditUpload): Boolean {
        if (current(edit.editId) != edit) return false
        sentIds.add(edit.editId)
        mutate(edit.editId) { it.copy(syncState = SyncState.Syncing, updatedAt = now()) }
        return true
    }

    override suspend fun markSynced(editId: String) =
        mutate(editId) { it.copy(syncState = SyncState.Synced, errorMessage = null, updatedAt = now()) }

    override suspend fun markFailed(editId: String, reason: String) =
        mutate(editId) {
            it.copy(
                syncState = SyncState.Failed,
                errorMessage = reason,
                attemptCount = it.attemptCount + 1,
                updatedAt = now(),
            )
        }

    override suspend fun bumpRetry(editId: String, reason: String) =
        mutate(editId) {
            it.copy(
                syncState = SyncState.Pending,
                errorMessage = reason,
                attemptCount = it.attemptCount + 1,
                updatedAt = now(),
            )
        }

    override suspend fun resetForRetry(editId: String) =
        mutate(editId) {
            it.copy(syncState = SyncState.Pending, errorMessage = null, attemptCount = 0, updatedAt = now())
        }

    override suspend fun resetStuckSyncing() {
        rows.value = rows.value.map {
            if (it.syncState == SyncState.Syncing) it.copy(syncState = SyncState.Pending, updatedAt = now()) else it
        }
    }

    override suspend fun pruneTerminalOlderThan(cutoff: Long) {
        rows.value = rows.value.filterNot {
            (it.syncState == SyncState.Synced || it.syncState == SyncState.Failed) && it.updatedAt < cutoff
        }
    }
}
