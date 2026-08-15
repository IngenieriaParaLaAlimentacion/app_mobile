package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.survey.Survey
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSurveyLocalStoreTest {

    private lateinit var store: DefaultSurveyLocalStore
    private var time = 100L

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).cachedSurveyQueries
        store = DefaultSurveyLocalStore(queries, json = Json, now = { time }, dispatcher = Dispatchers.Unconfined)
    }

    private fun survey(id: String, name: String, isActive: Boolean = true, questionCount: Int = 3) = Survey(
        id = id,
        name = name,
        responseTarget = SurveyResponseTarget.Establishment,
        isActive = isActive,
        questionCount = questionCount,
    )

    private fun detail(id: String) = SurveyDetail(
        id = id,
        name = "Detail $id",
        responseTarget = SurveyResponseTarget.Establishment,
        isActive = true,
        questions = emptyList(),
    )

    @Test
    fun `getSummaries returns rows name-ordered mapped correctly`() = runTest {
        store.upsertSummaries(listOf(survey("b", "Beta", isActive = false, questionCount = 5), survey("a", "Alpha")))

        val summaries = store.getSummaries()

        assertEquals(listOf("Alpha", "Beta"), summaries.map { it.name })
        val beta = summaries.first { it.id == "b" }
        assertEquals(SurveyResponseTarget.Establishment, beta.responseTarget)
        assertEquals(false, beta.isActive)
        assertEquals(5, beta.questionCount)
    }

    @Test
    fun `replaceAllSummaries deletes rows absent from the new set`() = runTest {
        store.upsertSummaries(listOf(survey("a", "Alpha"), survey("b", "Beta")))

        store.replaceAllSummaries(listOf(survey("a", "Alpha")))

        assertEquals(listOf("a"), store.getSummaries().map { it.id })
    }

    @Test
    fun `replaceAllSummaries preserves detailJson for surviving rows`() = runTest {
        store.upsertSummaries(listOf(survey("a", "Alpha")))
        store.upsertDetail(detail("a"))

        store.replaceAllSummaries(listOf(survey("a", "Alpha")))

        assertEquals(detail("a"), store.findDetail("a"))
    }

    @Test
    fun `idsMissingDetail returns only ids with detailJson null`() = runTest {
        store.upsertSummaries(listOf(survey("a", "Alpha"), survey("b", "Beta")))
        store.upsertDetail(detail("a"))

        val missing = store.idsMissingDetail()

        assertEquals(listOf("b"), missing)
    }

    @Test
    fun `findDetail is null when never fetched`() = runTest {
        store.upsertSummaries(listOf(survey("a", "Alpha")))

        assertNull(store.findDetail("a"))
    }

    @Test
    fun `replaceAllSummaries with empty list wipes every row`() = runTest {
        store.upsertSummaries(listOf(survey("a", "Alpha"), survey("b", "Beta")))

        store.replaceAllSummaries(emptyList())

        assertTrue(store.getSummaries().isEmpty())
    }
}
