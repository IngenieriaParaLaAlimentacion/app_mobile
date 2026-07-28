package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class FakeSubjectRepository : SubjectRepository {
    val enqueued = mutableListOf<NewSubject>()
    val retried = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var enqueueResult: Result<String> = Result.success("local-id")
    var syncSummary: SyncSummary = SyncSummary()
    val uploads = MutableStateFlow<List<SubjectUpload>>(emptyList())

    override suspend fun enqueue(newSubject: NewSubject): Result<String> {
        enqueued += newSubject
        return enqueueResult
    }

    override fun observeUploads(): Flow<List<SubjectUpload>> = uploads

    override suspend fun syncPending(): SyncSummary = syncSummary

    override suspend fun retry(localId: String): Result<Unit> {
        retried += localId
        return Result.success(Unit)
    }

    override suspend fun delete(localId: String): Result<Unit> {
        deleted += localId
        return Result.success(Unit)
    }

    override suspend fun resetStuckSyncing() = Unit

    override suspend fun pruneTerminal(olderThan: Duration) = Unit
}
