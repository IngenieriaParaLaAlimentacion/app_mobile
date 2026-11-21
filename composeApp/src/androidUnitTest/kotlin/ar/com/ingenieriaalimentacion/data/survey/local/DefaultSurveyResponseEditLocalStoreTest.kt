package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseEditUpload
import ar.com.ingenieriaalimentacion.fake.activeSessionLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSurveyResponseEditLocalStoreTest {
    private lateinit var store: DefaultSurveyResponseEditLocalStore

    @BeforeTest
    fun setup() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        store = DefaultSurveyResponseEditLocalStore(
            AppDatabase(driver).pendingSurveyResponseEditQueries,
            lifecycle = activeSessionLifecycle(),
            now = { 100L },
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `awaiting number policy selects only exact numberless legacy failures`() = runTest {
        store.enqueue(edit("legacy", null, "Falta el número de respuesta."))
        store.enqueue(edit("other", null, "Las respuestas no son válidas."))
        store.enqueue(edit("numbered", 8, "Falta el número de respuesta."))

        assertEquals(listOf("legacy"), store.awaitingNumberPolicy().map { it.editId })
    }

    @Test
    fun `recovered edit keeps the original request and new edits use a different id`() = runTest {
        val original = edit("original", 1, null).copy(
            syncState = SyncState.Pending,
            attemptCount = 0,
            answersJson = """{"q1":"first"}""",
        )
        store.enqueue(original)
        assertTrue(store.claimForSync(store.syncable().single()))
        store.resetStuckSyncing()

        val next = original.copy(editId = "next", createdAt = 101L, answersJson = """{"q1":"second"}""")
        store.enqueue(next)
        val pending = store.syncable()
        assertEquals(listOf("original", "next"), pending.map { it.editId })
        assertEquals(listOf(original.answersJson, next.answersJson), pending.map { it.answersJson })
        assertEquals(0, pending.first().attemptCount)

        // A later local edit can still coalesce into the new, unsent row.
        val replacement = next.copy(editId = "unused", answersJson = """{"q1":"replacement"}""")
        store.enqueue(replacement)
        assertFalse(store.claimForSync(pending.last()))
        assertEquals(listOf("original", "next"), store.syncable().map { it.editId })
        assertEquals(replacement.answersJson, store.syncable().last().answersJson)
    }

    private fun edit(editId: String, number: Int?, error: String?) = SurveyResponseEditUpload(
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
