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
    var enqueueResult: Result<Unit> = Result.success(Unit)
    var syncSummary: SyncSummary = SyncSummary()
    val uploads = MutableStateFlow<List<SubjectUpload>>(emptyList())

    override suspend fun enqueue(newSubject: NewSubject): Result<Unit> {
        enqueued += newSubject
        return enqueueResult
    }

    override fun observeUploads(): Flow<List<SubjectUpload>> = uploads

    override suspend fun syncPending(): SyncSummary = syncSummary

    override suspend fun retry(localId: String): Result<Unit> = Result.success(Unit)

    override suspend fun delete(localId: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetStuckSyncing() = Unit

    override suspend fun pruneTerminal(olderThan: Duration) = Unit
}
