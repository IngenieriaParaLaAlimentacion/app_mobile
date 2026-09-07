package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.NewSurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class FakeSurveyResponseRepository : SurveyResponseRepository {
    val enqueued = mutableListOf<NewSurveyResponse>()
    val retried = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val updatedNumbers = mutableListOf<Pair<String, Int>>()
    var enqueueResult: Result<Unit> = Result.success(Unit)
    var syncSummary: SyncSummary = SyncSummary()
    val uploads = MutableStateFlow<List<SurveyResponseUpload>>(emptyList())

    override suspend fun enqueue(newResponse: NewSurveyResponse): Result<Unit> {
        enqueued += newResponse
        return enqueueResult
    }

    override fun observeUploads(): Flow<List<SurveyResponseUpload>> = uploads

    override suspend fun syncPending(): SyncSummary = syncSummary

    override suspend fun retry(localId: String): Result<Unit> {
        retried += localId
        return Result.success(Unit)
    }

    override suspend fun updateResponseNumber(localId: String, responseNumber: Int): Result<Unit> {
        updatedNumbers += localId to responseNumber
        uploads.value = uploads.value.map {
            if (it.localId == localId) it.copy(responseNumber = responseNumber) else it
        }
        return Result.success(Unit)
    }

    override suspend fun findQueued(localId: String): SurveyResponseUpload? =
        uploads.value.firstOrNull { it.localId == localId }

    override suspend fun delete(localId: String): Result<Unit> {
        deleted += localId
        uploads.value = uploads.value.filterNot { it.localId == localId }
        return Result.success(Unit)
    }

    override suspend fun resetStuckSyncing() = Unit

    override suspend fun pruneTerminal(olderThan: Duration) = Unit
}
