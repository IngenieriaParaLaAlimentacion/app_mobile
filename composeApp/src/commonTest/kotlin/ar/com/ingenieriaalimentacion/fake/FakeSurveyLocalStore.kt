package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.data.survey.local.SurveyLocalStore
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail

/**
 * In-memory [SurveyLocalStore] mirroring [ar.com.ingenieriaalimentacion.data.survey.local.DefaultSurveyLocalStore]'s
 * detailJson-preservation semantics, so repository/warm tests don't need a real database.
 */
class FakeSurveyLocalStore : SurveyLocalStore {

    private val summaries = mutableMapOf<String, Survey>()
    private val details = mutableMapOf<String, SurveyDetail>()

    /** Number of times [replaceAllSummaries] was invoked — asserts a partial warm skipped the replace. */
    var replaceAllSummariesCallCount: Int = 0
        private set

    override suspend fun findDetail(surveyId: String): SurveyDetail? = details[surveyId]

    override suspend fun upsertSummaries(surveys: List<Survey>) {
        surveys.forEach { summaries[it.id] = it }
    }

    override suspend fun upsertDetail(detail: SurveyDetail) {
        details[detail.id] = detail
        summaries[detail.id]?.let { summaries[detail.id] = it.copy(questionCount = detail.questions.size) }
    }

    override suspend fun getSummaries(): List<Survey> = summaries.values.sortedBy { it.name }

    override suspend fun replaceAllSummaries(surveys: List<Survey>) {
        replaceAllSummariesCallCount++
        val keepDetails = details.filterKeys { id -> surveys.any { it.id == id } }
        summaries.clear()
        details.clear()
        surveys.forEach { summaries[it.id] = it }
        details.putAll(keepDetails)
    }

    override suspend fun idsMissingDetail(): List<String> =
        summaries.keys.filterNot { details.containsKey(it) }
}
