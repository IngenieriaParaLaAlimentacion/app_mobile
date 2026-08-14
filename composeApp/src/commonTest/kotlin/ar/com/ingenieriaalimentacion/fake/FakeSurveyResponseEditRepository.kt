package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyAnswerValue
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditStatus
import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseEditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

data class EnqueuedEdit(
    val responseId: String,
    val surveyId: String,
    val surveyName: String,
    val establishmentName: String,
    val answers: Map<String, SurveyAnswerValue>,
    val eventAtMs: Long,
)

class FakeSurveyResponseEditRepository : SurveyResponseEditRepository {
    val enqueued = mutableListOf<EnqueuedEdit>()
    var enqueueResult: Result<Unit> = Result.success(Unit)
    var syncSummary: SyncSummary = SyncSummary()
    val editStatus = MutableStateFlow<SurveyResponseEditStatus?>(null)

    override suspend fun enqueueEdit(
        responseId: String,
        surveyId: String,
        surveyName: String,
        establishmentName: String,
        answers: Map<String, SurveyAnswerValue>,
        eventAtMs: Long,
    ): Result<Unit> {
        enqueued += EnqueuedEdit(responseId, surveyId, surveyName, establishmentName, answers, eventAtMs)
        return enqueueResult
    }

    override suspend fun syncPending(): SyncSummary = syncSummary

    override suspend fun retry(editId: String): Result<Unit> = Result.success(Unit)

    override fun observeEditStatus(responseId: String): Flow<SurveyResponseEditStatus?> = editStatus

    override suspend fun resetStuckSyncing() = Unit

    override suspend fun pruneTerminal(olderThan: Duration) = Unit
}
