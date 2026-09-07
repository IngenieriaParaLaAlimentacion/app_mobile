package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSurveyResponseEditLocalStoreTest {
    private lateinit var store: DefaultSurveyResponseEditLocalStore

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        store = DefaultSurveyResponseEditLocalStore(
            AppDatabase(driver).pendingSurveyResponseEditQueries,
            now = { 100L },
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `awaiting number policy selects only exact numberless legacy failures`() = runTest {
        store.insert(edit("legacy", null, "Falta el número de respuesta."))
        store.insert(edit("other", null, "Las respuestas no son válidas."))
        store.insert(edit("numbered", 8, "Falta el número de respuesta."))

        assertEquals(listOf("legacy"), store.awaitingNumberPolicy().map { it.editId })
    }

    private fun edit(editId: String, number: Int?, error: String) = SurveyResponseEditUpload(
        editId = editId,
        responseId = "r1",
        surveyId = "s1",
        surveyName = "Encuesta",
        establishmentName = "Norte",
        answersJson = "{}",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = SyncState.Failed,
        errorMessage = error,
        attemptCount = 1,
        createdAt = 1L,
        updatedAt = 2L,
        responseNumber = number,
    )
}
